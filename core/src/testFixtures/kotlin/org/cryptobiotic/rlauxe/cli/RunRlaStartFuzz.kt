package org.cryptobiotic.rlauxe.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.audit.ClcaConfig

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.raire.RaireContestWithAssertions
import org.cryptobiotic.rlauxe.raire.simulateRaireTestContest
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsForPolling
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.OneAuditVunderFuzzer
import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditTest
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFileUnwrapped
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode
import org.cryptobiotic.rlauxe.workflow.readCardManifest
import org.cryptobiotic.rlauxe.workflow.readCardPools
import kotlin.io.path.Path
import kotlin.math.min

/** Create a multicontest audit, with fuzzed test data, TODO: stored in private record? */
object RunRlaStartFuzz {

    @JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgParser("RunRlaStartFuzz")
        val topdir by parser.option(
            ArgType.String,
            shortName = "in",
            description = "Directory containing test election record"
        ).required()
        val auditType by parser.option(
            ArgType.String,
            shortName = "type",
            description = "CLCA, ONEAUDIT, POLLING"
        ).default("CLCA")
        val minMargin by parser.option(
            ArgType.Double,
            shortName = "minMargin",
            description = "contest minimum margin"
        ).default(0.04)
        val fuzzMvrs by parser.option(
            ArgType.Double,
            shortName = "fuzzMvrs",
            description = "Fuzz Mvrs by this percent"
        ).default(0.0)
        val simFuzz by parser.option(
            ArgType.Double,
            shortName = "simFuzz",
            description = "simulation fuzzing"
        ).default(0.0)
        val quantile by parser.option(
            ArgType.Double,
            shortName = "quantile",
            description = "Estimation quantile (0.1-1.0)"
        ).default(0.8)
        val pctPhantoms by parser.option(
            ArgType.Double,
            shortName = "pctPhantoms",
            description = "Pct phantoms (0.0 to 1.0)"
        ).default(0.0)
        val ncards by parser.option(
            ArgType.Int,
            shortName = "ncards",
            description = "Total number of ballot/cards"
        ).default(10000)
        val ncontests by parser.option(
            ArgType.Int,
            shortName = "ncontests",
            description = "Number of contests"
        ).default(11)
        val addRaireContest by parser.option(
            ArgType.Boolean,
            shortName = "addRaire",
            description = "Add a Raire Contest"
        ).default(false)
        val addRaireCandidates by parser.option(
            ArgType.Int,
            shortName = "rcands",
            description = "Number of candidates for raire contest"
        ).default(5)
        val oaStrategy by parser.option(
            ArgType.String,
            shortName = "oaStrategy",
            description = "OneAudit Strategy: simulate or calc"
        ).default("simulate")
        val cvrFraction by parser.option(
            ArgType.Double,
            shortName = "cvrFraction",
            description = "CVR fraction (0.0 to 1.0)"
        ).default(0.95)
        val extra by parser.option(
            type = ArgType.Double,
            shortName = "extraPct",
            description = "add extra percent to simulate diluted margin"
        ).default(.01)

        parser.parse(args)
        println("RunRlaStartFuzz on $topdir auditType=$auditType minMargin=$minMargin fuzzMvrs=$fuzzMvrs, simFuzz=$simFuzz, pctPhantoms=$pctPhantoms, ncards=$ncards ncontests=$ncontests" +
                " addRaire=$addRaireContest addRaireCandidates=$addRaireCandidates quantile=$quantile")
        when (auditType) {
            "POLLING" -> startTestElectionPolling(topdir, minMargin, ncards, fuzzMvrs, simFuzz, quantile, pctPhantoms, ncontests)
            // fun startTestElectionOneAudit(
            //    topdir: String,
            //    minMargin: Double,
            //    fuzzMvrs: Double,
            //    simFuzz: Double,
            //    quantile: Double,
            //    phantomPct: Double,
            //    ncards: Int,
            //    strategy: String,
            //    cvrFraction: Double,
            //    extraPct: Double,
            "ONEAUDIT" ->  startTestElectionOneAudit(topdir, minMargin, fuzzMvrs, simFuzz, quantile, pctPhantoms, ncards, oaStrategy, cvrFraction, extraPct=extra)
            else ->  startTestElectionClca(topdir, minMargin, fuzzMvrs, simFuzz, quantile, pctPhantoms, ncards, ncontests, addRaireContest, addRaireCandidates)
        }
    }
}

