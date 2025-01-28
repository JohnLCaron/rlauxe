package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.clca.categoryStrategy
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

class EstVsMarginByStrategy {
    val nruns = 100  // number of times to run workflow
    val name = "estVsMarginByStrategy"
    val dirName = "/home/stormy/temp/workflow/$name"

    // Used in docs

    @Test
    fun estSamplesVsMarginByVersion() {
        val N = 50000
        val margins = listOf(.005, .0075, .01, .015, .02, .03, .04, .05, .06, .07, .08, .09, .10)
        val fuzzMvrs = .01
        val fuzzEst = .02
        val stopwatch = Stopwatch()

        val clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, fuzzEst)
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, true, seed = Random.nextLong(), quantile=.80, ntrials = 10, clcaConfig = clcaConfig)

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        margins.forEach { margin ->
            val clcaGenerator1 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzMvrs,
                parameters=mapOf("nruns" to nruns.toDouble(), "version" to 1.0, "fuzzPct" to fuzzEst),
                auditConfigIn=auditConfig)
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator1))

            val clcaGenerator2 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzMvrs,
                parameters=mapOf("nruns" to nruns.toDouble(), "version" to 1.1, "fuzzPct" to fuzzEst),
                auditConfigIn=auditConfig.copy(version=1.1))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator2))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        showEstCostVsVersion(Scale.Linear)
        showEstCostVsVersion(Scale.Log)
        /* showEstSizesVsMargin(Scale.Linear)
        showEstSizesVsMargin(Scale.Log)
        showEstSizesVsMargin(Scale.Pct)
        showFailuresVsMargin()
        showNroundsVsMargin() */
    }

    @Test
    fun regenPlots() {
        showEstCostVsVersion(Scale.Linear)
        showEstCostVsVersion(Scale.Log)
        /*showEstSizesVsMargin(Scale.Linear)
        showEstSizesVsMargin(Scale.Log)
        showEstSizesVsMargin(Scale.Pct)
        showFailuresVsMargin() */
        showNroundsVsMargin()
    }

    fun showEstCostVsVersion(yscale: Scale) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showEstCostVsVersion(results, "version", yscale) { categoryVersion(it) }
    }

    fun showEstSizesVsMargin(yscale: Scale) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showEstSizesVsMargin(results, "strategy", yscale) { categoryStrategy(it) }
    }

    fun showFailuresVsMargin() {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showFailuresVsMargin(results, "strategy") { categoryStrategy(it) }
    }

    fun showNroundsVsMargin() {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showNroundsVsMargin(results, "strategy") { categoryStrategy(it) }
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
                seed = Random.nextLong(),
                quantile = .50,
                ntrials = 100,
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