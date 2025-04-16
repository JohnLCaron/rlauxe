package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test

class OneAuditNoErrors {
    val name = "OneAuditNoErrors"
    val dirName = "/home/stormy/temp/audits/$name" // you need to make this directory first

    val nruns = 100  // number of times to run workflow
    val nsimEst = 10
    val N = 50000
    val cvrPercent = 0.95

    @Test
    fun oaNoErrorsPlots() {
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        val fuzzPct = 0.0

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val pollingGenerator = PollingSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, 0.0,
                nsimEst = nsimEst,
                parameters=mapOf("nruns" to nruns, "cat" to "poll")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaSingleRoundAuditTaskGenerator(
                Nc = N, margin=margin, underVotePct=0.0, phantomPct=0.0, mvrsFuzzPct=0.0,
                nsimEst = nsimEst,
                clcaConfigIn= ClcaConfig(ClcaStrategyType.noerror, 0.0),
                parameters=mapOf("nruns" to nruns, "cat" to "clca")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            val oneauditGeneratorDefault = OneAuditSingleRoundAuditTaskGenerator(
                Nc=N, margin=margin, underVotePct=0.0, phantomPct=0.0, cvrPercent=cvrPercent, mvrsFuzzPct=fuzzPct,
                parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "default"),
                auditConfigIn = AuditConfig(
                    AuditType.ONEAUDIT, true, nsimEst = 100,
                    oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.reportedMean)
                )
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGeneratorDefault))

            val oneauditGeneratorBet99 = OneAuditSingleRoundAuditTaskGenerator(
                Nc=N, margin=margin, underVotePct=0.0, phantomPct=0.0, cvrPercent=cvrPercent, mvrsFuzzPct=fuzzPct,
                parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "bet99"),
                auditConfigIn = AuditConfig(
                    AuditType.ONEAUDIT, true, nsimEst = 100,
                    oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.bet99)
                )
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGeneratorBet99))

            val oneauditGeneratorEta0Eps = OneAuditSingleRoundAuditTaskGenerator(
                Nc=N, margin=margin, underVotePct=0.0, phantomPct=0.0, cvrPercent=cvrPercent, mvrsFuzzPct=fuzzPct,
                parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "eta0Eps"),
                auditConfigIn = AuditConfig(
                    AuditType.ONEAUDIT, true, nsimEst = 100,
                    oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.eta0Eps)
                )
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGeneratorEta0Eps))
        }
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks, nthreads=40)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        regenPlots()
    }


    @Test
    fun regenPlots() {
        val subtitle = "Nc=${N} nruns=${nruns} cvrPercent=$cvrPercent"
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleType.Linear)
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleType.LogLinear)
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleType.LogLog)
    }

}