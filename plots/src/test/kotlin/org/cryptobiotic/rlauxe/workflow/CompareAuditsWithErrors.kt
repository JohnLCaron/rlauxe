package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test

class CompareAuditsWithErrors {
    val nruns = 100
    val nsimEst = 10
    val name = "auditsWithErrors"
    val dirName = "/home/stormy/temp/samples/$name"
    val N = 50000
    val margin = .04

    @Test
    fun genAuditWithFuzzPlots() {
        val fuzzPcts = listOf(.00, .001, .0025, .005, .0075, .01, .02, .03, .05)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        fuzzPcts.forEach { fuzzPct ->
            val pollingGenerator = PollingSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, fuzzPct, nsimEst=nsimEst,
                parameters=mapOf("nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct)
            )
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, fuzzPct, nsimEst=nsimEst,
                clcaConfigIn= ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct),
                parameters=mapOf("nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct)
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            val oneauditGenerator = OneAuditSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, cvrPercent = .99, mvrsFuzzPct=fuzzPct,
                parameters=mapOf("nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct),
                auditConfigIn = AuditConfig(
                    AuditType.ONEAUDIT, true, nsimEst = 100,
                    oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.max99)
                )
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator))

            // TODO single round
            val raireGenerator = RaireContestAuditTaskGenerator(
                N, margin, 0.0, 0.0, fuzzPct, nsimEst=nsimEst,
                clcaConfigIn= ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct),
                parameters=mapOf("nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct)
            )
            tasks.add(RepeatedWorkflowRunner(nruns, raireGenerator))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        regenPlots()
    }

    @Test
    fun regenPlots() {
        val subtitle = "margin=${margin} Nc=${N} nruns=${nruns}"
        showSampleSizesVsFuzzPct(dirName, name, subtitle, ScaleType.Linear, catName="auditType", catfld= { compareCategories(it) })
        showSampleSizesVsFuzzPct(dirName, name, subtitle, ScaleType.LogLinear, catName="auditType", catfld= { compareCategories(it) })
        showSampleSizesVsFuzzPct(dirName, name, subtitle, ScaleType.LogLog, catName="auditType", catfld= { compareCategories(it) })
    }
}

fun compareCategories(wr: WorkflowResult): String {
    return when (wr.Dparam("auditType")) {
        1.0 -> "oneaudit99"
        2.0 -> "polling"
        3.0 -> "clca"
        4.0 -> "raire"
        else -> "unknown"
    }
}