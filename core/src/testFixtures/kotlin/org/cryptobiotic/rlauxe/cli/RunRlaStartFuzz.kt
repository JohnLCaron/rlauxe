package org.cryptobiotic.rlauxe.cli

import io.github.oshai.kotlinlogging.KotlinLogging
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
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsForClca
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.raire.RaireContestWithAssertions
import org.cryptobiotic.rlauxe.raire.simulateRaireTestContest
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditTest
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode
import kotlin.math.min

private val logger = KotlinLogging.logger("RunRlaStartFuzz")

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

        try {
            parser.parse(args)
            println("RunRlaStartFuzz on $topdir auditType=$auditType minMargin=$minMargin fuzzMvrs=$fuzzMvrs, simFuzz=$simFuzz, pctPhantoms=$pctPhantoms, ncards=$ncards ncontests=$ncontests" +
                    " addRaire=$addRaireContest addRaireCandidates=$addRaireCandidates quantile=$quantile")
            when (auditType) {
                "POLLING" -> startTestElectionPolling(topdir, minMargin, ncards, fuzzMvrs, simFuzz, quantile, pctPhantoms, ncontests)
                "ONEAUDIT" ->  startTestElectionOneAudit(topdir, minMargin, fuzzMvrs, simFuzz, quantile, pctPhantoms, ncards, oaStrategy, cvrFraction, extraPct=extra)
                else ->  startTestElectionClca(topdir, minMargin, fuzzMvrs, simFuzz, quantile, pctPhantoms, ncards, ncontests, addRaireContest, addRaireCandidates)
            }
        } catch (t: Throwable) {
            println(t.message)
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
    val auditdir = "$topdir/audit"

    val election = TestClcaElection(
        minMargin,
        pctPhantoms,
        ncards,
        ncontests,
        addRaire,
        addRaireCandidates)
    createElectionRecord(election, auditDir = auditdir)

    val config = AuditConfig(
        AuditType.CLCA, nsimEst = 100, simFuzzPct = simFuzz, quantile = quantile,
        clcaConfig = ClcaConfig(fuzzMvrs=fuzzMvrs)
    )

    createAuditRecord(config, election, auditDir = auditdir)

    val result = startFirstRound(auditdir)
    if (result.isErr) error{ result.toString() }
}

