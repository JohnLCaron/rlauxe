package org.cryptobiotic.rlauxe.comparison

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.Scale
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsPlotter
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test


class GenClcaVsMarginPlots {
    val nruns = 100  // number of times to run workflow
    val name = "clcaMargin"
    val dirName = "/home/stormy/temp/workflow/$name"

    // Used in docs

    @Test
    fun genClcaMarginPlots() {
        val N = 50000
        val margins = listOf(.005, .0075, .01, .015, .02, .03, .04, .05, .06, .07, .08, .09, .10)
        val fuzzPct = .05
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        margins.forEach { margin ->
            val clcaGenerator1 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                ClcaConfig(ClcaSimulationType.oracle, fuzzPct),
                mapOf("nruns" to nruns.toDouble(), "simType" to 1.0, "fuzzPct" to fuzzPct))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator1))

            val clcaGenerator2 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                ClcaConfig(ClcaSimulationType.noerror, fuzzPct),
                mapOf("nruns" to nruns.toDouble(), "simType" to 2.0, "fuzzPct" to fuzzPct))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator2))

            val clcaGenerator3 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                ClcaConfig(ClcaSimulationType.fuzzPct, fuzzPct),
                mapOf("nruns" to nruns.toDouble(), "simType" to 3.0, "fuzzPct" to fuzzPct))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator3))

            //// generate mvrs with fuzzPct, but use different errors (twice or half actual) for estimating and auditing
            val clcaGenerator4 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                ClcaConfig(ClcaSimulationType.apriori, fuzzPct, errorRates = ClcaErrorRates.getErrorRates(2, 2*fuzzPct)),
                mapOf("nruns" to nruns.toDouble(), "simType" to 4.0, "fuzzPct" to fuzzPct))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator4))

            val clcaGenerator5 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                ClcaConfig(ClcaSimulationType.apriori, fuzzPct, errorRates = ClcaErrorRates.getErrorRates(2, fuzzPct/2)),
                mapOf("nruns" to nruns.toDouble(), "simType" to 5.0, "fuzzPct" to fuzzPct))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator5))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        showSampleSizesVsMargin(Scale.Linear)
        showSampleSizesVsMargin(Scale.Log)
        showSampleSizesVsMargin(Scale.Pct)
        showFailuresVsMargin()
    }

    fun showSampleSizesVsMargin(yscale: Scale) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showSampleSizesVsMargin(results, "auditType", yscale) { category(it) }
    }

    fun showFailuresVsMargin() {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showFailuresVsMargin(results, "auditType") { category(it) }
    }

    fun category(wr: WorkflowResult): String {
        return when (wr.parameters["simType"]) {
            1.0 -> "oracle"
            2.0 -> "noerror"
            3.0 -> "fuzzPct"
            4.0 -> "2*fuzzPct"
            5.0 -> "fuzzPct/2"
            else -> "unknown"
        }
    }
}