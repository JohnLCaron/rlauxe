package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestDataP
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import kotlin.test.Test
import kotlin.test.assertNotNull

class TestClcaAudit {
    val N = 10000

    val config = AuditConfig(
        AuditType.CLCA, nsimEst=10, simFuzzPct=0.05,
    )

    @Test
    fun testClcaOneContest() {
        val ncontests = 1
        val nbs = 1
        val marginRange= 0.01 .. 0.01
        val underVotePct= 0.02 .. 0.12
        val phantomPct= 0.00
        val phantomRange= phantomPct .. phantomPct
        val testData = MultiContestTestDataP(ncontests, nbs, N, marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomRange)

        // high fuzz rate to get multiple rounds
        val finalRound = testClcaWorkflow(config, testData, 0.005)
        assertNotNull(finalRound)
        println(finalRound.show())
    }

    @Test
    fun noErrorsNoPhantoms() {
        val ncontests = 11
        val nbs = 4
        val marginRange= 0.015 .. 0.05
        val underVotePct= 0.02 .. 0.12
        val phantomPct= 0.00 .. 0.00
        val testData = MultiContestTestDataP(ncontests, nbs, N, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomPct)

        val finalRound = testClcaWorkflow(config, testData)
        assertNotNull(finalRound)
        println(finalRound.show())
//         assertEquals(1, finalRound.roundIdx)
    }

    @Test
    fun noErrorsWithPhantoms() {
        val ncontests = 42
        val nbs = 1
        val marginRange= 0.01 .. 0.05
        val underVotePct= 0.02 .. 0.22
        val phantomPct= 0.005 .. 0.005
        val testData = MultiContestTestDataP(ncontests, nbs, N, marginRange =marginRange, underVotePctRange=underVotePct, phantomPctRange=phantomPct)

        val finalRound = testClcaWorkflow(config, testData)
        assertNotNull(finalRound)
        println(finalRound.show())
    }

    @Test
    fun p1ErrorsEqualPhantoms() {
        val ncontests = 42
        val nbs = 11
        val marginRange= 0.01 .. 0.05
        val underVotePct= 0.02 .. 0.22
        val phantomPct= 0.005
        val phantomRange= phantomPct .. phantomPct
        val testData = MultiContestTestDataP(ncontests, nbs, N, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomRange)

        val errorRates = PluralityErrorRates(0.0, phantomPct, 0.0, 0.0, ) // TODO automatic
        val config = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.apriori, pluralityErrorRates=errorRates))

        val finalRound = testClcaWorkflow(config, testData)
        assertNotNull(finalRound)
        println(finalRound.show())
    }

    @Test
    fun testClcaWithSimFuzz() {
        val config = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct))
        val testData = MultiContestTestDataP(11, 1, N,  )
        val finalRound = testClcaWorkflow(config, testData)
        assertNotNull(finalRound)
        println(finalRound.show())
    }

    @Test
    fun testClcaWithMvrFuzz() {
        val testData = MultiContestTestDataP(11, 1, N, )
        val finalRound = testClcaWorkflow(config, testData, .05)
        assertNotNull(finalRound)
        println(finalRound.show())
    }

    // @Test // TODO oracle disabled
    fun testClcaOracle() {
        val config = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.oracle))
        val testData = MultiContestTestDataP(11, 4, N, )
        val finalRound = testClcaWorkflow(config, testData)
        assertNotNull(finalRound)
        println(finalRound.show())
    }

    @Test
    fun testClcaPhantoms() {
        val testData = MultiContestTestDataP(11, 4, N, )
        val finalRound = testClcaWorkflow(config, testData)
        assertNotNull(finalRound)
        println(finalRound.show())
    }

    fun testClcaWorkflow(config: AuditConfig, testData: MultiContestTestDataP, mvrFuzzPct: Double? = null): AuditRound? {
        val contests: List<Contest> = testData.contests
        println("Start testClcaWorkflow $testData")
        contests.forEach{ println("  $it")}
        println()

        // Synthetic cvrs for testing reflecting the exact contest votes, plus undervotes and phantoms.
        val testCvrs = testData.makeCvrsFromContests()
        val testMvrs = if (mvrFuzzPct != null) makeFuzzedCvrsFrom(contests, testCvrs, mvrFuzzPct)
            else if (config.clcaConfig.strategy != ClcaStrategyType.fuzzPct) testCvrs
            else makeFuzzedCvrsFrom(contests, testCvrs, config.simFuzzPct!!) // mvrs fuzz = sim fuzz

        val workflow = WorkflowTesterClca(config, contests, emptyList(),
            MvrManagerForTesting(testCvrs, testMvrs, config.seed))
        return runTestAuditToCompletion("testClcaWorkflow", workflow)
    }
}