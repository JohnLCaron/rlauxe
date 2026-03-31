package org.cryptobiotic.rlauxe.cli

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.audit.*

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.workflow.makeFuzzedCvrsForClca
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.irv.RaireContestWithAssertions
import org.cryptobiotic.rlauxe.irv.simulateRaireTestContest
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditTest
import org.cryptobiotic.rlauxe.util.tabulateCvrs
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
        val pollingMode by parser.option(
            ArgType.Choice<PollingMode>(),
            shortName = "f",
            description = "Format"
        ).default(PollingMode.withBatches)
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
                    " addRaire=$addRaireContest addRaireCandidates=$addRaireCandidates")
            when (auditType) {
                "POLLING" -> startTestElectionPolling(topdir, minMargin, ncards, simFuzz, pctPhantoms, ncontests, pollingMode)
                "ONEAUDIT" ->  startTestElectionOneAudit(topdir, minMargin, fuzzMvrs, simFuzz, pctPhantoms, ncards, cvrFraction, extraPct=extra)
                else ->  startTestElectionClca(topdir, minMargin, fuzzMvrs, simFuzz, pctPhantoms, ncards, ncontests, addRaireContest, addRaireCandidates)
            }
        } catch (t: Throwable) {
            println(t.message)
            throw t
        }
    }
}

///////////////////////////////////////////////////////
fun startTestElectionClca(
    topdir: String,
    minMargin: Double,
    fuzzMvrs: Double,
    simFuzz: Double,
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

    val config = Config.from( election.electionInfo(), nsimTrials = 100, simFuzzPct = simFuzz, fuzzMvrs=fuzzMvrs)

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
): ElectionBuilder {
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

    override fun cards() : CloseableIterator<CardWithBatchName> {
        return CvrsToCardsWithBatchNameIterator(
            AuditType.CLCA,
            Closer(allCvrs.iterator()),
            null, null,
        )
    }
}

// interface ElectionBuilder {
//    fun electionInfo(): ElectionInfo
//    fun contestsUA(): List<ContestWithAssertions>
//
//    // if you immediately write to disk, you only need one pass through the cards iterator
//    fun cards() : CloseableIterator<CardNoBatch>
//    fun ncards(): Int
//
//    // probably implementations should put out both ? Let the auditor decide how to use ??
//    fun batches(): List<BatchIF>?
//    fun cardPools(): List<CardPool>?
//    fun createUnsortedMvrsInternal(): List<Cvr>? // for in-memory case, poolId used also as batch name?
//    fun createUnsortedMvrsExternal(): CloseableIterator<CardNoBatch>? // for out-of-memory case
//}

/////////////////////////////////////////////////////

fun startTestElectionPolling(
    topdir: String,
    minMargin: Double,
    ncards: Int,
    simFuzz: Double = .001, // TODO does this make sense, to fuzz polling ??
    pctPhantoms: Double = 0.0,
    ncontests: Int = 11,
    pollingMode: PollingMode,
) {
    val auditdir = "$topdir/audit"

    val election = TestPollingElection(
        auditdir,
        minMargin,
        pctPhantoms,
        ncards,
        ncontests,
        pollingMode
    )
    createElectionRecord(election, auditDir = auditdir, )

    val config = Config.from(election.electionInfo(), nsimTrials = 20, simFuzzPct = simFuzz)

    createAuditRecord(config, election, auditDir = auditdir)

    val result = startFirstRound(auditdir)
    if (result.isErr) logger.error{ result.toString() }
}

class TestPollingElection(
    val auditdir: String,
    minMargin: Double,
    pctPhantoms: Double?,
    ncards: Int,
    ncontests: Int,
    val pollingMode: PollingMode,
): ElectionBuilder {
    val contests: List<Contest>
    val testMvrs: List<Cvr>
    val batches: List<BatchIF>
    // val pools: List<CardPoolIF>
    val cards: List<CardWithBatchName>
    val contestsUA: List<ContestWithAssertions>

    init {
        val maxMargin = .08
        val useMin = min(minMargin, maxMargin)
        val phantomPctRange: ClosedFloatingPointRange<Double> =
            if (pctPhantoms == null) 0.00..0.005 else pctPhantoms..pctPhantoms
        val testData = MultiContestTestData(ncontests, 4, ncards, marginRange = useMin..maxMargin,
            phantomPctRange = phantomPctRange, auditType = AuditType.POLLING) // always poolid = 1

        contests = testData.contests
        println("Start testPersistentWorkflowPolling $testData")
        contests.forEach { println("  $it") }

        // Synthetic cvrs for testing, reflecting the exact contest votes, plus undervotes and phantoms.
        val mvrCardAndPops = testData.makeMvrCardAndPops()
        this.testMvrs  = mvrCardAndPops.mvrs
        this.cards = mvrCardAndPops.cards.map { CardWithBatchName(it) }
        // this.pools = mvrCardAndPops.pools
        this.batches = mvrCardAndPops.batches

        // testMvrs = makeFuzzedCvrsForClca(contests.map{ it.info() } , cvrs, fuzzMvrs)

        contestsUA = testData.contests.map { ContestWithAssertions(it, isClca=false).addStandardAssertions() }
        contestsUA.forEach { println("  $it") }

        println()
    }

    override fun electionInfo() = ElectionInfo(
        "TestPollingElection", AuditType.POLLING, ncards(), contestsUA.size,
        cvrsContainUndervotes = true, poolsHaveOneCardStyle = null, pollingMode = pollingMode,
    )
    override fun createUnsortedMvrsInternal() = testMvrs // for in-memory case
    override fun createUnsortedMvrsExternal() = null
    override fun batches() = batches
    override fun cardPools() = null
    override fun contestsUA() = contestsUA
    override fun ncards() = cards.size
    override fun cards() = Closer(cards.iterator())
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
    phantomPct: Double,
    ncards: Int,
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

    val config = Config.from(election.electionInfo(), nsimTrials = 20, simFuzzPct = simFuzz,
        fuzzMvrs=fuzzMvrs)

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
): ElectionBuilder {
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
    override fun batches() = null
    override fun cardPools() = cardPools
    override fun contestsUA() = contestsUA
    override fun ncards() = cards.size

    // override fun cards() = Closer ( cards.iterator())

    override fun cards() : CloseableIterator<CardWithBatchName> {
        return Closer( this.cards.map { CardWithBatchName(it) }.iterator())
    }

}
