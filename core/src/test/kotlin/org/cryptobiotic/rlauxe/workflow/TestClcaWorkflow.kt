package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

class TestClcaWorkflow {
    val auditConfig = AuditConfig(AuditType.CLCA, hasStyles=true, nsimEst=10,
        clcaConfig = ClcaConfig(ClcaStrategyType.previous))

    @Test
    fun testComparisonOneContest() {
        val N = 100000
        val ncontests = 1
        val nbs = 1
        val marginRange= 0.01 .. 0.01
        val underVotePct= 0.02 .. 0.12
        val phantomPct= 0.00
        val phantomRange= phantomPct .. phantomPct
        val testData = MultiContestTestData(ncontests, nbs, N, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomRange)

        // val errorRates = ClcaErrorRates(0.0, phantomPct, 0.0, 0.0, )
        // val auditConfig = auditConfig.copy(clcaConfig = ClcaConfig(ClcaStrategyType.apriori, errorRates=errorRates))

        // high fuzz rate to get multiple rounds
        val finalRound = testComparisonWorkflow(auditConfig, testData, 0.05)
        assertNotNull(finalRound)
        println(finalRound.show())
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

        val finalRound = testComparisonWorkflow(auditConfig, testData)
        assertNotNull(finalRound)
        println(finalRound.show())
        assertEquals(1, finalRound.roundIdx)
    }

    @Test
    fun noErrorsWithPhantoms() {
        val N = 100000
        val ncontests = 42
        val nbs = 1
        val marginRange= 0.01 .. 0.05
        val underVotePct= 0.02 .. 0.22
        val phantomPct= 0.005 .. 0.005
        val testData = MultiContestTestData(ncontests, nbs, N, marginRange =marginRange, underVotePctRange=underVotePct, phantomPctRange=phantomPct)

        val finalRound = testComparisonWorkflow(auditConfig, testData)
        assertNotNull(finalRound)
        println(finalRound.show())
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

        val finalRound = testComparisonWorkflow(auditConfig, testData)
        assertNotNull(finalRound)
        println(finalRound.show())
    }

    @Test
    fun testComparisonWithSimFuzz() {
        val auditConfig = auditConfig.copy(clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, simFuzzPct=0.05))
        val N = 50000
        val testData = MultiContestTestData(11, 1, N)
        val finalRound = testComparisonWorkflow(auditConfig, testData)
        assertNotNull(finalRound)
        println(finalRound.show())
    }

    @Test
    fun testComparisonWithMvrFuzz() {
        val N = 50000
        val testData = MultiContestTestData(11, 1, N)
        val finalRound = testComparisonWorkflow(auditConfig, testData, .05)
        assertNotNull(finalRound)
        println(finalRound.show())
    }

    // @Test oracle disabled
    fun testComparisonOracle() {
        val auditConfig = auditConfig.copy(clcaConfig = ClcaConfig(ClcaStrategyType.oracle))
        val N = 50000
        val testData = MultiContestTestData(11, 4, N)
        val finalRound = testComparisonWorkflow(auditConfig, testData)
        assertNotNull(finalRound)
        println(finalRound.show())
    }

    @Test
    fun testComparisonPhantoms() {
        val auditConfig = auditConfig.copy(clcaConfig = ClcaConfig(ClcaStrategyType.phantoms))
        val N = 50000
        val testData = MultiContestTestData(11, 4, N)
        val finalRound = testComparisonWorkflow(auditConfig, testData)
        assertNotNull(finalRound)
        println(finalRound.show())
    }

    @Test
    fun testComparisonPhantomStrategy() {
        val auditConfig = auditConfig.copy(clcaConfig = ClcaConfig(ClcaStrategyType.phantoms))
        val N = 50000
        val testData = MultiContestTestData(11, 4, N)
        val finalRound = testComparisonWorkflow(auditConfig, testData)
        assertNotNull(finalRound)
        println(finalRound.show())
    }

    fun testComparisonWorkflow(auditConfig: AuditConfig, testData: MultiContestTestData, mvrFuzzPct: Double? = null): AuditRound? {
        val contests: List<Contest> = testData.contests
        println("Start testComparisonWorkflow $testData")
        contests.forEach{ println("  $it")}
        println()

        // Synthetic cvrs for testing reflecting the exact contest votes, plus undervotes and phantoms.
        val testCvrs = testData.makeCvrsFromContests()
        val testMvrs = if (mvrFuzzPct != null)  makeFuzzedCvrsFrom(contests, testCvrs, mvrFuzzPct)
            else if (auditConfig.clcaConfig.strategy != ClcaStrategyType.fuzzPct) testCvrs
            else makeFuzzedCvrsFrom(contests, testCvrs, auditConfig.clcaConfig.simFuzzPct!!) // mvrs fuzz = sim fuzz

        val workflow = ClcaWorkflow(auditConfig, contests, emptyList(), BallotCardsClcaStart(testCvrs, testMvrs, auditConfig.seed))
        return runWorkflow("testComparisonWorkflow", workflow)
    }
}