package org.cryptobiotic.rlauxe.clca

import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class GenVsFuzzByStrategy {

    // Used in docs

    @Test
    fun genSamplesVsFuzzByStrategy() {
        val name = "clcaVsFuzzByStrategy"
        val dirName = "/home/stormy/temp/workflow/$name"

        val N = 50000
        val margin = .04
        val nruns = 100  // number of times to run workflow
        val fuzzPcts = listOf(.00, .005, .01, .02, .03, .04, .05, .06, .07, .08, .09, .10, .11, .12, .13, .14, .15)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<RepeatedWorkflowRunner>()

        fuzzPcts.forEach { fuzzPct ->
            val clcaGenerator1 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                mapOf("nruns" to nruns, "cat" to "oracle", "fuzzPct" to fuzzPct),
                clcaConfigIn = ClcaConfig(ClcaStrategyType.oracle, fuzzPct))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator1))

            val clcaGenerator2 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                mapOf("nruns" to nruns, "cat" to "noerror", "fuzzPct" to fuzzPct),
                clcaConfigIn = ClcaConfig(ClcaStrategyType.noerror, fuzzPct),
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator2))

            val clcaGenerator3 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                mapOf("nruns" to nruns, "cat" to "fuzzPct", "fuzzPct" to fuzzPct),
                clcaConfigIn = ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct),
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator3))

            //// generate mvrs with fuzzPct, but use different errors (twice or half actual) for estimating and auditing
            val clcaGenerator4 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                mapOf("nruns" to nruns, "cat" to "2*fuzzPct", "fuzzPct" to fuzzPct),
                clcaConfigIn = ClcaConfig(ClcaStrategyType.apriori, fuzzPct, errorRates = ClcaErrorRates.getErrorRates(2, 2*fuzzPct)),
                )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator4))

            val clcaGenerator5 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                mapOf("nruns" to nruns, "cat" to "fuzzPct/2", "fuzzPct" to fuzzPct),
                clcaConfigIn = ClcaConfig(ClcaStrategyType.apriori, fuzzPct, errorRates = ClcaErrorRates.getErrorRates(2, fuzzPct/2)),
                )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator5))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        showSampleSizesVsFuzzPct(name, Scale.Linear)
        showSampleSizesVsFuzzPct(name, Scale.Log)
        showSampleSizesVsFuzzPct(name, Scale.Pct)
        showFailuresVsFuzzPct(name, )
        showNroundsVsFuzzPct(name, )
    }

    fun showSampleSizesVsFuzzPct(name:String, yscale: Scale) {
        val dirName = "/home/stormy/temp/workflow/$name"

        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showSampleSizesVsFuzzPct(results, "strategy", yscale=yscale) { category(it) }
    }

    fun showFailuresVsFuzzPct(name:String, ) {
        val dirName = "/home/stormy/temp/workflow/$name"

        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showFailuresVsFuzzPct(results, "strategy") { category(it) }
    }

    fun showNroundsVsFuzzPct(name:String, ) {
        val dirName = "/home/stormy/temp/workflow/$name"

        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showNroundsVsFuzzPct(results, "strategy") { category(it) }
    }
}