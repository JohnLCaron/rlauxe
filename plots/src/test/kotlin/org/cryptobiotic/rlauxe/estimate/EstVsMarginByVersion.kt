package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.clca.*
import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.Scale
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsPlotter
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.secureRandom
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.random.Random
import kotlin.test.Test

class EstVsMarginByVersion {
    val Nc = 50000
    val nruns = 10  // number of times to run workflow
    val name = "estVsMarginByVersion"
    val dirName = "/home/stormy/temp/workflow/$name"

    @Test
    fun estSamplesVsMarginByVersion() {
        val margins = listOf(.005, .0075, .01, .015, .02, .03, .04, .05, .06, .07, .08, .09, .10)
        val fuzzMvrs = .01
        val fuzzDiffs = listOf(.005) // listOf(-.01, 0.0, .01)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        fuzzDiffs.forEach { fuzzDiff ->
            val simFuzzPct = fuzzMvrs+fuzzDiff
            val clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, simFuzzPct=simFuzzPct)
            val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, true, quantile=.50, nsimEst = 100, clcaConfig = clcaConfig)

            margins.forEach { margin ->
                val clcaGenerator1 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, 0.0, fuzzMvrs,
                    parameters=mapOf("nruns" to nruns.toDouble(), "version" to 1.0, "simFuzzPct" to simFuzzPct, "fuzzMvrs" to fuzzMvrs),
                    auditConfigIn=auditConfig)
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator1))

                val clcaGenerator2 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, 0.0, fuzzMvrs,
                    parameters=mapOf("nruns" to nruns.toDouble(), "version" to 2.0, "simFuzzPct" to simFuzzPct, "fuzzMvrs" to fuzzMvrs),
                    auditConfigIn=auditConfig.copy(version=2.0))
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator2))
            }

        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        val subtitle = "Nc=${Nc} nruns=${nruns} fuzzMvrs=.01"

        //showEstCostVsVersion(Scale.Linear)
        //showEstCostVsVersion(Scale.Log)
        showEstSizesVsMarginVersion(subtitle, Scale.Linear)
        showEstSizesVsMarginVersion(subtitle, Scale.Log)
        showEstSizesVsMarginVersion(subtitle, Scale.Pct)
        //showFailuresVsMargin()
        showNroundsVsMarginVersion()
    }

    @Test
    fun regenPlots() {
        val subtitle = "Nc=${Nc} nruns=${nruns} fuzzDiff=.01"

        //showEstCostVsVersion(Scale.Linear)
        //showEstCostVsVersion(Scale.Log)
        showEstSizesVsMarginVersion(subtitle, Scale.Linear)
        showEstSizesVsMarginVersion(subtitle, Scale.Log)
        showEstSizesVsMarginVersion(subtitle, Scale.Pct)
        //showFailuresVsMargin()
        showNroundsVsMarginVersion()
    }

    fun showEstCostVsVersion(yscale: Scale) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showEstCostVsVersion(results, "version", yscale) { categoryVersion(it) }
    }

    fun showEstSizesVsMarginVersion(subtitle: String, yscale: Scale) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showEstSizesVsMarginVersion(results, subtitle, "simFuzzPct", yscale) { categorySimFuzzVersion(it) }
    }

    fun showFailuresVsMargin() {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showFailuresVsMargin(results, catName="simFuzzPct") { categorySimFuzzVersion(it) }
    }

    fun showNroundsVsMarginVersion() {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showNroundsVsMargin(results, catName="simFuzzPct") { categorySimFuzzVersion(it) }
    }

    @Test
    fun testVersionTwo() {
        val N = 50000
        val margin = .01
        val mvrFuzz = .005
        val simFuzz = .01

        //repeat(10) {
            val clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, simFuzz)
            val auditConfig = AuditConfig(
                AuditType.CARD_COMPARISON,
                true,
                quantile = .80,
                nsimEst = 100,
                clcaConfig = clcaConfig,
                version=1.0
            )

            val clcaGenerator2 = ClcaWorkflowTaskGenerator(
                N, margin, 0.0, 0.0, mvrsFuzzPct=mvrFuzz,
                parameters = mapOf("nruns" to nruns.toDouble(), "strat" to 3.0, "fuzzPct" to simFuzz),
                auditConfigIn = auditConfig
            )
            val task = clcaGenerator2.generateNewTask()

            val nmvrs = runWorkflow(name, task.workflow, task.testCvrs, quiet = false)
            println("nmvrs = $nmvrs")

            val minAssertion = task.workflow.getContests().first().minClcaAssertion()!!
            val lastRound = minAssertion.roundResults.last()
            println("lastRound = $lastRound")
            println("extra = ${lastRound.estSampleSize - lastRound.samplesNeeded}")
        //}

    }

}