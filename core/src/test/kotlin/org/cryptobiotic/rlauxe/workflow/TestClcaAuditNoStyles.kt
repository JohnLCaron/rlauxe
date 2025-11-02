package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ClcaStrategyType
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import kotlin.test.Test

class TestClcaAuditNoStyles {
    val N = 10000

    @Test
    fun testClcaOneContest() {
        val ncontests = 1
        val nbs = 1
        val marginRange= 0.01 .. 0.01
        val underVotePct= 0.02 .. 0.12
        val phantomPct= 0.005
        val phantomRange= phantomPct .. phantomPct
        val testData = MultiContestTestData(ncontests, nbs, N, hasStyle=true, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomRange)

        val errorRates = ClcaErrorRates(0.0, phantomPct, 0.0, 0.0, )
        val config = AuditConfig(
            AuditType.CLCA, hasStyle=false, seed=12356667890L, nsimEst=10,
            clcaConfig = ClcaConfig(ClcaStrategyType.apriori, errorRates=errorRates)
        )

        testClcaWorkflow(config, testData)
    }

    @Test
    fun testClcaTwoContests() {
        val ncontests = 2
        val nbs = 1
        val marginRange= 0.01 .. 0.01
        val underVotePct= 0.02 .. 0.12
        val phantomPct= 0.005
        val phantomRange= phantomPct .. phantomPct
        val testData = MultiContestTestData(ncontests, nbs, N, hasStyle=true, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomRange)

        val errorRates = ClcaErrorRates(0.0, phantomPct, 0.0, 0.0, )
        val config = AuditConfig(
            AuditType.CLCA, hasStyle=false, seed=12356667890L, nsimEst=10,
            clcaConfig = ClcaConfig(ClcaStrategyType.apriori, errorRates=errorRates)
        )

        testClcaWorkflow(config, testData)
    }

    // @Test
    fun noErrorsNoPhantomsRepeat() {
        repeat(100) {
            noErrorsNoPhantoms()
        }
    }

    @Test
    fun noErrorsNoPhantoms() {
        val config = AuditConfig(AuditType.CLCA, hasStyle=false, nsimEst=10)
        val ncontests = 11
        val nbs = 4
        val marginRange= 0.015 .. 0.05
        val underVotePct= 0.02 .. 0.12
        val phantomPct= 0.00 .. 0.00
        val testData = MultiContestTestData(ncontests, nbs, N, config.hasStyle, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomPct)
        testClcaWorkflow(config, testData)
    }

    @Test
    fun noErrorsWithPhantoms() {
        val config = AuditConfig(AuditType.CLCA, hasStyle=false, nsimEst=10)
        val ncontests = 42
        val nbs = 11
        val marginRange= 0.01 .. 0.05
        val underVotePct= 0.02 .. 0.22
        val phantomPct= 0.005 .. 0.005
        val testData = MultiContestTestData(ncontests, nbs, N, config.hasStyle, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomPct)
        testClcaWorkflow(config, testData)
    }

    @Test
    fun p2ErrorsEqualPhantoms() {
        val ncontests = 42
        val nbs = 11
        val marginRange= 0.01 .. 0.05
        val underVotePct= 0.02 .. 0.22
        val phantomPct= 0.005
        val phantomRange= phantomPct .. phantomPct
        val testData = MultiContestTestData(ncontests, nbs, N, hasStyle=true, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomRange)

        val errorRates = ClcaErrorRates(0.0, phantomPct, 0.0, 0.0, )
        val config = AuditConfig(
            AuditType.CLCA, hasStyle=true, nsimEst=10,
            clcaConfig = ClcaConfig(ClcaStrategyType.apriori, errorRates=errorRates)
        )
        testClcaWorkflow(config, testData)
    }

    @Test
    fun testClcaWithFuzz() {
        val config = AuditConfig(
            AuditType.CLCA, hasStyle=true, nsimEst=10,
            clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, simFuzzPct = 0.01)
        )

        val N = 50000
        val testData = MultiContestTestData(11, 4, N, config.hasStyle)
        testClcaWorkflow(config, testData)
    }

    fun testClcaWorkflow(config: AuditConfig, testData: MultiContestTestData) {
        val contests: List<Contest> = testData.contests

        // Synthetic cvrs for testing reflecting the exact contest votes, plus undervotes and phantoms.
        val testCvrs = testData.makeCvrsFromContests()
        val testMvrs = if (config.clcaConfig.strategy != ClcaStrategyType.fuzzPct) testCvrs
            // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
            else makeFuzzedCvrsFrom(contests, testCvrs, config.clcaConfig.simFuzzPct!!) // mvrs fuzz = sim fuzz

        val workflow = WorkflowTesterClca(config, contests, emptyList(),
            MvrManagerClcaForTesting(testCvrs, testMvrs, config.seed))
        runAudit("TestClcaWorkflowNoStyles", workflow)
    }

}