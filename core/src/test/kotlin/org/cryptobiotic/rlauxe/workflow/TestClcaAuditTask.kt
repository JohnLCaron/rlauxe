package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TestH0Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestClcaAuditTask {

    @Test
    fun testClcaContestAuditTaskGenerator() {
        val Nc = 50000
        val margin = .04
        val mvrFuzzPct = .00123
        val config =  Config.from( AuditType.CLCA, nsimEst = 10, fuzzMvrs = mvrFuzzPct, contestSampleCutoff = 10000, )

        val taskGen = ClcaContestAuditTaskGenerator("TestClcaAuditTask",
            Nc, margin, 0.10, 0.001, mvrFuzzPct,
            config = config,
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
        val margin = .05
        val mvrFuzzPct = .00123
        val config =  Config.from( AuditType.CLCA, nsimEst = 10, fuzzMvrs = mvrFuzzPct, contestSampleCutoff = 10000, )

        val taskGen = ClcaSingleRoundAuditTaskGenerator(
            Nc, margin, 0.10, 0.001, mvrFuzzPct,
            config = config,
            parameters = emptyMap(),
        )

        val task = taskGen.generateNewTask()
        val workflowResult = task.run()
        println(workflowResult)
        assertEquals(TestH0Status.StatRejectNull, workflowResult.status)
    }
}