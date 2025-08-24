package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.OneAuditConfig
import org.cryptobiotic.rlauxe.audit.PollingConfig
import org.cryptobiotic.rlauxe.core.TestH0Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestOneAuditTask {

    @Test
    fun testOneAuditContestAuditTaskGenerator() {
        val Nc = 50000
        val margin = .04
        val mvrFuzzPct = .0123
        val auditConfig = AuditConfig(
            AuditType.ONEAUDIT, hasStyles = true, nsimEst = 10,
            oaConfig = OneAuditConfig(simFuzzPct = mvrFuzzPct)
        )
        val taskGen = OneAuditContestAuditTaskGenerator(
            Nc, margin, 0.10, 0.01, 0.01,
            auditConfigIn = auditConfig,
            parameters = emptyMap(),
            nsimEst = 10,
            mvrsFuzzPct = mvrFuzzPct,
        )

        val task = taskGen.generateNewTask()
        val workflowResult = task.run()
        println(workflowResult)
        assertEquals(workflowResult.status, TestH0Status.StatRejectNull)
    }

    @Test
    fun testPollingSingleRoundAuditTaskGenerator() {
        val Nc = 50000
        val margin = .04
        val mvrFuzzPct = .0123
        val auditConfig = AuditConfig(
            AuditType.ONEAUDIT, hasStyles = true, nsimEst = 10,
            oaConfig = OneAuditConfig(simFuzzPct = mvrFuzzPct)
        )
        val taskGen = OneAuditSingleRoundAuditTaskGenerator(
            Nc, margin, 0.25, 0.001, 0.02,
            auditConfigIn = auditConfig,
            parameters = emptyMap(),
            nsimEst = 10,
            mvrsFuzzPct = mvrFuzzPct,
        )

        val task = taskGen.generateNewTask()
        val workflowResult = task.run()
        println(workflowResult)
        assertTrue(workflowResult.status == TestH0Status.StatRejectNull)
    }
}