package org.cryptobiotic.rlauxe.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.audit.*

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.raire.simulateRaireTestContest
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.CvrToAuditableCardClca
import org.cryptobiotic.rlauxe.util.CvrToAuditableCardPolling
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import kotlin.io.path.Path
import kotlin.math.min

/**
 * Create a multicontest audit, with fuzzed test data, stored in private record.
 */
object RunRlaStartFuzz {

    @JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgParser("RunRlaStartFuzz")
        val topdir by parser.option(
            ArgType.String,
            shortName = "in",
            description = "Directory containing test election record"
        ).required()
        val isPolling by parser.option(
            ArgType.Boolean,
            shortName = "isPolling",
            description = "Polling election"
        ).default(false)
        val minMargin by parser.option(
            ArgType.Double,
            shortName = "minMargin",
            description = "contest minimum margin"
        ).default(0.04)
        val fuzzMvrs by parser.option(
            ArgType.Double,
            shortName = "fuzzMvrs",
            description = "Fuzz Mvrs by this factor (0.0 to 1.0)"
        ).default(0.0)
        val pctPhantoms by parser.option(
            ArgType.Double,
            shortName = "pctPhantoms",
            description = "Pct phantoms (0.0 to 1.0)"
        )
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

        parser.parse(args)
        println("RunRlaStartFuzz on $topdir isPolling=$isPolling minMargin=$minMargin fuzzMvrs=$fuzzMvrs, pctPhantoms=$pctPhantoms, ncards=$ncards ncontests=$ncontests" +
                " addRaire=$addRaireContest addRaireCandidates=$addRaireCandidates")
        if (!isPolling) startTestElectionClca(topdir, minMargin, fuzzMvrs, pctPhantoms, ncards, ncontests, addRaireContest, addRaireCandidates)
            else startTestElectionPolling(topdir, minMargin, fuzzMvrs, pctPhantoms, ncards, ncontests)
    }
}

fun startTestElectionPolling(
    topdir: String,
    minMargin: Double,
    fuzzMvrsPct: Double,
    pctPhantoms: Double?,
    ncards: Int,
    ncontests: Int = 11,
) {
    val auditDir = "$topdir/audit"
    clearDirectory(Path(auditDir))
    val config = AuditConfig(AuditType.POLLING, hasStyle = true, nsimEst = 100)

    clearDirectory(Path(auditDir))
    val election = TestPollingElection(
        config,
        minMargin,
        fuzzMvrsPct,
        pctPhantoms,
        ncards,
        ncontests,
    )
    CreateAudit("startTestElectionPolling", topdir = topdir, config, election, clear = false)
}

class TestPollingElection(
    config: AuditConfig,
    minMargin: Double,
    fuzzMvrsPct: Double,
    pctPhantoms: Double?,
    ncards: Int,
    ncontests: Int,
): CreateElectionIF {
    val contestsUA = mutableListOf<ContestUnderAudit>()
    val cvrs: List<Cvr>
    val testMvrs: List<Cvr>

    init {
        val maxMargin = .08
        val useMin = min(minMargin, maxMargin)
        val phantomPctRange: ClosedFloatingPointRange<Double> =
            if (pctPhantoms == null) 0.00..0.005 else pctPhantoms..pctPhantoms
        val testData = MultiContestTestData(ncontests, 4, ncards, config.hasStyle, marginRange = useMin..maxMargin, phantomPctRange = phantomPctRange)

        val contests: List<Contest> = testData.contests
        println("Start testPersistentWorkflowPolling $testData")
        contests.forEach { println("  $it") }
        println()

        // Synthetic cvrs for testing, reflecting the exact contest votes, plus undervotes and phantoms.
        val (testCvrs, ballots) = testData.makeCvrsAndBallots()
        cvrs = testCvrs
        testMvrs = makeFuzzedCvrsFrom(contests, testCvrs, fuzzMvrsPct) // ??

        val regularContests = testData.contests.map { ContestUnderAudit(it, isClca=true, hasStyle=config.hasStyle).addStandardAssertions() }
        contestsUA.addAll(regularContests)
        contestsUA.forEach { println("  $it") }
        println()

        val infos = contestsUA().map { it.contest.info() }.associateBy { it.id }
        val mvrTabs = tabulateCvrs(testMvrs.iterator(), infos)
        println("testMvrs = ${mvrTabs}")
        println()
    }
    override fun cardPools() = null

    override fun contestsUA() = contestsUA

    override fun allCvrs() = Pair(
        CvrToAuditableCardPolling(Closer(cvrs.iterator())),
        CvrToAuditableCardPolling(Closer(testMvrs.iterator()))
    )
}

