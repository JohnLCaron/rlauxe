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
            AuditType.ONEAUDIT, hasStyle = true, nsimEst = 10, simFuzzPct = mvrFuzzPct,
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
            AuditType.ONEAUDIT, hasStyle = true, simFuzzPct = mvrFuzzPct,
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
            AuditType.ONEAUDIT, hasStyle = true, simFuzzPct = mvrFuzzPct,
        )
        val taskGen = OneAuditSingleRoundWithDilutedMargin(
            Nc,
            margin,
            underVotePct = 0.0,
            phantomPct = 0.005,
            cvrPercent = 0.90,
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
        val mvrFuzzPct = 0.005
        val config = AuditConfig(
            AuditType.ONEAUDIT, hasStyle = true, simFuzzPct = mvrFuzzPct,
        )
        val taskGen = OneAuditSingleRoundAuditTaskGenerator(
            Nc,
            margin,
            underVotePct = 0.0,
            phantomPct = 0.01,
            cvrPercent = 0.90,
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
    fun testOneAuditSingleRoundMultipleContests() {
        val Nc = 50000
        val mvrFuzzPct = 0.00
        val config = AuditConfig(
            AuditType.ONEAUDIT, hasStyle = false, simFuzzPct = mvrFuzzPct,
        )
        //     val N: Int,
        //    val simFuzzPct: Double,
        //    val ncontests: Int,
        //    val nballotStyles: Int,
        val taskGen = OneAuditSingleRoundMultipleContests(
            Nc,
            simFuzzPct = mvrFuzzPct,
            ncontests = 11,
            nballotStyles = 5,
            configIn = config,
            phantomPctRange = 0.0..0.0,
            show = false,
        )

        val task = taskGen.generateNewTask()
        val workflowResult = task.run()
        println(workflowResult)
        // TODO assertEquals(TestH0Status.StatRejectNull, workflowResult.status)
    }
}