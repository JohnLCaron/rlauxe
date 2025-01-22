package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsPlotter
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test

class GenAuditsWithUndervotesPlots {
    val nruns = 100  // number of times to run workflow
    val name = "AuditsWithUndervotes"
    val dirName = "/home/stormy/temp/workflow/$name"

    @Test
    fun genAuditWithUnderVotesPlots() {
        val N = 50000
        val margin = .04
        val cvrPercent = .50
        val undervotes = listOf(.00, .05, .10, .15, .20, .25, .30, .35, .40, .45, .50)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        undervotes.forEach { undervote ->
            val pollingGenerator = PollingWorkflowTaskGenerator(N, margin, undervote, 0.0, 0.0,
                mapOf("nruns" to nruns.toDouble(), "undervote" to undervote))
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaWorkflowTaskGenerator(N, margin, undervote, 0.0, 0.0,
                ClcaConfig(ClcaSimulationType.oracle, 0.0),
                mapOf("nruns" to nruns.toDouble(), "undervote" to undervote))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            // oneaudit
            val oneauditGenerator = OneAuditWorkflowTaskGenerator(N, margin, undervote, 0.0, cvrPercent, 0.0,
                mapOf("nruns" to nruns.toDouble(), "undervote" to undervote))
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())


        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        showSampleSizesVsUndervotePct(true)
        showSampleSizesVsUndervotePct(false)
    }

    fun showSampleSizesVsUndervotePct(useLog: Boolean) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showSampleSizesVsUndervotePct(results, "auditType", useLog=useLog) {
            when (it.parameters["auditType"]) {
                1.0 -> "oneaudit"
                2.0 -> "polling"
                3.0 -> "clca"
                else -> "unknown"
            }
        }
    }

}