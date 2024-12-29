package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.sampling.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TestComparisonWorkflowNoStyles {

    @Test
    fun testComparisonOneContest() {
        val N = 100000
        val ncontests = 1
        val nbs = 1
        val marginRange= 0.01 ..< 0.01
        val underVotePct= 0.02 ..< 0.12
        val phantomPct= 0.005
        val phantomRange= phantomPct ..< phantomPct
        val testData = MultiContestTestData(ncontests, nbs, N, marginRange=marginRange, underVotePct=underVotePct, phantomPct=phantomRange)

        val errorRates = listOf(0.0, phantomPct, 0.0, 0.0, )
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, hasStyles=false, seed=12356667890L, fuzzPct=null, ntrials=10, errorRates=errorRates)
        testComparisonWorkflow(auditConfig, testData)
    }

    @Test
    fun testComparisonTwoContests() {
        val N = 100000
        val ncontests = 2
        val nbs = 1
        val marginRange= 0.01 ..< 0.01
        val underVotePct= 0.02 ..< 0.12
        val phantomPct= 0.005
        val phantomRange= phantomPct ..< phantomPct
        val testData = MultiContestTestData(ncontests, nbs, N, marginRange=marginRange, underVotePct=underVotePct, phantomPct=phantomRange)

        val errorRates = listOf(0.0, phantomPct, 0.0, 0.0, )
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, hasStyles=false, seed=12356667890L, fuzzPct=null, ntrials=10, errorRates=errorRates)
        testComparisonWorkflow(auditConfig, testData)
    }

    @Test
    fun noErrorsNoPhantoms() {
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, hasStyles=false, seed = 12356667890L, fuzzPct = null, ntrials=10)
        val N = 100000
        val ncontests = 11
        val nbs = 4
        val marginRange= 0.015 ..< 0.05
        val underVotePct= 0.02 ..< 0.12
        val phantomPct= 0.00 ..< 0.00
        val testData = MultiContestTestData(ncontests, nbs, N, marginRange=marginRange, underVotePct=underVotePct, phantomPct=phantomPct)
        testComparisonWorkflow(auditConfig, testData)
    }

    @Test
    fun noErrorsWithPhantoms() {
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, hasStyles=false, seed = 12356667890L, fuzzPct = null, ntrials=10)
        val N = 100000
        val ncontests = 42
        val nbs = 11
        val marginRange= 0.01 ..< 0.05
        val underVotePct= 0.02 ..< 0.22
        val phantomPct= 0.005 ..< 0.005
        val testData = MultiContestTestData(ncontests, nbs, N, marginRange=marginRange, underVotePct=underVotePct, phantomPct=phantomPct)
        testComparisonWorkflow(auditConfig, testData)
    }

    @Test
    fun p2ErrorsEqualPhantoms() {
        val N = 100000
        val ncontests = 42
        val nbs = 11
        val marginRange= 0.01 ..< 0.05
        val underVotePct= 0.02 ..< 0.22
        val phantomPct= 0.005
        val phantomRange= phantomPct ..< phantomPct
        val testData = MultiContestTestData(ncontests, nbs, N, marginRange=marginRange, underVotePct=underVotePct, phantomPct=phantomRange)

        val errorRates = listOf(0.0, phantomPct, 0.0, 0.0, )
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, hasStyles=false, seed = 12356667890L, fuzzPct = null, ntrials=10,
                errorRates=errorRates)
        testComparisonWorkflow(auditConfig, testData)
    }

    @Test
    fun testComparisonWithFuzz() {
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, hasStyles=false, seed = 12356667890L, fuzzPct = 0.01, ntrials=10)
        val N = 50000
        val testData = MultiContestTestData(11, 4, N)
        testComparisonWorkflow(auditConfig, testData)
    }

    fun testComparisonWorkflow(auditConfig: AuditConfig, testData: MultiContestTestData) {
        val contests: List<Contest> = testData.contests
        println("Start testComparisonWorkflow $testData")
        contests.forEach{ println("  $it")}
        println()

        // Synthetic cvrs for testing reflecting the exact contest votes, plus undervotes and phantoms.
        val testCvrs = testData.makeCvrsFromContests()
        val testMvrs = if (auditConfig.fuzzPct == null) testCvrs
            // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
            else makeFuzzedCvrsFrom(contests, testCvrs, auditConfig.fuzzPct)

        val workflow = ComparisonWorkflow(auditConfig, contests, emptyList(), testCvrs)
        val nassertions = workflow.contestsUA.sumOf { it.assertions().size }
        runComparisonWorkflow(workflow, testMvrs, nassertions)
    }

}