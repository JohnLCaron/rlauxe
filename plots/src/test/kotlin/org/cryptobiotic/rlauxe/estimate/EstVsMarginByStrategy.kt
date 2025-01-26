package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.clca.categoryStrategy
import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.Scale
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsPlotter
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class EstVsMarginByStrategy {
    val nruns = 100  // number of times to run workflow
    val name = "clcaVsMarginByStrategy"
    val dirName = "/home/stormy/temp/workflow/$name"

    // Used in docs

    @Test
    fun genSamplesVsMarginByStrategy() {
        val N = 50000
        val margins = listOf(.005, .0075, .01, .015, .02, .03, .04, .05, .06, .07, .08, .09, .10)
        val fuzzPct = .05
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        margins.forEach { margin ->
            val clcaGenerator1 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                ClcaConfig(ClcaStrategyType.oracle, fuzzPct),
                mapOf("nruns" to nruns.toDouble(), "strat" to 1.0, "fuzzPct" to fuzzPct))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator1))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        showEstSizesVsMargin(Scale.Linear)
    }

    fun showEstSizesVsMargin(yscale: Scale) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showEstSizesVsMargin(results, "strategy", yscale) { categoryStrategy(it) }
    }
}