///////////////////////////////////////////////////////
fun startTestElectionClca(
    topdir: String,
    minMargin: Double,
    fuzzMvrs: Double,
    simFuzz: Double,
    quantile: Double,
    pctPhantoms: Double?,
    ncards: Int,
    ncontests: Int,
    addRaire: Boolean,
    addRaireCandidates: Int,
) {
    val auditDir = "$topdir/audit"
    clearDirectory(Path(auditDir))

    val config = AuditConfig(
        AuditType.CLCA, nsimEst = 100, simFuzzPct = simFuzz, quantile = quantile,
        clcaConfig = ClcaConfig(fuzzMvrs=fuzzMvrs)
    )

    clearDirectory(Path(auditDir))
    val election = TestClcaElection(
        config,
        minMargin,
        pctPhantoms,
        ncards,
        ncontests,
        addRaire,
        addRaireCandidates)

    CreateAudit("startTestElectionClca", config, election, auditDir = "$topdir/audit", clear = false)
}

class TestClcaElection(
    val config: AuditConfig,
    minMargin: Double,
    pctPhantoms: Double?,
    ncards: Int,
    ncontests: Int,
    addRaire: Boolean,
    addRaireCandidates: Int,
): CreateElectionIF {
    val contestsUA = mutableListOf<ContestWithAssertions>()
    val allCvrs = mutableListOf<Cvr>()

    init {
        val maxMargin = .10
        val useMin = min(minMargin, maxMargin)
        val phantomPctRange: ClosedFloatingPointRange<Double> =
            if (pctPhantoms == null) 0.00..0.005 else pctPhantoms..pctPhantoms

        val testData =
            MultiContestTestData(ncontests, 4, ncards, marginRange = useMin..maxMargin, phantomPctRange = phantomPctRange)
        println("$testData")

        // Synthetic cvrs for testing, reflecting the exact contest votes, plus undervotes and phantoms.
        // includes phantom Cvrs
        allCvrs.addAll(testData.makeCvrsFromContests())
        println("ncvrs (not raire) = ${allCvrs.size}")

        if (addRaire) {
            val (rcontest: RaireContestWithAssertions, rcvrs: List<Cvr>) = simulateRaireTestContest(N=ncards/2, contestId=111, addRaireCandidates, minMargin=.04, quiet = true)
            contestsUA.add(rcontest)
            allCvrs.addAll(rcvrs)
        }

        val regularContests = testData.contests.map { ContestWithAssertions(it, isClca=true).addStandardAssertions() }
        contestsUA.addAll(regularContests)
        contestsUA.forEach { println("  $it") }
        println()
    }
    override fun populations() = null
    override fun cardPools() = null
    override fun contestsUA() = contestsUA

    override fun cardManifest() : CloseableIterator<AuditableCard> {
        return CvrsWithPopulationsToCardManifest(
            config.auditType,
            Closer(allCvrs.iterator()),
            null, null
        )
    }
}

/////////////////////////////////////////////////////

fun startTestElectionPolling(
    topdir: String,
    minMargin: Double,
    ncards: Int,
    fuzzMvrs: Double = .001,
    simFuzz: Double = .001,
    quantile: Double = .80,
    pctPhantoms: Double = 0.0,
    ncontests: Int = 11,
) {
    val auditDir = "$topdir/audit"
    clearDirectory(Path(auditDir))
    val config = AuditConfig(AuditType.POLLING, nsimEst = 100, simFuzzPct = simFuzz,
        persistedWorkflowMode = PersistedWorkflowMode.testPrivateMvrs, quantile=quantile)

    clearDirectory(Path(auditDir))
    val election = TestPollingElection(
        auditDir,
        config,
        minMargin,
        fuzzMvrs,
        pctPhantoms,
        ncards,
        ncontests,
    )

    // dont clear, we've already started writing
    CreateAudit("startTestElectionPolling", config, election, auditDir = auditDir, clear=false)
}

class TestPollingElection(
    val auditdir: String,
    val config: AuditConfig,
    minMargin: Double,
    fuzzMvrs: Double,
    pctPhantoms: Double?,
    ncards: Int,
    ncontests: Int,
): CreateElectionIF {
    val contestsUA = mutableListOf<ContestWithAssertions>()
    val cvrs: List<Cvr>
    val testMvrs: List<Cvr>
    val pops: List<Population>

    init {
        val maxMargin = .08
        val useMin = min(minMargin, maxMargin)
        val phantomPctRange: ClosedFloatingPointRange<Double> =
            if (pctPhantoms == null) 0.00..0.005 else pctPhantoms..pctPhantoms
        val testData = MultiContestTestData(ncontests, 4, ncards, marginRange = useMin..maxMargin, phantomPctRange = phantomPctRange)

        val contests: List<Contest> = testData.contests
        println("Start testPersistentWorkflowPolling $testData")
        contests.forEach { println("  $it") }
        println()
        val pop = Population("all", 1, contests.map { it.id }.toIntArray(), hasSingleCardStyle=false)
        pops = listOf(pop)

        // Synthetic cvrs for testing, reflecting the exact contest votes, plus undervotes and phantoms.
        cvrs = testData.makeCvrsFromContests()
        testMvrs = makeFuzzedCvrsForPolling(contests.map{ it.info() }, cvrs, fuzzMvrs) // ??

        val makum = ContestWithAssertions.make(testData.contests, cardManifest(), isClca=false)
        // not setting Npop, so it defaults to Nc
        //val regularContests = testData.contests.map {
        //    ContestUnderAudit(it, isClca=true, hasStyle=config.hasStyle).addStandardAssertions()
        //}
        contestsUA.addAll(makum)
        contestsUA.forEach { println("  $it") }
        println()

        val infos = contestsUA().map { it.contest.info() }.associateBy { it.id }
        val mvrTabs = tabulateCvrs(testMvrs.iterator(), infos)
        println("testMvrs = ${mvrTabs}")
        println()

        // have to write this here, where we know the mvrs
        writeUnsortedPrivateMvrs(Publisher(auditdir), testMvrs, seed=config.seed)
    }

    override fun populations() = pops
    override fun cardPools() = null
    override fun contestsUA() = contestsUA

    override fun cardManifest() : CloseableIterator<AuditableCard> {
        return CvrsWithPopulationsToCardManifest(
            config.auditType,
            Closer(cvrs.iterator()),
            null,
            populations(),
        )
    }
}

