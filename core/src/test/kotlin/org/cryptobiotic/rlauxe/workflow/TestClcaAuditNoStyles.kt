package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ClcaStrategyType
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestDataP
import kotlin.test.Test

 // TODO add Nbs
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
        val testData = MultiContestTestDataP(ncontests, nbs, N, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomRange)

        val errorRates = PluralityErrorRates(0.0, phantomPct, 0.0, 0.0, )
        val config = AuditConfig(
            AuditType.CLCA, hasStyle=false, seed=12356667890L, nsimEst=10,
            clcaConfig = ClcaConfig(ClcaStrategyType.apriori, pluralityErrorRates=errorRates)
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
        val testData = MultiContestTestDataP(ncontests, nbs, N, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomRange)

        val errorRates = PluralityErrorRates(0.0, phantomPct, 0.0, 0.0, )
        val config = AuditConfig(
            AuditType.CLCA, hasStyle=false, seed=12356667890L, nsimEst=10,
            clcaConfig = ClcaConfig(ClcaStrategyType.apriori, pluralityErrorRates=errorRates)
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
        val testData = MultiContestTestDataP(ncontests, nbs, N, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomPct)
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
        val testData = MultiContestTestDataP(ncontests, nbs, N, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomPct)
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
        val testData = MultiContestTestDataP(ncontests, nbs, N, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomRange)

        val errorRates = PluralityErrorRates(0.0, phantomPct, 0.0, 0.0, )
        val config = AuditConfig(
            AuditType.CLCA, hasStyle=false, nsimEst=10,
            clcaConfig = ClcaConfig(ClcaStrategyType.apriori, pluralityErrorRates=errorRates)
        )
        testClcaWorkflow(config, testData)
    }

    @Test
    fun testClcaWithFuzz() {
        val config = AuditConfig(
            AuditType.CLCA, hasStyle=false, nsimEst=10, simFuzzPct = 0.01,
            clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct = 0.01)
        )

        val N = 50000
        val testData = MultiContestTestDataP(11, 4, N)
        testClcaWorkflow(config, testData)
    }

    fun testClcaWorkflow(config: AuditConfig, testData: MultiContestTestDataP) {
        val contestsToAudit: List<Contest> = testData.contests

        // Synthetic cvrs for testing reflecting the exact contest votes, plus undervotes and phantoms.
        val testCvrs = testData.makeCvrsFromContests()
        val testMvrs = if (config.clcaConfig.strategy != ClcaStrategyType.fuzzPct) testCvrs
            // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
            else makeFuzzedCvrsFrom(contestsToAudit, testCvrs, config.simFuzzPct!!) // mvrs fuzz = sim fuzz

        val workflow = WorkflowTesterClca(config, contestsToAudit, emptyList(),
            MvrManagerForTesting(testCvrs, testMvrs, config.seed))
        runTestAuditToCompletion("TestClcaWorkflowNoStyles", workflow)
    }

    // TODO test, compare hasStyle and noStyle

}