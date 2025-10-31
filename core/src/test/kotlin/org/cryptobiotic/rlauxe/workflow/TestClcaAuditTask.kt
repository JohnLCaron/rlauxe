package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.TestH0Status
import kotlin.test.Test
import kotlin.test.assertTrue

class TestClcaAuditTask {

    @Test
    fun testClcaContestAuditTaskGenerator() {
        val Nc = 50000
        val margin = .04
        val mvrFuzzPct = .00123
        val auditConfig = AuditConfig(
            AuditType.CLCA, hasStyles = true, nsimEst = 10, contestSampleCutoff = 10000,
            clcaConfig = ClcaConfig(strategy=ClcaStrategyType.phantoms, simFuzzPct = mvrFuzzPct)
        )
        val taskGen = ClcaContestAuditTaskGenerator(
            Nc, margin, 0.10, 0.001, mvrFuzzPct,
            auditConfig = auditConfig,
            parameters = emptyMap(),
            nsimEst = 10,
        )

        val task = taskGen.generateNewTask()
        val workflowResult = task.run()
        println(workflowResult)
        assertTrue(workflowResult.status == TestH0Status.StatRejectNull)
    }

    @Test
    fun testClcaSingleRoundAuditTaskGenerator() {
        val Nc = 50000
        val margin = .02
        val mvrFuzzPct = .00123
        val auditConfig = AuditConfig(
            AuditType.CLCA, hasStyles = true, nsimEst = 10, contestSampleCutoff = 10000,
            clcaConfig = ClcaConfig(strategy=ClcaStrategyType.phantoms, simFuzzPct = mvrFuzzPct)
        )
        val taskGen = ClcaSingleRoundAuditTaskGenerator(
            Nc, margin, 0.10, 0.001, mvrFuzzPct,
            auditConfig = auditConfig,
            parameters = emptyMap(),
            nsimEst = 10,
        )

        val task = taskGen.generateNewTask()
        val workflowResult = task.run()
        println(workflowResult)
        assertTrue(workflowResult.status == TestH0Status.StatRejectNull)
    }
}