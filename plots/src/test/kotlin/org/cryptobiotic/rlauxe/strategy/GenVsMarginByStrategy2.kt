package org.cryptobiotic.rlauxe.strategy

import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class GenVsMarginByStrategy2 {
    var name = "clcaVsMarginByStrategy0"
    val dirName = "/home/stormy/temp/strategy2"

    val N = 50000
    val nruns = 100  // number of times to run workflow
    val fuzzPct = .01
    var phantomPct = .00

    @Test
    fun genSamplesVsMarginByStrategy() {
        val allMargins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .025, .03, .04, .05, .06, .07, .08)
        val margins = allMargins.filter { it > phantomPct }
        val stopwatch = Stopwatch()

        val config = AuditConfig(AuditType.CLCA, true, nsimEst = 100, minMargin = 0.0, samplePctCutoff = 1.0)

        val tasks = mutableListOf<RepeatedWorkflowRunner>()
        margins.forEach { margin ->
            val clcaGenerator1 = ClcaWorkflowTaskGenerator(N, margin, 0.0, phantomPct, fuzzPct,
                parameters=mapOf("nruns" to nruns, "cat" to "oracle", "fuzzPct" to fuzzPct),
                auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.oracle, fuzzPct))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator1))

            val clcaGenerator2 = ClcaWorkflowTaskGenerator(N, margin, 0.0, phantomPct, fuzzPct,
                parameters= mapOf("nruns" to nruns, "cat" to "noerror", "fuzzPct" to fuzzPct),
                auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.noerror, fuzzPct))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator2))

            val clcaGenerator3 = ClcaWorkflowTaskGenerator(N, margin, 0.0, phantomPct, fuzzPct,
                parameters= mapOf("nruns" to nruns, "cat" to "fuzzPct", "fuzzPct" to fuzzPct),
                auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator3))

            val clcaGenerator5 = ClcaWorkflowTaskGenerator(N, margin, 0.0, phantomPct, fuzzPct,
                parameters= mapOf("nruns" to nruns, "cat" to "phantoms", "fuzzPct" to fuzzPct),
                auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.phantoms)))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator5))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        regenPlots()
    }

    @Test
    fun regenPlots() {
        val subtitle = "fuzzPct=${fuzzPct} phantomPct=${phantomPct} Nc=${N} nruns=${nruns}"
        //showSampleSizesVsFuzzPct(dirName, name, subtitle, ScaleType.Linear)
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.LogLog)
        //showSampleSizesVsFuzzPct(dirName, name, subtitle, ScaleType.LogLinear)
        // showFailuresVsFuzzPct(dirName, name, subtitle)
    }

    fun showSampleSizesVsMargin(dirName: String, name:String, subtitle: String, scaleType: ScaleType) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val data = io.readResults()
        wrsPlot(
            titleS = "$name samples needed",
            subtitleS = subtitle,
            writeFile = "$dirName/${name}${scaleType.name}",
            wrs = data,
            xname = "margin", xfld = { it.margin },
            yname = "samplesNeeded", yfld = { it.samplesNeeded },
            catName = "strategy", catfld = { category(it) },
            scaleType = scaleType
        )
    }
}