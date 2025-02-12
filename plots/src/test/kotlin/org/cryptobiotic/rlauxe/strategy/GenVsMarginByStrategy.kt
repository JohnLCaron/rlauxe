package org.cryptobiotic.rlauxe.strategy

import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class GenVsMarginByStrategy {
    var name = "clcaVsMarginByStrategy1"
    val dirName = "/home/stormy/temp/strategy"

    val N = 50000
    val nruns = 100  // number of times to run workflow
    var fuzzPct = .01

    @Test
    fun genSamplesVsFuzzByStrategy() {
        val margins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<RepeatedWorkflowRunner>()

        val config = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = 100, minMargin = 0.0, samplePctCutoff = 1.0)

        margins.forEach { margin ->
            val clcaGenerator1 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                parameters=mapOf("nruns" to nruns, "cat" to "oracle", "fuzzPct" to fuzzPct),
                auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.oracle, fuzzPct))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator1))

            val clcaGenerator2 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                parameters= mapOf("nruns" to nruns, "cat" to "noerror", "fuzzPct" to fuzzPct),
                auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.noerror, fuzzPct))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator2))

            val clcaGenerator3 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                parameters= mapOf("nruns" to nruns, "cat" to "fuzzPct", "fuzzPct" to fuzzPct),
                auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator3))

            //// generate mvrs with fuzzPct, but use different errors (twice or half actual) for estimating and auditing
            val clcaGenerator4 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                parameters= mapOf("nruns" to nruns, "cat" to "2*fuzzPct", "fuzzPct" to fuzzPct),
                auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.apriori, fuzzPct, errorRates = ClcaErrorRates.getErrorRates(2, 2*fuzzPct)))
                )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator4))

            val clcaGenerator5 = ClcaWorkflowTaskGenerator(N, margin, 0.0, 0.0, fuzzPct,
                parameters= mapOf("nruns" to nruns, "cat" to "fuzzPct/2", "fuzzPct" to fuzzPct),
                auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.apriori, fuzzPct, errorRates = ClcaErrorRates.getErrorRates(2, fuzzPct/2)))
            )
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
        val subtitle = "fuzzPct=${fuzzPct} Nc=${N} nruns=${nruns}"
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