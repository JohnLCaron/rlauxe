package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.OneAuditConfig
import org.cryptobiotic.rlauxe.audit.OneAuditStrategyType
import org.cryptobiotic.rlauxe.core.TestH0Status
import kotlin.test.Test
import kotlin.test.assertEquals

class TestOneAuditTask {

    @Test
    fun testOneAuditContestAuditTaskGenerator() {
        val Nc = 50000
        val margin = .04
        val mvrFuzzPct = .0123
        val config = AuditConfig(
            AuditType.ONEAUDIT, hasStyle = true, nsimEst = 10,
            oaConfig = OneAuditConfig(simFuzzPct = mvrFuzzPct, strategy = OneAuditStrategyType.optimalBet)
        )
        val taskGen = OneAuditContestAuditTaskGenerator(
            Nc, margin, 0.10, 0.01, 0.01,
            auditConfigIn = config,
            parameters = emptyMap(),
            nsimEst = 10,
            mvrsFuzzPct = mvrFuzzPct,
        )

        val task = taskGen.generateNewTask()
        val workflowResult = task.run()
        println(workflowResult)
        assertEquals(true, workflowResult.status.success, )
    }

    @Test
    fun testOneAuditSingleRoundAuditTaskGenerator() {
        val Nc = 50000
        val margin = .074
        val mvrFuzzPct = 0.0
        val config = AuditConfig(
            AuditType.ONEAUDIT, hasStyle = true, nsimEst = 10,
            oaConfig = OneAuditConfig(simFuzzPct = mvrFuzzPct, strategy = OneAuditStrategyType.optimalBet)
        )
        val taskGen = OneAuditSingleRoundAuditTaskGenerator(
            Nc,
            margin,
            underVotePct = 0.0,
            phantomPct = 0.0,
            cvrPercent = 0.80,
            mvrsFuzzPct = mvrFuzzPct,
            auditConfigIn = config,
            parameters = emptyMap(),
            nsimEst = 10,
            )

        val task = taskGen.generateNewTask()
        val workflowResult = task.run()
        println(workflowResult)
        assertEquals(TestH0Status.StatRejectNull, workflowResult.status)
    }
}