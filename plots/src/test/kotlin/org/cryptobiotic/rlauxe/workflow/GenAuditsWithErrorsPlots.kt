package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.ConcurrentTaskRunnerG
import org.cryptobiotic.rlauxe.concur.RepeatedTaskRunner
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsPlotter
import kotlin.test.Test

class GenAuditsWithErrorsPlots {
    private val nruns = 100  // number of times to run workflow

    @Test
    fun genAuditWithErrorsPlots() {
        val N = 50000
        val margin = .04
        val cvrPercent = .50
        val fuzzPcts = listOf(.005, .01, .02, .03, .04, .05, .06, .07, .08, .09, .10, .11, .12)

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        fuzzPcts.forEach { fuzzPct ->
            val pollingGenerator = PollingWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                mapOf("nruns" to nruns.toDouble()))
            tasks.add(RepeatedTaskRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                mapOf("nruns" to nruns.toDouble()))
            tasks.add(RepeatedTaskRunner(nruns, clcaGenerator))

            // oneaudit
            val oneauditflowGenerator = OneAuditWorkflowTaskGenerator(N, margin, 0.0, 0.0, cvrPercent, fuzzPct,
                mapOf("nruns" to nruns.toDouble()))
            tasks.add(RepeatedTaskRunner(nruns, oneauditflowGenerator))
        }

        // run tasks concurrently
        val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunnerG<List<WorkflowResult>>().run(tasks)
        // average the results
        val results: List<WorkflowResult> = rresults.map { avgWorkflowResult(it) }

        val dirName = "/home/stormy/temp/workflow/AuditsWithErrors"
        val filename = "AuditsWithErrors"
        val writer = WorkflowResultsIO("$dirName/${filename}.cvs")
        writer.writeResults(results)

        showSampleSizesVsErrorPct(true)
        showSampleSizesVsErrorPct(false)
        showFailuresVsErrorPct()
        showNroundsVsErrorPct()
    }

    fun showSampleSizesVsErrorPct(useLog: Boolean) {
        val dirName = "/home/stormy/temp/workflow/AuditsWithErrors"
        val filename = "AuditsWithErrors"
        val io = WorkflowResultsIO("$dirName/${filename}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, filename)
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
        val dirName = "/home/stormy/temp/workflow/AuditsWithErrors"
        val filename = "AuditsWithErrors"
        val io = WorkflowResultsIO("$dirName/${filename}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, filename)
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
        val dirName = "/home/stormy/temp/workflow/AuditsWithErrors"
        val filename = "AuditsWithErrors"
        val io = WorkflowResultsIO("$dirName/${filename}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, filename)
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