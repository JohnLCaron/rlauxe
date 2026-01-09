package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ClcaStrategyType
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.io.path.Path
import kotlin.test.Test

class ExtraVsMarginByFuzzDiff {
    val Nc = 50000
    val nruns = 100
    val nsimEst = 100
    val name = "extraVsMarginByFuzzDiff"
    val dirName = "$testdataDir/plots/extra/$name"
    val fuzzMvrs = .02

    // Used in docs

    // TODO this is going off the rails
    // 2026-01-08 10:01:18.170 WARN  0/0/1:  100/100 failures in sampling the max= 875 samples
    // runAudit ClcaWorkflowTaskGenerator 11 exceeded maxRounds = 10
    @Test
    fun estSamplesVsMarginByFuzzDiff() {
        val margins = listOf(.01, .015, .02, .03, .04, .05, .06, .07, .08, .09, .10)
        val fuzzDiffs = listOf(-.01, -.005, 0.0, .005, .01)
        //val margins = listOf(.005)
        // val fuzzDiffs = listOf(-.01)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        fuzzDiffs.forEach { fuzzDiff ->
            val simFuzzPct = fuzzMvrs+fuzzDiff
            val auditConfig = AuditConfig(
                AuditType.CLCA, true, nsimEst = nsimEst,
                clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, simFuzzPct)
            )

            margins.forEach { margin ->
                val clcaGenerator1 = ClcaContestAuditTaskGenerator("'estSamplesVsMarginByFuzzDiff simFuzzPct=$simFuzzPct, margin=$margin'",
                    Nc, margin, 0.0, 0.0, fuzzMvrs,
                    parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "1.0", "fuzzDiff" to fuzzDiff, "fuzzMvrs" to fuzzMvrs),
                    config=auditConfig)
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator1))
            }

        }
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks, nthreads = 1)
        println(stopwatch.took())

        validateOutputDir(Path(dirName))
        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        regenPlots()
    }

    @Test
    fun regenPlots() {
        val subtitle = "Nc=${Nc} nruns=${nruns} fuzzMvrs=$fuzzMvrs"

        showExtraVsMargin(dirName, name, subtitle, ScaleType.LogLinear, "fuzzDiff %") { categoryFuzzDiff(it) }
        showEstSizesVsMargin(subtitle, ScaleTypeOld.Pct)
        showFailuresVsMargin(subtitle)
        showNroundsVsMargin(subtitle)
    }

    fun showExtraVsMargin(dirName: String, name:String, subtitle: String, scaleType: ScaleType,
                                 catName: String, catfld: ((WorkflowResult) -> String) = { category(it) } ) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val data = io.readResults()
        wrsPlot(
            titleS = "$name extra samples",
            subtitleS = subtitle,
            writeFile = "$dirName/${name}${scaleType.name}",
            wrs = data,
            xname = "margin", xfld = { it.margin},
            yname = "extraSamples", yfld = { it.nmvrs - it.samplesUsed },
            catName = catName, catfld = catfld,
            scaleType = scaleType
        )
    }

    fun showEstSizesVsMargin(subtitle: String, yscale: ScaleTypeOld) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotterOld(dirName, name)
        plotter.showEstSizesVsMargin(results, subtitle, "fuzzDiff %", yscale) { categoryFuzzDiff(it) }
    }

    fun showFailuresVsMargin(subtitle: String, ) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotterOld(dirName, name)
        plotter.showFailuresVsMargin(results, subtitle, "fuzzDiff %") { categoryFuzzDiff(it) }
    }

    fun showNroundsVsMargin(subtitle: String, ) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotterOld(dirName, name)
        plotter.showNroundsVsMargin(results, subtitle, "fuzzDiff %") { categoryFuzzDiff(it) }
    }
}