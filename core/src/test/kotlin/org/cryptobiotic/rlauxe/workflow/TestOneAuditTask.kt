package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.OneAuditConfig
import org.cryptobiotic.rlauxe.audit.OneAuditStrategyType
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
            oaConfig = OneAuditConfig(simFuzzPct = mvrFuzzPct, strategy = OneAuditStrategyType.optimalBet)
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
        assertEquals(TestH0Status.StatRejectNull, workflowResult.status, )
    }

    @Test
    fun testOneAuditSingleRoundAuditTaskGenerator() {
        val Nc = 50000
        val margin = .074
        val mvrFuzzPct = 0.0
        val auditConfig = AuditConfig(
            AuditType.ONEAUDIT, hasStyles = true, nsimEst = 10,
            oaConfig = OneAuditConfig(simFuzzPct = mvrFuzzPct, strategy = OneAuditStrategyType.optimalBet)
        )
        // class OneAuditSingleRoundAuditTaskGenerator(
        //    val Nc: Int, // including undervotes but not phantoms
        //    val margin: Double,
        //    val underVotePct: Double,
        //    val phantomPct: Double,
        //    val cvrPercent: Double,
        //    val mvrsFuzzPct: Double,
        //    val parameters : Map<String, Any>,
        //    val auditConfigIn: AuditConfig? = null,
        //    val nsimEst: Int = 100,
        //    val quiet: Boolean = true,
        //    val skewPct: Double = 0.0,
        //    val p2flips: Double? = null,
        //    val p1flips: Double? = null,
        //): ContestAuditTaskGenerator {
        val taskGen = OneAuditSingleRoundAuditTaskGenerator(
            Nc,
            margin,
            underVotePct = 0.0,
            phantomPct = 0.0,
            cvrPercent = 0.80,
            mvrsFuzzPct = mvrFuzzPct,
            auditConfigIn = auditConfig,
            parameters = emptyMap(),
            nsimEst = 10,
            )

        val task = taskGen.generateNewTask()
        val workflowResult = task.run()
        println(workflowResult)
        assertEquals(TestH0Status.StatRejectNull, workflowResult.status)
    }
}