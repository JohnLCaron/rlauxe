package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.sampling.makeFuzzedCvrsFrom
import kotlin.test.Test

class TestClcaWorkflow {
    val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, hasStyles=true, nsimEst=10)

    @Test
    fun testComparisonOneContest() {
        val N = 100000
        val ncontests = 1
        val nbs = 1
        val marginRange= 0.01 .. 0.01
        val underVotePct= 0.02 .. 0.12
        val phantomPct= 0.005
        val phantomRange= phantomPct .. phantomPct
        val testData = MultiContestTestData(ncontests, nbs, N, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomRange)

        val errorRates = ClcaErrorRates(0.0, phantomPct, 0.0, 0.0, )
        val auditConfig = auditConfig.copy(clcaConfig = ClcaConfig(ClcaStrategyType.apriori, errorRates=errorRates))

        testComparisonWorkflow(auditConfig, testData)
    }

    @Test
    fun noErrorsNoPhantoms() {
        val N = 100000
        val ncontests = 11
        val nbs = 4
        val marginRange= 0.015 .. 0.05
        val underVotePct= 0.02 .. 0.12
        val phantomPct= 0.00 .. 0.00
        val testData = MultiContestTestData(ncontests, nbs, N, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomPct)
        testComparisonWorkflow(auditConfig, testData)
    }

    @Test
    fun noErrorsWithPhantoms() {
        val N = 100000
        val ncontests = 42
        val nbs = 11
        val marginRange= 0.01 .. 0.05
        val underVotePct= 0.02 .. 0.22
        val phantomPct= 0.005 .. 0.005
        val testData = MultiContestTestData(ncontests, nbs, N, marginRange =marginRange, underVotePctRange=underVotePct, phantomPctRange=phantomPct)
        testComparisonWorkflow(auditConfig, testData)
    }

    @Test
    fun p1ErrorsEqualPhantoms() {
        val N = 100000
        val ncontests = 42
        val nbs = 11
        val marginRange= 0.01 .. 0.05
        val underVotePct= 0.02 .. 0.22
        val phantomPct= 0.005
        val phantomRange= phantomPct .. phantomPct
        val testData = MultiContestTestData(ncontests, nbs, N, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomRange)

        val errorRates = ClcaErrorRates(0.0, phantomPct, 0.0, 0.0, ) // TODO automatic
        val auditConfig = auditConfig.copy(clcaConfig = ClcaConfig(ClcaStrategyType.apriori, errorRates=errorRates))

        testComparisonWorkflow(auditConfig, testData)
    }

    @Test
    fun testComparisonWithFuzz() {
        val auditConfig = auditConfig.copy(clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, simFuzzPct=0.01))
        val N = 50000
        val testData = MultiContestTestData(11, 4, N)
        testComparisonWorkflow(auditConfig, testData)
    }

    @Test
    fun testComparisonOracle() {
        val auditConfig = auditConfig.copy(clcaConfig = ClcaConfig(ClcaStrategyType.oracle))
        val N = 50000
        val testData = MultiContestTestData(11, 4, N)
        testComparisonWorkflow(auditConfig, testData)
    }

    @Test
    fun testComparisonPhantoms() {
        val auditConfig = auditConfig.copy(clcaConfig = ClcaConfig(ClcaStrategyType.phantoms))
        val N = 50000
        val testData = MultiContestTestData(11, 4, N)
        testComparisonWorkflow(auditConfig, testData)
    }

    @Test
    fun testComparisonPrevious() {
        val auditConfig = auditConfig.copy(clcaConfig = ClcaConfig(ClcaStrategyType.previous))
        val N = 50000
        val testData = MultiContestTestData(11, 4, N)
        testComparisonWorkflow(auditConfig, testData)
    }

    @Test
    fun testComparisonMixed() {
        val auditConfig = auditConfig.copy(clcaConfig = ClcaConfig(ClcaStrategyType.mixed))
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
        val testMvrs = if (auditConfig.clcaConfig.strategy != ClcaStrategyType.fuzzPct) testCvrs
            // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
            else makeFuzzedCvrsFrom(contests, testCvrs, auditConfig.clcaConfig.simFuzzPct!!) // mvrs fuzz = sim fuzz

        val workflow = ClcaWorkflow(auditConfig, contests, emptyList(), testCvrs)
        runWorkflow("testComparisonWorkflow", workflow, testMvrs)
    }
}