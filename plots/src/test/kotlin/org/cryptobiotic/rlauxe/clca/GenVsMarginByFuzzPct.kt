package org.cryptobiotic.rlauxe.clca

import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class GenVsMarginByFuzzPct {

    // Used in docs

    // redo plots/samples/ComparisonFuzzed.html
    @Test
    fun genSamplesVsMarginByFuzzPct() {
        val name = "clcaVsMarginByFuzzPct"
        val dirName = "/home/stormy/temp/workflow/$name"

        val N = 10000
        val actualFuzz = .01
        val nruns = 100  // number of times to run workflow

        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        val stopwatch = Stopwatch()
        val tasks = mutableListOf<RepeatedWorkflowRunner>()
        fuzzPcts.forEach { fuzzPct ->
            margins.forEach { margin ->
                val clcaGenerator = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, actualFuzz,
                    parameters=mapOf("nruns" to nruns, "fuzzPct" to fuzzPct),
                    clcaConfigIn=ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct),
                )
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))
            }
        }
        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        showSampleSizesVsMargin(name, Scale.Linear)
        showSampleSizesVsMargin(name, Scale.Log)
        showSampleSizesVsMargin(name, Scale.Pct)
        showFailuresVsMargin(name, )
    }

    fun showSampleSizesVsMargin(name:String, yscale: Scale) {
        val dirName = "/home/stormy/temp/workflow/$name"

        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showSampleSizesVsMargin(results, "estFuzzPct", yscale=yscale) { categoryFuzzPct(it) }
    }

    fun showFailuresVsMargin(name:String, ) {
        val dirName = "/home/stormy/temp/workflow/$name"

        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showFailuresVsMargin(results, catName="estFuzzPct") { categoryFuzzPct(it) }
    }

}