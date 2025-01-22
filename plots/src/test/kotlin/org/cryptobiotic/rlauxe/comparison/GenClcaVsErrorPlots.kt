package org.cryptobiotic.rlauxe.comparison

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsPlotter
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class GenClcaVsErrorPlots {
    val nruns = 200  // number of times to run workflow
    val name = "clca"
    val dirName = "/home/stormy/temp/workflow/$name"

    @Test
    fun genClcaErrorPlots() {
        val N = 50000
        val margin = .04
        val fuzzPcts = listOf(.00, .005, .01, .02, .03, .04, .05, .06, .07, .08, .09, .10, .11, .12, .13, .14, .15)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        fuzzPcts.forEach { fuzzPct ->
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

        showSampleSizesVsErrorPct(true)
        showSampleSizesVsErrorPct(false)
        showFailuresVsErrorPct()
        showNroundsVsErrorPct()
    }

    fun showSampleSizesVsErrorPct(useLog: Boolean) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showSampleSizesVsErrorPct(results, "auditType", useLog=useLog) { category(it) }
    }

    fun showFailuresVsErrorPct() {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showFailuresVsErrorPct(results, "auditType") { category(it) }
    }

    fun showNroundsVsErrorPct() {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showNroundsVsErrorPct(results, "auditType") { category(it) }
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