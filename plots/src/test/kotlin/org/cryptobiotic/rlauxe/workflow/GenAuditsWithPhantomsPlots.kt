package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsPlotter
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test

class GenAuditsWithPhantomsPlots {
    val name = "AuditsWithPhantoms"
    val dirName = "/home/stormy/temp/workflow/$name"

    @Test
    fun genAuditWithPhantomsPlots() {
        val nruns = 100  // number of times to run workflow
        val N = 50000
        val margin = .045
        val cvrPercent = .50
        val phantoms = listOf(.00, .005, .01, .02, .03, .04, .05)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        phantoms.forEach { phantom ->
            val pollingGenerator = PollingWorkflowTaskGenerator(N, margin, 0.0, phantom, 0.0,
                mapOf("nruns" to nruns.toDouble(), "phantom" to phantom))
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaWorkflowTaskGenerator(N, margin, 0.0, phantom, 0.0,
                ClcaConfig(ClcaSimulationType.oracle, 0.0),
                mapOf("nruns" to nruns.toDouble(), "phantom" to phantom))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            // oneaudit
            val oneauditGenerator = OneAuditWorkflowTaskGenerator(N, margin, 0.0, phantom, cvrPercent, 0.0,
                mapOf("nruns" to nruns.toDouble(), "phantom" to phantom))
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        showSampleSizesVsPhantomPct(true)
        showSampleSizesVsPhantomPct(false)
        showFailuresVsPhantomPct()
    }


    fun showSampleSizesVsPhantomPct(useLog: Boolean) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showSampleSizesVsPhantomPct(results, "auditType", useLog=useLog) {
            when (it.parameters["auditType"]) {
                1.0 -> "oneaudit"
                2.0 -> "polling"
                3.0 -> "clca"
                else -> "unknown"
            }
        }
    }

    fun showFailuresVsPhantomPct() {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showFailuresVsPhantomPct(results, "auditType") {
            when (it.parameters["auditType"]) {
                1.0 -> "oneaudit"
                2.0 -> "polling"
                3.0 -> "clca"
                else -> "unknown"
            }
        }
    }

}