////////////////////////////////

fun startTestElectionOneAudit(
    topdir: String,
    minMargin: Double,
    fuzzMvrs: Double,
    simFuzz: Double,
    quantile: Double,
    phantomPct: Double,
    ncards: Int,
    strategy: String,
    cvrFraction: Double,
    extraPct: Double,
) {
    val auditDir = "$topdir/audit"
    clearDirectory(Path(auditDir))

    val config = AuditConfig(
        AuditType.ONEAUDIT, nsimEst = 10, simFuzzPct = simFuzz, quantile = quantile,
        persistedWorkflowMode = PersistedWorkflowMode.testPrivateMvrs,
        clcaConfig = ClcaConfig(fuzzMvrs=fuzzMvrs),
        oaConfig = if (strategy == "calc") OneAuditConfig(strategy = OneAuditStrategyType.calcMvrsNeeded) else OneAuditConfig()
    )
    clearDirectory(Path(auditDir))

    //     val config: AuditConfig,
    //    minMargin: Double,
    //    cvrFraction: Double,
    //    ncards: Int,
    //    phantomPct: Double,
    //    extraPct: Double,
    val election = TestOneAuditElection(
        config,
        minMargin,
        cvrFraction = cvrFraction,
        ncards,
        phantomPct=phantomPct,
        extraPct=extraPct,
    )

    CreateAudit("RunRlaStartOneAudit", config, election, auditDir = "$topdir/audit", clear = false)

    // write the sorted cards: why isnt this part of CreateAudit? Because seed must be generated after committment to cardManifest
    val publisher = Publisher(auditDir)
    writeSortedCardsInternalSort(publisher, config.seed)

    // simulate the mvrs, write to private dir
    val contests = readContestsJsonFileUnwrapped(publisher.contestsFile())
    val infos = contests.map{ it.contest.info() }.associateBy { it.id }
    val cardManifest = readCardManifest(publisher)
    val cardPools = readCardPools(publisher, infos)
    val scardIter = cardManifest.cards.iterator()
    val sortedCards = mutableListOf<AuditableCard>()
    scardIter.forEach { sortedCards.add(it) }

    // OneAuditVunderFuzzer creates fuzzed mvrs (non-pooled) and simulated mvrs (pooled)
    val vunderFuzz = OneAuditVunderFuzzer(cardPools!!, infos, fuzzMvrs, sortedCards)
    val oaFuzzedPairs: List<Pair<AuditableCard, AuditableCard>> = vunderFuzz.mvrCvrPairs
    val sortedMvrs = oaFuzzedPairs.map { it.first }

    // have to write this here, where we know the mvrs
    writePrivateMvrs(publisher, sortedMvrs)
}

class TestOneAuditElection(
    val config: AuditConfig,
    minMargin: Double,
    cvrFraction: Double,
    ncards: Int,
    phantomPct: Double,
    extraPct: Double,
): CreateElectionIF {
    val contestsUA = mutableListOf<ContestWithAssertions>()
    val cardPools: List<OneAuditPoolFromCvrs>
    val cardManifest: List<AuditableCard>

    init {
        // one contest
        val (contestOA, mvrs, cardManifest, pools) =
            makeOneAuditTest(
                margin = minMargin,
                Nc = ncards,
                cvrFraction = cvrFraction,
                undervoteFraction = .01,
                phantomFraction = phantomPct,
                extraInPool= (extraPct * ncards).toInt(),
            )
        contestsUA.add(contestOA)
        this.cardManifest = cardManifest
        this.cardPools = pools
    }

    override fun populations() = cardPools
    override fun cardPools() = cardPools
    override fun contestsUA() = contestsUA
    override fun cardManifest() = Closer (cardManifest.iterator() )
}
