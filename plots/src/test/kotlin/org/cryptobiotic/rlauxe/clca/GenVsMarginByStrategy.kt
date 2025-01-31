package org.cryptobiotic.rlauxe.clca

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.Scale
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsPlotter
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class GenVsMarginByStrategy {
    val nruns = 10  // number of times to run workflow
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
                mapOf("nruns" to nruns.toDouble(), "strat" to 1.0, "fuzzPct" to fuzzPct),
                clcaConfigIn=ClcaConfig(ClcaStrategyType.oracle, fuzzPct),
                )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator1))

            val clcaGenerator2 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                mapOf("nruns" to nruns.toDouble(), "strat" to 2.0, "fuzzPct" to fuzzPct),
                clcaConfigIn=ClcaConfig(ClcaStrategyType.noerror, fuzzPct),
                )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator2))

            val clcaGenerator3 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                mapOf("nruns" to nruns.toDouble(), "strat" to 3.0, "fuzzPct" to fuzzPct),
                clcaConfigIn=ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct),
                )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator3))

            //// generate mvrs with fuzzPct, but use different errors (twice or half actual) for estimating and auditing
            val clcaGenerator4 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                mapOf("nruns" to nruns.toDouble(), "strat" to 4.0, "fuzzPct" to fuzzPct),
                clcaConfigIn=ClcaConfig(ClcaStrategyType.apriori, fuzzPct, errorRates = ClcaErrorRates.getErrorRates(2, 2*fuzzPct)),
                )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator4))

            val clcaGenerator5 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                mapOf("nruns" to nruns.toDouble(), "strat" to 5.0, "fuzzPct" to fuzzPct),
                clcaConfigIn=ClcaConfig(ClcaStrategyType.apriori, fuzzPct, errorRates = ClcaErrorRates.getErrorRates(2, fuzzPct/2)),
                )
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
        plotter.showSampleSizesVsMargin(results, "strategy", yscale) { categoryStrategy(it) }
    }

    fun showFailuresVsMargin() {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showFailuresVsMargin(results, catName="strategy") { categoryStrategy(it) }
    }
}

fun categoryStrategy(wr: WorkflowResult): String {
    return when (wr.parameters["strat"]) {
        1.0 -> "oracle"
        2.0 -> "noerror"
        3.0 -> "fuzzPct"
        4.0 -> "2*fuzzPct"
        5.0 -> "fuzzPct/2"
        else -> "unknown"
    }
}

fun categoryVersion(wr: WorkflowResult): String {
    return df(wr.parameters["version"]!!)
}

fun categoryFuzzMvrs(wr: WorkflowResult): String {
    return df(100.0*wr.parameters["fuzzMvrs"]!!)
}

fun categoryFuzzDiff(wr: WorkflowResult): String {
    return dfn(100.0*wr.parameters["fuzzDiff"]!!, 2)
}

fun categorySimFuzzVersion(wr: WorkflowResult): String {
    val diff =  dfn(100.0*wr.parameters["simFuzzPct"]!!, 2)
    val ver = dfn(wr.parameters["version"]!!, 0)
    return "ver$ver ${diff}%"
}
