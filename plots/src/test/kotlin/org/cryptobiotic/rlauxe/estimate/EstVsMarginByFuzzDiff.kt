package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.clca.categoryFuzzDiff
import org.cryptobiotic.rlauxe.clca.categoryVersion
import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.Scale
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsPlotter
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.random.Random
import kotlin.test.Test

class EstVsMarginByFuzzDiff {
    val Nc = 50000
    val nruns = 100  // number of times to run workflow
    val name = "estVsMarginByFuzzDiff"
    val dirName = "/home/stormy/temp/workflow/$name"

    @Test
    fun estSamplesVsMarginByFuzzDiff() {
        val margins = listOf(.005, .0075, .01, .015, .02, .03, .04, .05, .06, .07, .08, .09, .10)
        val fuzzMvrs = .02
        val fuzzDiffs = listOf(-.01, -.005, 0.0, .005, .01)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        fuzzDiffs.forEach { fuzzDiff ->
            val simFuzzPct = fuzzMvrs+fuzzDiff
            val clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, simFuzzPct)
            val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, true, quantile=.50, nsimEst = 100, clcaConfig = clcaConfig)

            margins.forEach { margin ->
                val clcaGenerator1 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, 0.0, fuzzMvrs,
                    parameters=mapOf("nruns" to nruns.toDouble(), "version" to 1.0, "fuzzDiff" to fuzzDiff, "fuzzMvrs" to fuzzMvrs),
                    auditConfigIn=auditConfig)
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator1))
            }

        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        val subtitle = "Nc=${Nc} nruns=${nruns} fuzzMvrs=.02"

        //showEstCostVsVersion(Scale.Linear)
        //showEstCostVsVersion(Scale.Log)
        showEstSizesVsMargin(subtitle, Scale.Linear)
        showEstSizesVsMargin(subtitle, Scale.Log)
        showEstSizesVsMargin(subtitle, Scale.Pct)
        showFailuresVsMargin()
        showNroundsVsMargin()
    }

    @Test
    fun regenPlots() {
        val subtitle = "Nc=${Nc} nruns=${nruns} fuzzDiff=.01"

        //showEstCostVsVersion(Scale.Linear)
        //showEstCostVsVersion(Scale.Log)
        showEstSizesVsMargin(subtitle, Scale.Linear)
        showEstSizesVsMargin(subtitle, Scale.Log)
        showEstSizesVsMargin(subtitle, Scale.Pct)
        showFailuresVsMargin()
        showNroundsVsMargin()
    }

    fun showEstCostVsVersion(yscale: Scale) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showEstCostVsVersion(results, "version", yscale) { categoryVersion(it) }
    }

    fun showEstSizesVsMargin(subtitle: String, yscale: Scale) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showEstSizesVsMargin(results, subtitle, "fuzzDiff %", yscale) { categoryFuzzDiff(it) }
    }

    fun showFailuresVsMargin() {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showFailuresVsMargin(results, "fuzzDiff %") { categoryFuzzDiff(it) }
    }

    fun showNroundsVsMargin() {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showNroundsVsMargin(results, "fuzzDiff %") { categoryFuzzDiff(it) }
    }

    @Test
    fun testOne() {
        val N = 50000
        val margin = .02
        val fuzzPct = .01

        repeat(10) {
            val clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct)
            val auditConfig = AuditConfig(
                AuditType.CARD_COMPARISON,
                true,
                quantile = .50,
                nsimEst = 100,
                clcaConfig = clcaConfig
            )

            val clcaGenerator2 = ClcaWorkflowTaskGenerator(
                N, margin, 0.0, 0.0, fuzzPct,
                parameters = mapOf("nruns" to nruns.toDouble(), "strat" to 3.0, "fuzzPct" to fuzzPct),
                auditConfigIn = auditConfig
            )
            val task = clcaGenerator2.generateNewTask()

            val nmvrs = runWorkflow(name, task.workflow, task.testCvrs, quiet = false)
            println("nmvrs = $nmvrs")

            val minAssertion = task.workflow.getContests().first().minClcaAssertion()!!
            val lastRound = minAssertion.roundResults.last()
            println("lastRound = $lastRound")
            println("extra = ${lastRound.estSampleSize - lastRound.samplesNeeded}")
        }

    }

}