package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.cli.startTestElectionPolling
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsForClca
import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditTest
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.ConcurrentTask
import kotlin.Int
import kotlin.test.Test
import kotlin.test.fail

class TestPersistedWorkflow {

    @Test
    fun testPersistedSingleClca() {
        // val topdir = kotlin.io.path.createTempDirectory().toString()
        val topdir = "$testdataDir/persist/persistWorkflow/singleClca"
        val auditdir = "$topdir/audit"

        val N = 50000
        val testData = MultiContestTestData(1, 1, N, marginRange=0.03..0.03, ncands=2)

        val contests: List<Contest> = testData.contests
        println("Start testPersistedSingleClca $testData")

        // Synthetic cvrs for testing reflecting the exact contest votes, already has undervotes and phantoms.
        val testMvrs = testData.makeCvrsFromContests()
        val contestsUA = contests.map { ContestWithAssertions(it, isClca = true).addStandardAssertions() }

        val election = CreateElectionFromCvrs("testPersistedSingleClca", contestsUA, testMvrs, AuditType.CLCA, mvrSource=MvrSource.testPrivateMvrs)
        createElectionRecord(election, auditDir = auditdir)

        val config = Config.from(election.electionInfo(), nsimTrials = 10, contestSampleCutoff = 1000, simFuzzPct = .01)

        createAuditRecord(config, election, auditDir = auditdir, externalSortDir=topdir)
        startFirstRound(auditdir)

        runPersistedAudit(topdir)
    }

    @Test
    fun testPersistedAuditClca() {
        // val topdir = kotlin.io.path.createTempDirectory().toString()
        val topdir = "$testdataDir/persist/persistWorkflow/clca"
        val auditdir = "$topdir/audit"

        val N = 50000
        val testData = MultiContestTestData(11, 4, N, marginRange=0.03..0.05)

        val contests: List<Contest> = testData.contests
        println("Start testPersistedAuditClca $testData")

        // Synthetic cvrs for testing reflecting the exact contest votes, already has undervotes and phantoms.
        val testMvrs = testData.makeCvrsFromContests()
        val contestsUA = contests.map { ContestWithAssertions(it, isClca = true).addStandardAssertions() }

        val election = CreateElectionFromCvrs("testPersistedAuditClca", contestsUA, testMvrs, AuditType.CLCA, mvrSource=MvrSource.testPrivateMvrs)
        createElectionRecord(election, auditDir = auditdir)

        val config = Config.from(election.electionInfo(), nsimTrials = 10, contestSampleCutoff = 1000, simFuzzPct = .01)
        createAuditRecord(config, election, auditDir = auditdir, externalSortDir=topdir)
        startFirstRound(auditdir)

        runPersistedAudit(topdir)
    }

    @Test
    fun testPersistedAuditPolling() {
        val topdir = "$testdataDir/persist/persistWorkflow/polling"
        val N = 50000
        startTestElectionPolling(topdir, minMargin = .11, fuzzMvrs = .00, pctPhantoms = 0.00, ncards = N, ncontests = 1)
        runPersistedAudit(topdir, maxRounds=10)
    }

    @Test
    fun testPersistedOneAudit() {
        // val topdir = kotlin.io.path.createTempDirectory().toString()
        val topdir = "$testdataDir/persist/persistWorkflow/oneaudit"
        val auditdir = "$topdir/audit"

        val N = 5000
        // Synthetic cvrs for testing reflecting the exact contest votes, already has undervotes and phantoms.
        val (contestOA, mvrs, cards, cardPools) = makeOneAuditTest(
            N + 100,
            N - 100,
            cvrFraction = .95,
            undervoteFraction = .0,
            phantomFraction = .0
        )

        val contestsUA = listOf(contestOA)

        val election = CreateElectionFromCvrs("testPersistedOneAudit", contestsUA, mvrs, AuditType.ONEAUDIT,
            cardPools = cardPools, mvrSource=MvrSource.testPrivateMvrs)
        createElectionRecord(election, auditDir = auditdir)

        val config = Config.from(election.electionInfo(), nsimTrials = 10, contestSampleCutoff = 20000, simFuzzPct = .01)

        createAuditRecord(config, election, auditDir = auditdir, externalSortDir=topdir)
        startFirstRound(auditdir)

        runPersistedAudit(topdir, maxRounds=10)
    }

    @Test
    fun problem() {
        // from ExtraVsMarginOneAudit
        // 2026-03-23 16:11:19.947 WARN   runAudit OneAuditWorkflowTaskGenerator margin=0.01 mvrsFuzzPct=0.0 cvrPercent=0.9 11 exceeded maxRounds = 10
        val Nc = 50000
        val margin = .01
        val mvrFuzzPct = .00
        val ntrials = 1
        val nsimTrials = 100
        val cvrPercent = .50

        val topdir = "$testdataDir/persist/persistWorkflow/oneauditProblem2"
        val auditdir = "$topdir/audit"

        val electionInfo = ElectionInfo.forTest(AuditType.ONEAUDIT, MvrSource.testPrivateMvrs)
        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit=.05,)
        val config = Config(electionInfo, creation, round =
            AuditRoundConfig(
                SimulationControl(nsimTrials=nsimTrials),
                sampling = ContestSampleControl.NONE,
                ClcaConfig(), null)
        )

        val (contestOA, mvrs, cards, pools) = makeOneAuditTest(
            margin,
            Nc,
            cvrFraction = cvrPercent,
            undervoteFraction = 0.0,
            phantomFraction = 0.0
        )

        val contestsUA = listOf(contestOA)

        val election = CreateElectionFromCvrs("testPersistedOneAudit", contestsUA, mvrs, AuditType.ONEAUDIT,
            cardPools = pools, mvrSource=MvrSource.testPrivateMvrs)
        createElectionRecord(election, auditDir = auditdir)

        createAuditRecord(config, election, auditDir = auditdir, externalSortDir=topdir)
        startFirstRound(auditdir)

        runPersistedAudit(topdir, maxRounds=10)
    }
}

fun runPersistedAudit(topdir: String, maxRounds:Int = 6) {
    val auditdir = "$topdir/audit"

    val verifyResults = RunVerifyContests.runVerifyContests(auditdir, null, show = true)
    println()
    print(verifyResults)
    if (verifyResults.hasErrors) fail()

    println("============================================================")
    var done = false
    var lastRound: AuditRoundIF? = null

    while (!done) {
        lastRound = runRound(inputDir = auditdir)
        if (lastRound == null) fail()

        /* TODO!! ??
        val enterResult = enterMvrs(auditdir, publisher.sortedCardsFile())
        if (enterResult is Err) {
            println("enterMvrs failed ${enterResult.error}")
            fail()
        } */

        done = lastRound.auditIsComplete || lastRound.roundIdx > maxRounds
    }

    if (lastRound != null) {
        println("nrounds = ${lastRound.roundIdx} nmvrs = ${lastRound.nmvrs} topdir=$topdir")
    } else {
        println("failed in topdir=$topdir")
        fail()
    }
}