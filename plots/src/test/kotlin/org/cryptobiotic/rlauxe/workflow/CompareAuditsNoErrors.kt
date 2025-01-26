package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.Scale
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsPlotter
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.dfn
import kotlin.test.Test

class CompareAuditsNoErrors {
    val nruns = 100  // number of times to run workflow
    val N = 10000

    @Test
    fun genAuditsNoErrorsPlots() {
        val name = "AuditsNoErrors"
        val dirName = "/home/stormy/temp/workflow/$name"
        val margins = listOf(.01, .015, .02, .03, .04, .05, .06, .07, .08, .10)

        val cvrPercents = listOf(0.0, 0.5, 1.0)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val pollingGenerator = PollingWorkflowTaskGenerator(
                N, margin, 0.0, 0.0, 0.0,
                mapOf("nruns" to nruns.toDouble())
            )
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaWorkflowTaskGenerator(
                N, margin, 0.0, 0.0, 0.0,
                ClcaConfig(ClcaStrategyType.oracle, 0.0),
                mapOf("nruns" to nruns.toDouble())
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            // oneaudit
            cvrPercents.forEach { cvrPercent ->
                val oneauditGenerator = OneAuditWorkflowTaskGenerator(
                    N, margin, 0.0, 0.0, cvrPercent, 0.0,
                    mapOf("nruns" to nruns.toDouble())
                )
                tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator))
            }
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        showSampleSizesVsMargin(name, dirName, Scale.Linear)
        showSampleSizesVsMargin(name, dirName, Scale.Log)
        showSampleSizesVsMargin(name, dirName, Scale.Pct)
    }

    @Test
    fun clcaNoErrorsPlots() {
        val name = "clcaNoErrors"
        val dirName = "/home/stormy/temp/workflow/$name"
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val clcaGenerator = ClcaWorkflowTaskGenerator(
                N, margin, 0.0, 0.0, 0.0,
                ClcaConfig(ClcaStrategyType.oracle, 0.0),
                mapOf("nruns" to nruns.toDouble())
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        showSampleSizesVsMargin(name, dirName, Scale.Linear)
        showSampleSizesVsMargin(name, dirName, Scale.Log)
        showSampleSizesVsMargin(name, dirName, Scale.Pct)
    }

    fun showSampleSizesVsMargin(name: String, dirName: String, yscale: Scale) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showSampleSizesVsMargin(results, "auditType", yscale) {
            val cvrPercentR = it.parameters["cvrPercent"] ?: 0.0
            when (it.parameters["auditType"]) {
                1.0 -> "oneaudit ${dfn(cvrPercentR, 3)}"
                2.0 -> "polling"
                3.0 -> "clca"
                else -> "unknown"
            }
        }
    }
}