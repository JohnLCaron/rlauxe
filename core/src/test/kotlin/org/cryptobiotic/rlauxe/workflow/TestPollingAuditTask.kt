package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.core.TestH0Status
import kotlin.test.Test
import kotlin.test.assertEquals

class TestPollingAuditTask {

    @Test
    fun testPollingContestAuditTaskGenerator() {
        val Nc = 50000
        val margin = .04
        val mvrFuzzPct = .0123
        val auditConfig = AuditConfig(
            AuditType.POLLING, nsimEst = 1, simFuzzPct = mvrFuzzPct
        )
        val taskGen = PollingContestAuditTaskGenerator(
            Nc, margin, 0.0, 0.0, 0.0,
            mapOf("cat" to "pollingWithStyles"),
            auditConfig = auditConfig,
            Npop = Nc
        )

        val task = taskGen.generateNewTask()
        val workflowResult = task.run()
        println(workflowResult)
        assertEquals(TestH0Status.StatRejectNull, workflowResult.status)
    }

    @Test
    fun testPollingSingleRoundAuditTaskGenerator() {
        val Nc = 50000
        val margin = .08
        val mvrFuzzPct = .0123
        val auditConfig = AuditConfig(
            AuditType.POLLING, nsimEst = 10, contestSampleCutoff = 10000, simFuzzPct = mvrFuzzPct
        )
        val taskGen = PollingSingleRoundAuditTaskGenerator(
            Nc, margin, 0.0, 0.0, 0.0,
            mapOf("cat" to "pollingWithStyles"),
            auditConfig = auditConfig,
            Npop = Nc
        )

        val task = taskGen.generateNewTask()
        val workflowResult = task.run()
        println(workflowResult)
        assertEquals(TestH0Status.StatRejectNull, workflowResult.status)
    }
}