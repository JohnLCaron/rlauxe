package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedTaskRunner
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsPlotter
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test

class GenAuditsWithErrorsPlots {
    val nruns = 100  // number of times to run workflow
    val name = "AuditsWithErrors"
    val dirName = "/home/stormy/temp/workflow/$name"

    @Test
    fun genAuditWithErrorsPlots() {
        val N = 50000
        val margin = .04
        val cvrPercent = .50
        val fuzzPcts = listOf(.00, .005, .01, .02, .03, .04, .05, .06, .07, .08, .09, .10, .11, .12)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        fuzzPcts.forEach { fuzzPct ->
            val pollingGenerator = PollingWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                mapOf("nruns" to nruns.toDouble()))
            tasks.add(RepeatedTaskRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                mapOf("nruns" to nruns.toDouble()))
            tasks.add(RepeatedTaskRunner(nruns, clcaGenerator))

            // oneaudit
            val oneauditGenerator = OneAuditWorkflowTaskGenerator(N, margin, 0.0, 0.0, cvrPercent, fuzzPct,
                mapOf("nruns" to nruns.toDouble()))
            tasks.add(RepeatedTaskRunner(nruns, oneauditGenerator))
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
        plotter.showSampleSizesVsErrorPct(results, "auditType", useLog=useLog) {
            when (it.parameters["auditType"]) {
                1.0 -> "oneaudit"
                2.0 -> "polling"
                3.0 -> "clca"
                else -> "unknown"
            }
        }
    }

    fun showFailuresVsErrorPct() {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showFailuresVsErrorPct(results, "auditType") {
            when (it.parameters["auditType"]) {
                1.0 -> "oneaudit"
                2.0 -> "polling"
                3.0 -> "clca"
                else -> "unknown"
            }
        }
    }

    fun showNroundsVsErrorPct() {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showNroundsVsErrorPct(results, "auditType") {
            when (it.parameters["auditType"]) {
                1.0 -> "oneaudit"
                2.0 -> "polling"
                3.0 -> "clca"
                else -> "unknown"
            }
        }
    }
}