fun startTestElectionClca(
    topdir: String,
    minMargin: Double,
    fuzzMvrs: Double,
    pctPhantoms: Double?,
    ncards: Int,
    ncontests: Int,
    addRaire: Boolean,
    addRaireCandidates: Int,
) {
    val auditDir = "$topdir/audit"
    clearDirectory(Path(auditDir))

    val config = AuditConfig(
        AuditType.CLCA, hasStyle = true, nsimEst = 100,
        clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous)
    )

    clearDirectory(Path(auditDir))
    val election = TestClcaElection(
        config,
        minMargin,
        fuzzMvrs,
        pctPhantoms,
        ncards,
        ncontests,
        addRaire,
        addRaireCandidates)

    CreateAudit("startTestElectionClca", topdir = topdir, config, election, clear = false)
}

class TestClcaElection(
    config: AuditConfig,
    minMargin: Double,
    fuzzMvrs: Double,
    pctPhantoms: Double?,
    ncards: Int,
    ncontests: Int,
    addRaire: Boolean,
    addRaireCandidates: Int,
): CreateElectionIF {
    val contestsUA = mutableListOf<ContestUnderAudit>()
    val allCvrs = mutableListOf<Cvr>()
    val testMvrs: List<Cvr>

    init {
        val maxMargin = .10
        val useMin = min(minMargin, maxMargin)
        val phantomPctRange: ClosedFloatingPointRange<Double> =
            if (pctPhantoms == null) 0.00..0.005 else pctPhantoms..pctPhantoms

        val testData =
            MultiContestTestData(ncontests, 4, ncards, config.hasStyle, marginRange = useMin..maxMargin, phantomPctRange = phantomPctRange)
        println("$testData")

        // Synthetic cvrs for testing, reflecting the exact contest votes, plus undervotes and phantoms.
        // includes phantom Cvrs
        allCvrs.addAll(testData.makeCvrsFromContests())
        println("ncvrs (not raire) = ${allCvrs.size}")

        if (addRaire) {
            val (rcontest: RaireContestUnderAudit, rcvrs: List<Cvr>) = simulateRaireTestContest(N=ncards/2, contestId=111, addRaireCandidates, minMargin=.04, quiet = true, hasStyle=config.hasStyle)
            contestsUA.add(rcontest)
            allCvrs.addAll(rcvrs)
        }

        val regularContests = testData.contests.map { ContestUnderAudit(it, isClca=true, hasStyle=config.hasStyle).addStandardAssertions() }
        contestsUA.addAll(regularContests)
        contestsUA.forEach { println("  $it") }
        println()

        testMvrs = if (fuzzMvrs == 0.0) allCvrs
            else makeFuzzedCvrsFrom(contestsUA.map { it.contest}, allCvrs, fuzzMvrs)
        println("nmvrs = ${testMvrs.size}")

        val infos = contestsUA().map { it.contest.info() }.associateBy { it.id }
        val mvrTabs = tabulateCvrs(testMvrs.iterator(), infos)
        println("testMvrs = ${mvrTabs}")
        println()
    }
    override fun cardPools() = null
    override fun contestsUA() = contestsUA

    override fun allCvrs() = Pair(
        CvrToAuditableCardClca(Closer(allCvrs.iterator())),
        CvrToAuditableCardClca(Closer(testMvrs.iterator()))
    )
}

