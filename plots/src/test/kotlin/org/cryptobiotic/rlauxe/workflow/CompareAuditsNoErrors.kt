package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.category
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
                parameters=mapOf("nruns" to nruns)
            )
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaWorkflowTaskGenerator(
                N, margin, 0.0, 0.0, 0.0,
                clcaConfigIn=ClcaConfig(ClcaStrategyType.oracle, 0.0),
                parameters=mapOf("nruns" to nruns)
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            // oneaudit
            cvrPercents.forEach { cvrPercent ->
                val oneauditGenerator = OneAuditWorkflowTaskGenerator(
                    N, margin, 0.0, 0.0, cvrPercent, 0.0,
                    parameters=mapOf("nruns" to nruns)
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


    fun showSampleSizesVsMargin(name: String, dirName: String, yscale: Scale) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showSampleSizesVsMargin(results, "auditType", yscale) {
            when (it.parameters["auditType"]) {
                1.0 -> "oneaudit ${dfn(it.Dparam("cvrPercent"), 3)}"
                2.0 -> "polling"
                3.0 -> "clca"
                else -> "unknown"
            }
        }
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
                clcaConfigIn=ClcaConfig(ClcaStrategyType.oracle, 0.0),
                parameters=mapOf("nruns" to nruns)
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

    @Test
    fun oaNoErrorsPlots() {
        val name = "oaNoErrors"
        val dirName = "/home/stormy/temp/workflow/$name"
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        val fuzzPct = 0.0

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val oneauditGenerator1 = OneAuditWorkflowTaskGenerator(
                N, margin, 0.0, 0.0, 0.50, fuzzPct=fuzzPct,
                mapOf("nruns" to nruns.toDouble(), "cat" to "standard")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator1))
            val oneauditGenerator2 = OneAuditWorkflowTaskGenerator(
                N, margin, 0.0, 0.0, 0.50, fuzzPct=fuzzPct,
                mapOf("nruns" to nruns.toDouble(), "cat" to "max99"),
                auditConfigIn = AuditConfig(AuditType.ONEAUDIT, true, nsimEst = 10,
                    oaConfig = OneAuditConfig(strategy=OneAuditStrategyType.max99, fuzzPct = fuzzPct))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator2))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        val subtitle = "Nc=${N} nruns=${nruns}"
        showSampleSizesVsMarginByStrategy(name, dirName, Scale.Linear)
        showSampleSizesVsMarginByStrategy(name, dirName, Scale.Log)
        showSampleSizesVsMarginByStrategy(name, dirName, Scale.Pct)
        showFailuresVsMarginByStrategy(name, dirName, subtitle=subtitle)
    }

    fun showSampleSizesVsMarginByStrategy(name: String, dirName: String, yscale: Scale) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showSampleSizesVsMargin(results, "strategy", yscale) { category(it) }
    }

    fun showFailuresVsMarginByStrategy(name: String, dirName: String, subtitle: String) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showFailuresVsMargin(results, subtitle, "strategy") { category(it) }
    }

}