class TestClcaElection(
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

    override fun electionInfo() = ElectionInfo(
        "TestClcaElection", AuditType.CLCA, ncards(), contestsUA.size, cvrsContainUndervotes = true, poolsHaveOneCardStyle = null,
    )
    override fun createUnsortedMvrsInternal() = allCvrs // for in-memory case
    override fun createUnsortedMvrsExternal() = null
    override fun batches() = null
    override fun cardPools() = null
    override fun contestsUA() = contestsUA
    override fun ncards() = allCvrs.size

    override fun cards() : CloseableIterator<AuditableCard> {
        return CvrsToCardManifest(
            AuditType.CLCA,
            Closer(allCvrs.iterator()),
            null, null,
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
    val auditdir = "$topdir/audit"

    val election = TestPollingElection(
        auditdir,
        minMargin,
        fuzzMvrs,
        pctPhantoms,
        ncards,
        ncontests,
    )
    createElectionRecord(election, auditDir = auditdir)

    val config = AuditConfig(AuditType.POLLING, nsimEst = 100, simFuzzPct = simFuzz,
        persistedWorkflowMode = PersistedWorkflowMode.testPrivateMvrs, quantile=quantile)

    createAuditRecord(config, election, auditDir = auditdir)

    val result = startFirstRound(auditdir)
    if (result.isErr) logger.error{ result.toString() }
}

class TestPollingElection(
    val auditdir: String,
    minMargin: Double,
    fuzzMvrs: Double,
    pctPhantoms: Double?,
    ncards: Int,
    ncontests: Int,
): CreateElectionIF {
    val contestsUA: List<ContestWithAssertions>
    val cvrs: List<Cvr>
    val testMvrs: List<Cvr>
    val contests: List<Contest>

    init {
        val maxMargin = .08
        val useMin = min(minMargin, maxMargin)
        val phantomPctRange: ClosedFloatingPointRange<Double> =
            if (pctPhantoms == null) 0.00..0.005 else pctPhantoms..pctPhantoms
        val testData = MultiContestTestData(ncontests, 4, ncards, marginRange = useMin..maxMargin,
            phantomPctRange = phantomPctRange) // always poolid = 1

        contests = testData.contests
        println("Start testPersistentWorkflowPolling $testData")
        contests.forEach { println("  $it") }

        // Synthetic cvrs for testing, reflecting the exact contest votes, plus undervotes and phantoms.
        cvrs = testData.makeCvrsFromContests(42)
        testMvrs = makeFuzzedCvrsForClca(contests.map{ it.info() } , cvrs, fuzzMvrs)

        contestsUA = ContestWithAssertions.make(testData.contests, cards(), isClca=false)
        contestsUA.forEach { println("  $it") }
        println()
    }

    override fun electionInfo() = ElectionInfo(
        "TestPollingElection", AuditType.POLLING, ncards(), contestsUA.size, cvrsContainUndervotes = true, poolsHaveOneCardStyle = null,
    )
    override fun createUnsortedMvrsInternal() = testMvrs // for in-memory case
    override fun createUnsortedMvrsExternal() = null
    override fun batches() = listOf( Batch("batch42", 42, contests.map{it.id}.toIntArray(), true))  // no batches !!
    override fun cardPools() = null
    override fun contestsUA() = contestsUA
    override fun ncards() = cvrs.size

    override fun cards() : CloseableIterator<AuditableCard> {
        return CvrsToCardManifest(
            AuditType.POLLING,
            Closer(cvrs.iterator()),
            null,
            batches(),
        )
    }
}

fun makeOnePool(poolId: Int, contests: List<Contest>, cvrs: List<Cvr>): CardPool {
    val infos = contests.associate { it.id to it.info() }

    // can just use contest totals. a contest can generate a Vunder
    val contestTabs = tabulateCvrs(cvrs.iterator(), infos)
    return CardPool("all", poolId, hasSingleCardStyle=true, infos, contestTabs, cvrs.size)
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

    val election = TestOneAuditElection(
        auditDir,
        minMargin,
        cvrFraction = cvrFraction,
        ncards,
        phantomPct=phantomPct,
        extraPct=extraPct,
        fuzzMvrs=fuzzMvrs,
    )
    createElectionRecord(election, auditDir = auditDir, clear = true)

    val config = AuditConfig(
        AuditType.ONEAUDIT, nsimEst = 10, simFuzzPct = simFuzz, quantile = quantile,
        persistedWorkflowMode = PersistedWorkflowMode.testPrivateMvrs,
        clcaConfig = ClcaConfig(fuzzMvrs=fuzzMvrs),
        simulationStrategy = if (strategy == "calc") SimulationStrategy.optimistic else SimulationStrategy.regular,
    )

    createAuditRecord(config, election, auditDir = auditDir)

    val result = startFirstRound(auditDir)
    if (result.isErr) error{ result.toString() }
}

class TestOneAuditElection(
    val auditDir: String,
    minMargin: Double,
    cvrFraction: Double,
    ncards: Int,
    phantomPct: Double,
    extraPct: Double,
    fuzzMvrs: Double,
): CreateElectionIF {
    val contestsUA = mutableListOf<ContestWithAssertions>()
    val cardPools: List<CardPool>
    val cards: List<AuditableCard>
    val fuzzedMvrs: List<Cvr>

    init {
        // one contest
        val (contestOA, mvrs, cards, pools) =
            makeOneAuditTest(
                margin = minMargin,
                Nc = ncards,
                cvrFraction = cvrFraction,
                undervoteFraction = .01,
                phantomFraction = phantomPct,
                extraInPool= (extraPct * ncards).toInt(),
            )
        contestsUA.add(contestOA)
        this.cards = cards
        this.cardPools = pools

        // just need to fuzz the mvrs
        val infoList = listOf(contestOA.contest.info())
        this.fuzzedMvrs = makeFuzzedCvrsForClca(infoList, mvrs, fuzzMvrs)
    }

    override fun electionInfo() = ElectionInfo(
        "TestOneAuditElection", AuditType.ONEAUDIT, ncards(), contestsUA.size, cvrsContainUndervotes = true, poolsHaveOneCardStyle = true,
    )
    override fun createUnsortedMvrsInternal() = fuzzedMvrs // for in-memory case
    override fun createUnsortedMvrsExternal() = null
    override fun batches() = cardPools
    override fun cardPools() = cardPools
    override fun contestsUA() = contestsUA
    override fun ncards() = cards.size

    override fun cards() = Closer ( cards.iterator())
}
