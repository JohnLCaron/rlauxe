package org.cryptobiotic.rlauxe.unittest

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsPlotter
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.random.Random
import kotlin.test.Test

class TestGenWorkflowTasks {
    val nruns = 10

    @Test
    fun genPollingWorkflowMarginPlots() {
        val N = 50000
        val margins = listOf(.02, .03, .04, .05, .06, .07, .08, .09, .10)

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val workflowGenerator = PollingWorkflowTaskGenerator(N, margin, 0.0, 0.0, 0.0,
                mapOf("nruns" to nruns))
            tasks.add(RepeatedWorkflowRunner(nruns, workflowGenerator))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)

        val dirName = "/home/stormy/temp/testWorkflowTasks"
        val filename = "PollingMarginRepeated"
        val writer = WorkflowResultsIO("$dirName/${filename}.cvs")
        writer.writeResults(results)

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsMargin(results, "category") { "all" }
    }

    @Test
    fun genClcaWorkflowMarginPlots() {
        val N = 50000
        val margins = listOf(.02, .03, .04, .05, .06, .07, .08, .09, .10)

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val workflowGenerator = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, 0.0,
                mapOf("nruns" to nruns),
                clcaConfigIn=ClcaConfig(ClcaStrategyType.oracle, 0.0),
                )
            tasks.add(RepeatedWorkflowRunner(nruns, workflowGenerator))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)

        val dirName = "/home/stormy/temp/testWorkflowTasks"
        val filename = "ClcaMargin"
        val writer = WorkflowResultsIO("$dirName/${filename}.cvs")
        writer.writeResults(results)

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsMargin(results, "category") { "all" }
    }

    @Test
    fun genOneAuditWorkflowMarginPlots() {
        val N = 50000
        val cvrPercents = listOf(.2, .4, .6, .8, .9, .95, .99)
        val margins = listOf(.02, .03, .04, .05, .06, .07, .08, .09, .10)

        val auditConfig = AuditConfig(
            AuditType.ONEAUDIT,
            hasStyles = true,
            quantile = .80,
            nsimEst = 10
        )
        println("N=${N} ntrials=${auditConfig.nsimEst}")

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        cvrPercents.forEach { cvrPercent ->
            margins.forEach { margin ->
                val workflowGenerator = OneAuditWorkflowTaskGenerator(N, margin, 0.0, 0.0, cvrPercent, fuzzPct = 0.0,
                    mapOf("nruns" to nruns.toDouble()))
                tasks.add(RepeatedWorkflowRunner(nruns, workflowGenerator))
            }
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)

        val dirName = "/home/stormy/temp/testWorkflowTasks"
        val filename = "OneAuditMarginRepeated"
        val writer = WorkflowResultsIO("$dirName/${filename}.cvs")
        writer.writeResults(results)

        val plotter = WorkflowResultsPlotter(dirName, filename)
        plotter.showSampleSizesVsMargin(results, "cvrPercent") { df(it.Dparam("cvrPercent")) }
    }
}
