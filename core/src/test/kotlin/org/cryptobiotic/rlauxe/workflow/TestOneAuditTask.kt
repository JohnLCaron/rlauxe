package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.OneAuditConfig
import org.cryptobiotic.rlauxe.audit.OneAuditStrategyType
import org.cryptobiotic.rlauxe.betting.TestH0Status
import kotlin.test.Test
import kotlin.test.assertEquals

class TestOneAuditTask {

    @Test
    fun testOneAuditContestAuditTaskGenerator() {
        val Nc = 50000
        val margin = .04
        val mvrFuzzPct = .003
        val config = AuditConfig(
            AuditType.ONEAUDIT, nsimEst = 10, simFuzzPct = mvrFuzzPct,
        )
        val taskGen = OneAuditContestAuditTaskGenerator(
            Nc, margin, 0.10, 0.01, 0.99,
            auditConfigIn = config,
            parameters = emptyMap(),
            mvrsFuzzPct = mvrFuzzPct,
        )

        val task = taskGen.generateNewTask()
        val workflowResult = task.run()
        println(workflowResult)
        assertEquals(true, workflowResult.status.success, )
    }

    @Test
    fun testOneAuditWithNoExtra() {
        val Nc = 50000
        val margin = .04
        val mvrFuzzPct = 0.0
        val config = AuditConfig(
            AuditType.ONEAUDIT, simFuzzPct = mvrFuzzPct,
        )
        val taskGen = OneAuditSingleRoundWithDilutedMargin(
            Nc,
            margin,
            underVotePct = 0.0,
            phantomPct = 0.0,
            cvrPercent = 0.90,
            extraInPool = 0,
            mvrsFuzzPct = mvrFuzzPct,
            auditConfigIn = config,
            parameters = emptyMap(),
        )

        val task = taskGen.generateNewTask()
        val workflowResult = task.run()
        println(workflowResult)
        assertEquals(TestH0Status.StatRejectNull, workflowResult.status)
    }

    @Test
    fun testOneAuditWithDilutedMargin() {
        val Nc = 50000
        val margin = .04
        val mvrFuzzPct = 0.005
        val config = AuditConfig(
            AuditType.ONEAUDIT, simFuzzPct = mvrFuzzPct,
        )
        val taskGen = OneAuditSingleRoundWithDilutedMargin(
            Nc,
            margin,
            underVotePct = 0.0,
            phantomPct = 0.005,
            cvrPercent = 0.95,
            extraInPool = Nc/20, // 5%
            mvrsFuzzPct = mvrFuzzPct,
            auditConfigIn = config,
            parameters = emptyMap(),
        )

        val task = taskGen.generateNewTask()
        val workflowResult = task.run()
        println(workflowResult)
        assertEquals(TestH0Status.StatRejectNull, workflowResult.status)
    }

    @Test
    fun testOneAuditSingleRoundAuditTaskGenerator() {
        val Nc = 50000
        val margin = .04
        val fuzzPct = 0.0
        val underVotePct = 0.0
        val phantomPct = 0.00
        val cvrPercent = 0.80

        val auditConfigIn = AuditConfig(
            AuditType.ONEAUDIT, true,
            oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.clca)
        )

        val taskGen = OneAuditSingleRoundAuditTaskGenerator(
            Nc,
            margin,
            underVotePct = underVotePct,
            phantomPct = phantomPct,
            cvrPercent = cvrPercent,
            mvrsFuzzPct = fuzzPct,
            auditConfigIn = auditConfigIn,
            parameters = emptyMap(),
        )

        val task = taskGen.generateNewTask()
        val workflowResult = task.run()
        println(workflowResult.show())
        assertEquals(TestH0Status.StatRejectNull, workflowResult.status)
    }
}