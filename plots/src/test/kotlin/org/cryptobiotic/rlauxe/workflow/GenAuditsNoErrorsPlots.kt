package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.ConcurrentTaskRunnerG
import org.cryptobiotic.rlauxe.concur.RepeatedTaskRunner
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsPlotter
import org.cryptobiotic.rlauxe.util.dfn
import kotlin.test.Test

class GenAuditsNoErrorsPlots {
    private val nruns = 100  // number of times to run workflow

    @Test
    fun genAuditsNoErrorsPlots() {
        val N = 50000
        val margins = listOf(.02, .03, .04, .05, .06, .07, .08, .09, .10)
        val cvrPercents = listOf(0.0, 0.5, 1.0)

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val pollingGenerator = PollingWorkflowTaskGenerator(N, margin, 0.0, 0.0, 0.0,
                mapOf("nruns" to nruns.toDouble()))

            tasks.add(RepeatedTaskRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, 0.0,
                mapOf("nruns" to nruns.toDouble()))

            tasks.add(RepeatedTaskRunner(nruns, clcaGenerator))

            // oneaudit
            cvrPercents.forEach { cvrPercent ->
                val oneauditflowGenerator = OneAuditWorkflowTaskGenerator(N, margin, 0.0, 0.0, cvrPercent, 0.0,
                    mapOf("nruns" to nruns.toDouble()))

                tasks.add(RepeatedTaskRunner(nruns, oneauditflowGenerator))
            }
        }

        // run tasks concurrently
        val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunnerG<List<WorkflowResult>>().run(tasks)
        // average the results
        val results: List<WorkflowResult> = rresults.map { avgWorkflowResult(it) }

        val dirName = "/home/stormy/temp/workflow/AuditsNoErrors"
        val filename = "AuditsNoErrors"
        val writer = WorkflowResultsIO("$dirName/${filename}.cvs")
        writer.writeResults(results)

        plotLinearOrLog(true)
        plotLinearOrLog(false)
    }

    fun plotLinearOrLog(useLog: Boolean) {
        val dirName = "/home/stormy/temp/workflow/AuditsNoErrors"
        val filename = "AuditsNoErrors"
        val io = WorkflowResultsIO("$dirName/${filename}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsMargin(results, "auditType", useLog=useLog) {
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