package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import kotlin.test.Test

class TestOneRoundClcaAudit {
    val config = AuditConfig(AuditType.CLCA, nsimEst=10)

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
        val config = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.apriori, pluralityErrorRates=errorRates))

        val contests: List<Contest> = testData.contests
        println("Start testOneRoundClcaWorkflow $testData")

        // Synthetic cvrs for testing reflecting the exact contest votes, plus undervotes and phantoms.
        val testCvrs = testData.makeCvrsFromContests()
        val testMvrs = testCvrs

        val workflow = WorkflowTesterClca(config, contests, emptyList(),
            MvrManagerForTesting(testCvrs, testMvrs, config.seed))
        val contestRounds = workflow.contestsUA().map { ContestRound(it, 1) }
        runClcaSingleRoundAudit(workflow, contestRounds, auditor = ClcaAssertionAuditor())
    }

}