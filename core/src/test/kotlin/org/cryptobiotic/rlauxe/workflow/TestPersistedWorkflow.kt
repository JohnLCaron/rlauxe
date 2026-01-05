package org.cryptobiotic.rlauxe.workflow

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.cli.enterMvrs
import org.cryptobiotic.rlauxe.cli.startTestElectionPolling
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditTest
import org.cryptobiotic.rlauxe.persist.*
import org.cryptobiotic.rlauxe.testdataDir
import kotlin.test.Test
import kotlin.test.fail

class TestPersistedWorkflow {

    @Test
    fun testPersistedSingleClca() {
        // val topdir = kotlin.io.path.createTempDirectory().toString()
        val topdir = "$testdataDir/persist/persistWorkflow/singleClca"
        val auditdir = "$topdir/audit"

        val config = AuditConfig(AuditType.CLCA, seed = 12356667890L, nsimEst=10, contestSampleCutoff = 1000, simFuzzPct = .01,
            persistedWorkflowMode=PersistedWorkflowMode.testPrivateMvrs
        )

        val N = 50000
        val testData = MultiContestTestData(1, 1, N, marginRange=0.03..0.03, ncands=2)

        val contests: List<Contest> = testData.contests
        println("Start testPersistedSingleClca $testData")

        // Synthetic cvrs for testing reflecting the exact contest votes, already has undervotes and phantoms.
        val testMvrs = testData.makeCvrsFromContests()
        val contestsUA = contests.map { ContestWithAssertions(it, isClca = true).addStandardAssertions() }

        val election = CreateElectionFromCvrs(contestsUA, testMvrs, config=config)
        CreateAudit("testPersistedSingleClca", config, election, auditDir = auditdir, clear = true)

        writeUnsortedPrivateMvrs(Publisher(auditdir), testMvrs, config.seed)

        runPersistedAudit(topdir, test=false)
    }

    @Test
    fun testPersistedAuditClca() {
        // val topdir = kotlin.io.path.createTempDirectory().toString()
        val topdir = "$testdataDir/persist/persistWorkflow/clca"
        val auditdir = "$topdir/audit"

        val config = AuditConfig(AuditType.CLCA, seed = 12356667890L, nsimEst=10, contestSampleCutoff = 1000, simFuzzPct = .01,
            persistedWorkflowMode=PersistedWorkflowMode.testPrivateMvrs
        )
        val N = 50000
        val testData = MultiContestTestData(11, 4, N, marginRange=0.03..0.05)

        val contests: List<Contest> = testData.contests
        println("Start testPersistedAuditClca $testData")

        // Synthetic cvrs for testing reflecting the exact contest votes, already has undervotes and phantoms.
        val testMvrs = testData.makeCvrsFromContests()
        val contestsUA = contests.map { ContestWithAssertions(it, isClca = true).addStandardAssertions() }

        val election = CreateElectionFromCvrs(contestsUA, testMvrs, config=config)
        CreateAudit("testPersistedAuditClca",  config, election, auditDir = auditdir,  clear = true)

        // have to write this here, where we know the mvrs
        writeUnsortedPrivateMvrs(Publisher(auditdir), testMvrs, config.seed)

        runPersistedAudit(topdir, test=false)
    }

    @Test
    fun testPersistedAuditPolling() {
            // val topdir = kotlin.io.path.createTempDirectory().toString()
        val topdir = "$testdataDir/persist/persistWorkflow/polling"
        val auditdir = "$topdir/audit"
        val N = 50000

        // fun startTestElectionPolling(
        //    topdir: String,
        //    minMargin: Double,
        //    fuzzMvrs: Double,
        //    pctPhantoms: Double?,
        //    ncards: Int,
        //    ncontests: Int = 11,
        //)
        startTestElectionPolling(topdir, minMargin = .03, fuzzMvrs = .00, pctPhantoms = 0.00, ncards = N, ncontests = 1)

/*
        val config = AuditConfig(AuditType.POLLING, hasStyle=true, seed = 12356667890L, nsimEst=10, simFuzzPct = .01)

        val testData = MultiContestTestDataP(1, 4, N, marginRange=0.03..0.05)

        val contests: List<Contest> = testData.contests
        println("Start testPersistedAuditPolling $testData")

        // Synthetic cvrs for testing reflecting the exact contest votes, already has undervotes and phantoms.
        val testMvrs = testData.makeCvrsFromContests()

        // have to write this here, where we know the mvrs
        writeUnsortedMvrs(Publisher(auditdir), testMvrs, config.seed)

        val contestsUA = contests.map { ContestUnderAudit(it, isClca = true, hasStyle = config.hasStyle).addStandardAssertions() }
        val election = CreateElectionFromCvrs(contestsUA, testMvrs, config=config)
        CreateAuditP("testPersistedAuditPolling", config, election, auditDir = auditdir, clear = true) */

        runPersistedAudit(topdir, test=false)
    }

    @Test
    fun testPersistedOneAudit() {
        // val topdir = kotlin.io.path.createTempDirectory().toString()
        val topdir = "$testdataDir/persist/persistWorkflow/oneaudit"
        val auditdir = "$topdir/audit"

        val config = AuditConfig(
            AuditType.ONEAUDIT, contestSampleCutoff = 20000, nsimEst = 10, simFuzzPct = .01,
            oaConfig = OneAuditConfig(OneAuditStrategyType.generalAdaptive, useFirst = true)
        )

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

        val election = CreateElectionFromCvrs(contestsUA, mvrs, cardPools, config=config)
        CreateAudit("testPersistedAuditPolling", config, election, auditDir = "$topdir/audit", clear = true)

        runPersistedAudit(topdir, test=false)
    }
}

fun runPersistedAudit(topdir: String, test:Boolean) {
    val auditdir = "$topdir/audit"
    val publisher = Publisher(auditdir)
    val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
    writeSortedCardsExternalSort(topdir, publisher, config.seed)
    val writeMvrs = config.persistedWorkflowMode == PersistedWorkflowMode.testPrivateMvrs

    val verifyResults = RunVerifyContests.runVerifyContests(auditdir, null, show = true)
    println()
    print(verifyResults)
    if (verifyResults.hasErrors) fail()

    println("============================================================")
    var done = false
    var lastRound: AuditRound? = null

    while (!done) {
        lastRound = runRound(inputDir = auditdir)
        if (lastRound == null) fail()

        /* TODO!! ??
        val enterResult = enterMvrs(auditdir, publisher.sortedCardsFile())
        if (enterResult is Err) {
            println("enterMvrs failed ${enterResult.error}")
            fail()
        } */

        done = lastRound.auditIsComplete || lastRound.roundIdx > 5
        if (!done && writeMvrs) writeMvrsForRound(publisher, lastRound!!.roundIdx) // RunRlaCreateOneAudit writes the mvrs
    }

    if (lastRound != null) {
        println("nrounds = ${lastRound.roundIdx} nmvrs = ${lastRound.nmvrs} topdir=$topdir")
    } else {
        println("failed in topdir=$topdir")
        fail()
    }
}