package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import kotlin.test.Test

class TestOneRoundClcaAudit {
    val config = AuditConfig(AuditType.CLCA, hasStyle=true, nsimEst=10)

    @Test
    fun testClcaSingleRoundAudit() {
        val N = 100000
        val ncontests = 1
        val nbs = 1
        val marginRange= 0.01 .. 0.01
        val underVotePct= 0.02 .. 0.12
        val phantomPct= 0.005
        val phantomRange= phantomPct .. phantomPct
        val testData = MultiContestTestData(ncontests, nbs, N, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomRange)

        val errorRates = PluralityErrorRates(0.0, phantomPct, 0.0, 0.0, )
        val config = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.apriori, errorRates=errorRates))

        val contests: List<Contest> = testData.contests
        println("Start testOneRoundClcaWorkflow $testData")

        // Synthetic cvrs for testing reflecting the exact contest votes, plus undervotes and phantoms.
        val testCvrs = testData.makeCvrsFromContests()
        val testMvrs = if (config.clcaConfig.strategy != ClcaStrategyType.fuzzPct) testCvrs
            // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
            else makeFuzzedCvrsFrom(contests, testCvrs, config.clcaConfig.simFuzzPct!!) // mvrs fuzz = sim fuzz

        val workflow = WorkflowTesterClca(config, contests, emptyList(),
            MvrManagerClcaForTesting(testCvrs, testMvrs, config.seed))
        val contestRounds = workflow.contestsUA().map { ContestRound(it, 1) }
        runClcaSingleRoundAudit(workflow, contestRounds, auditor = ClcaAssertionAuditor())
    }

}