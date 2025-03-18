package org.cryptobiotic.rlauxe.attack

import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class GenVsMarginWithPhantoms {
    var name = "marginWithPhantoms0"
    var dirName = "/home/stormy/temp/attack/marginWithPhantoms0"
    var phantomPct = .00

    val N = 100000
    val nruns = 100
    val nsimEst = 10
    val fuzzPct = .01

    @Test
    fun genSamplesVsMarginWithPhantoms() {
        val allMargins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .025, .03, .04, .05, .06, .07, .08, .10)
        val margins = allMargins.filter { it > phantomPct }
        val stopwatch = Stopwatch()

        val config = AuditConfig(AuditType.CLCA, true, nsimEst = nsimEst)

        val tasks = mutableListOf<RepeatedWorkflowRunner>()
        allMargins.forEach { margin ->
            /* val clcaGenerator1 = ClcaSingleRoundAuditTaskGenerator(N, margin, 0.0, phantomPct, fuzzPct,
                parameters=mapOf("nruns" to nruns, "cat" to "oracle", "fuzzPct" to fuzzPct),
                auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.oracle, fuzzPct))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator1)) */

            val clcaGenerator2 = ClcaSingleRoundAuditTaskGenerator(N, margin, 0.0, phantomPct, fuzzPct,
                parameters= mapOf("nruns" to nruns, "cat" to "noerror", "fuzzPct" to fuzzPct),
                auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.noerror, fuzzPct))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator2))

            val clcaGenerator3 = ClcaSingleRoundAuditTaskGenerator(N, margin, 0.0, phantomPct, fuzzPct,
                parameters= mapOf("nruns" to nruns, "cat" to "fuzzPct", "fuzzPct" to fuzzPct),
                auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator3))

            /* val clcaGenerator4 = ClcaOneRoundAuditTaskGenerator(N, margin, 0.0, phantomPct, fuzzPct,
                parameters= mapOf("nruns" to nruns, "cat" to "previous", "fuzzPct" to fuzzPct),
                auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.previous)))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator4)) */

            val clcaGenerator5 = ClcaSingleRoundAuditTaskGenerator(N, margin, 0.0, phantomPct, fuzzPct,
                parameters= mapOf("nruns" to nruns, "cat" to "phantoms", "fuzzPct" to fuzzPct),
                auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.phantoms)))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator5))

            /* val clcaGenerator6 = ClcaOneRoundAuditTaskGenerator(N, margin, 0.0, phantomPct, fuzzPct,
                parameters= mapOf("nruns" to nruns, "cat" to "mixed", "fuzzPct" to fuzzPct),
                auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.mixed)))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator6)) */
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
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.Linear)
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.LogLog)
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.LogLinear)
        showFailuresVsMargin(dirName, name, subtitle)
    }

    fun showSampleSizesVsMargin(dirName: String, name:String, subtitle: String, scaleType: ScaleType) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val data = io.readResults()
        wrsPlot(
            titleS = "$name samples needed",
            subtitleS = subtitle,
            writeFile = "$dirName/${name}${scaleType.name}",
            wrs = data,
            xname = "true margin", xfld = { it.mvrMargin},
            yname = "samplesNeeded", yfld = { it.samplesUsed },
            catName = "strategy", catfld = { category(it) },
            scaleType = scaleType
        )
    }

    fun showFailuresVsMargin(dirName: String, name:String, subtitle: String) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val data = io.readResults()
        wrsPlot(
            titleS = "$name failurePct",
            subtitleS = subtitle,
            writeFile = "$dirName/${name}Failure",
            wrs = data,
            xname = "true margin", xfld = { it.mvrMargin },
            yname = "failPct", yfld = { it.failPct },
            catName = "strategy", catfld = { category(it) },
        )
    }
}