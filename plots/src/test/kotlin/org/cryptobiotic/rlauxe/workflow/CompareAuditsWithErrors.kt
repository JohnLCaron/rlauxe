package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test

class CompareAuditsWithErrors {
    val nruns = 100
    val nsimEst = 100
    val name = "auditsWithErrors"
    val dirName = "/home/stormy/temp/samples/$name"
    val N = 50000
    val margin = .04

    @Test
    fun genAuditWithFuzzPlots() {
        val cvrPercent = .50
        val fuzzPcts = listOf(.00, .005, .01, .02, .03, .04, .05, .06, .07, .08, .09, .10)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        fuzzPcts.forEach { fuzzPct ->
            val pollingGenerator = PollingWorkflowTaskGenerator(
                N, margin, 0.0, 0.0, fuzzPct, nsimEst=nsimEst,
                parameters=mapOf("nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct)
            )
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaWorkflowTaskGenerator(
                N, margin, 0.0, 0.0, fuzzPct, nsimEst=nsimEst,
                clcaConfigIn=ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct),
                parameters=mapOf("nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct)
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            val oneauditGenerator = OneAuditWorkflowTaskGenerator(
                N, margin, 0.0, 0.0, cvrPercent, fuzzPct, nsimEst=nsimEst,
                parameters=mapOf("nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct)
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator))

            val raireGenerator = RaireWorkflowTaskGenerator(
                N, margin, 0.0, 0.0, fuzzPct, nsimEst=nsimEst,
                clcaConfigIn=ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct),
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
    }
}

fun compareCategories(wr: WorkflowResult): String {
    return when (wr.Dparam("auditType")) {
        1.0 -> "oneaudit"
        2.0 -> "polling"
        3.0 -> "clca"
        4.0 -> "raire"
        else -> "unknown"
    }
}