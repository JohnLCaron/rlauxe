package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class EstVsMarginByStrategy {
    val Nc = 50000
    val nruns = 10  // number of times to run workflow
    val nsimEst = 10 // number of simulations

    @Test
    fun estSamplesVsMarginByStrategy() {
        val name = "estVsMarginByStrategy"
        val dirName = "/home/stormy/temp/workflow/$name"

        val margins = listOf(.005, .0075, .01, .015, .02, .03, .04, .05, .06, .07, .08, .09, .10)
        val fuzzMvrs = .01
        val fuzzDiffs = listOf(.01)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        fuzzDiffs.forEach { fuzzDiff ->
            val simFuzzPct = fuzzMvrs+fuzzDiff
            margins.forEach { margin ->
                val auditConfig1 = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
                    clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, simFuzzPct=simFuzzPct))
                val clcaGenerator1 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, 0.0, fuzzMvrs,
                    parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "fuzzPct", "simFuzzPct" to simFuzzPct, "fuzzDiff" to fuzzDiff),
                    auditConfigIn=auditConfig1)
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator1))

                val auditConfig2 = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
                    clcaConfig = ClcaConfig(ClcaStrategyType.default, simFuzzPct=simFuzzPct))
                val clcaGenerator2 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, 0.0, fuzzMvrs,
                    parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "default", "simFuzzPct" to simFuzzPct, "fuzzDiff" to fuzzDiff),
                    auditConfigIn=auditConfig2)
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator2))

                val auditConfig3 = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
                    clcaConfig = ClcaConfig(ClcaStrategyType.oracle))
                val clcaGenerator3 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, phantomPct=0.0, fuzzMvrs,
                    parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "oracle", "simFuzzPct" to simFuzzPct, "fuzzDiff" to fuzzDiff),
                    auditConfigIn=auditConfig3)
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator3))

                val auditConfig4 = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
                    clcaConfig = ClcaConfig(ClcaStrategyType.noerror))
                val clcaGenerator4 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, phantomPct=0.0, fuzzMvrs,
                    parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "noerror", "simFuzzPct" to simFuzzPct, "fuzzDiff" to fuzzDiff),
                    auditConfigIn=auditConfig4)
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator4))
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
        showEstSizesVsMarginStrategy(dirName, name, subtitle, Scale.Linear)
        showEstSizesVsMarginStrategy(dirName, name, subtitle, Scale.Log)
        showEstSizesVsMarginStrategy(dirName, name, subtitle, Scale.Pct)
        showFailuresVsMargin(dirName, name, )
        showNroundsVsMargin(dirName, name, )
    }

    @Test
    fun estSamplesVsMarginByStrategyWithPhantoms() {
        val name = "estVsMarginByStrategyWithPhantoms"
        val dirName = "/home/stormy/temp/workflow/$name"

        val margins = listOf(.0075, .01, .02, .03, .04, .05, .06, .07, .08, .09, .10)
        val phantomPct = .01
        val fuzzMvrs = .01
        val fuzzDiffs = listOf(.01)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        fuzzDiffs.forEach { fuzzDiff ->
            val simFuzzPct = fuzzMvrs+fuzzDiff
            margins.forEach { margin ->
                val auditConfig1 = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
                    clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, simFuzzPct=simFuzzPct))
                val clcaGenerator1 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, phantomPct=phantomPct, fuzzMvrs,
                    parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "fuzzPct", "simFuzzPct" to simFuzzPct, "fuzzDiff" to fuzzDiff),
                    auditConfigIn=auditConfig1)
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator1))

                val auditConfig2 = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
                    clcaConfig = ClcaConfig(ClcaStrategyType.default))
                val clcaGenerator2 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, phantomPct=phantomPct, fuzzMvrs,
                    parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "default", "simFuzzPct" to simFuzzPct, "fuzzDiff" to fuzzDiff),
                    auditConfigIn=auditConfig2)
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator2))

                val auditConfig3 = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
                    clcaConfig = ClcaConfig(ClcaStrategyType.oracle))
                val clcaGenerator3 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, phantomPct=phantomPct, fuzzMvrs,
                    parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "oracle", "simFuzzPct" to simFuzzPct, "fuzzDiff" to fuzzDiff),
                    auditConfigIn=auditConfig3)
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator3))

                val auditConfig4 = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
                    clcaConfig = ClcaConfig(ClcaStrategyType.noerror))
                val clcaGenerator4 = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, phantomPct=phantomPct, fuzzMvrs,
                    parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "noerror", "simFuzzPct" to simFuzzPct, "fuzzDiff" to fuzzDiff),
                    auditConfigIn=auditConfig4)
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator4))
            }
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        val subtitle = "Nc=${Nc} nruns=${nruns} fuzzMvrs=$fuzzMvrs phantomPct=$phantomPct"

        //showEstCostVsVersion(Scale.Linear)
        //showEstCostVsVersion(Scale.Log)
        showEstSizesVsMarginStrategy(dirName, name, subtitle, Scale.Linear)
        showEstSizesVsMarginStrategy(dirName, name, subtitle, Scale.Log)
        showEstSizesVsMarginStrategy(dirName, name, subtitle, Scale.Pct)
        showFailuresVsMargin(dirName, name, )
        showNroundsVsMargin(dirName, name, )
    }

    @Test
    fun regenPlots() {
        val name = "estVsMarginByStrategy"
        val dirName = "/home/stormy/temp/workflow/$name"

        val subtitle = "Nc=${Nc} nruns=${nruns} fuzzDiff=.01"

        //showEstCostVsVersion(Scale.Linear)
        //showEstCostVsVersion(Scale.Log)
        showEstSizesVsMarginStrategy(dirName, name, subtitle, Scale.Linear)
        showEstSizesVsMarginStrategy(dirName, name, subtitle, Scale.Log)
        showEstSizesVsMarginStrategy(dirName, name, subtitle, Scale.Pct)
        showFailuresVsMargin(dirName, name, )
        showNroundsVsMargin(dirName, name, )
    }

    fun showEstSizesVsMarginStrategy(dirName: String, name: String, subtitle: String, yscale: Scale) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showEstSizesVsMarginStrategy(results, subtitle, "fuzzDiff", yscale) { categorySimFuzzCat(it) }
    }

    fun showFailuresVsMargin(dirName: String, name: String, ) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showFailuresVsMargin(results, catName="fuzzDiff") { categorySimFuzzCat(it) }
    }

    fun showNroundsVsMargin(dirName: String, name: String, ) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showNroundsVsMargin(results, catName="fuzzDiff") { categorySimFuzzCat(it) }
    }

}