package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.ElectionInfo
import org.cryptobiotic.rlauxe.audit.MvrSource
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.util.ConcurrentTask
import kotlin.test.Test
import kotlin.test.assertEquals

class TestOneAuditTask {

    @Test
    fun problem() {
        // from ExtraVsMarginOneAudit
        // 2026-03-23 16:11:19.947 WARN   runAudit OneAuditWorkflowTaskGenerator margin=0.01 mvrsFuzzPct=0.0 cvrPercent=0.9 11 exceeded maxRounds = 10
        val Nc = 50000
        val margin = .01
        val mvrFuzzPct = .00
        val ntrials = 1
        val nsimTrials = 100
        val cvrPercent = .50

        val election = ElectionInfo.forTest(AuditType.ONEAUDIT, MvrSource.testClcaSimulated) // TODO where do you get the mvrs ??
        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit=.05,)
        val sampleControl = ContestSampleControl(minRecountMargin = 0.0, minMargin=0.0, contestSampleCutoff = null, auditSampleCutoff = null)
        val config = Config(election, creation, round =
            AuditRoundConfig(
                SimulationControl(nsimTrials=nsimTrials),
                sampling = sampleControl,
                ClcaConfig(), null)
        )

        val tasks = mutableListOf<ConcurrentTask<List<WorkflowResult>>>()

        val oneauditGenerator = OneAuditContestAuditTaskGenerator(
            Nc, margin, 0.0, 0.0, cvrPercent,
            configIn = config,
            parameters = emptyMap(),
            mvrsFuzzPct = mvrFuzzPct,
        )

        tasks.add(RepeatedWorkflowRunner(ntrials, oneauditGenerator))
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)

        println("got ${results.size} results")
    }

    @Test
    fun testOneAuditContestAuditTaskGenerator() {
        val Nc = 50000
        val margin = .04
        val mvrFuzzPct = .003
        val config = Config.from(
            AuditType.ONEAUDIT, nsimTrials = 10, simFuzzPct = mvrFuzzPct,
        )
        val taskGen = OneAuditContestAuditTaskGenerator(
            Nc, margin, 0.10, 0.01, 0.99,
            configIn = config,
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
        val config = Config.from(
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
        val config = Config.from(
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

        val auditConfigIn = Config.from(AuditType.ONEAUDIT)
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