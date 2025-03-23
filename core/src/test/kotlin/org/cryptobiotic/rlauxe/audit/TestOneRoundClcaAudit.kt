package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.workflow.MvrManagerClcaForTesting
import org.cryptobiotic.rlauxe.workflow.runClcaSingleRoundAudit
import kotlin.test.Test

class TestOneRoundClcaAudit {
    val auditConfig = AuditConfig(AuditType.CLCA, hasStyles=true, nsimEst=10)

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

        val errorRates = ClcaErrorRates(0.0, phantomPct, 0.0, 0.0, )
        val auditConfig = auditConfig.copy(clcaConfig = ClcaConfig(ClcaStrategyType.apriori, errorRates=errorRates))

        val contests: List<Contest> = testData.contests
        println("Start testOneRoundClcaWorkflow $testData")

        // Synthetic cvrs for testing reflecting the exact contest votes, plus undervotes and phantoms.
        val testCvrs = testData.makeCvrsFromContests()
        val testMvrs = if (auditConfig.clcaConfig.strategy != ClcaStrategyType.fuzzPct) testCvrs
            // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
            else makeFuzzedCvrsFrom(contests, testCvrs, auditConfig.clcaConfig.simFuzzPct!!) // mvrs fuzz = sim fuzz

        val workflow = ClcaAudit(auditConfig, contests, emptyList(),
            MvrManagerClcaForTesting(testCvrs, testMvrs, auditConfig.seed), testCvrs)
        val contestRounds = workflow.contestsUA().map { ContestRound(it, 1) }
        runClcaSingleRoundAudit(workflow, contestRounds, auditor = AuditClcaAssertion())
    }

}