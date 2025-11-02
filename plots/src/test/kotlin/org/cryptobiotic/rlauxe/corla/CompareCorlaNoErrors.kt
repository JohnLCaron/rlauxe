package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ClcaStrategyType
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class CompareCorlaNoErrors {
    val nruns = 10
    val nsimEst = 10
    val name = "corlaNoErrors2"
    val dirName = "/home/stormy/rla/corla/$name"
    val N = 100000
    val risk = .03

    @Test
    fun corlaComparePlotsNoError() {
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .10)
        val config = AuditConfig(AuditType.CLCA, true, nsimEst = nsimEst, riskLimit = risk)

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            var fuzzPct = 0.0
            var phantomPct = 0.0

            val corla0 = CorlaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, phantomPct, fuzzPct,
                parameters = mapOf("nruns" to nruns, "cat" to "corla"),
                auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.noerror))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, corla0))

            val clca0 = ClcaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, phantomPct, fuzzPct,
                parameters = mapOf("nruns" to nruns, "cat" to "rlauxe"),
                config = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.phantoms))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clca0))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks, nthreads=1)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        regenPlots()
    }

    @Test
    fun regenPlots() {
        val subtitle = "Nc=${N} nruns=${nruns} riskLimit=$risk"
        showSampleSizes(name, dirName, subtitle, ScaleType.Linear)
        showSampleSizes(name, dirName, subtitle, ScaleType.LogLinear)
        showSampleSizes(name, dirName, subtitle, ScaleType.LogLog)
    }
}

fun showSampleSizes(name:String, dirName: String, subtitle: String, scaleType: ScaleType) {
    val io = WorkflowResultsIO("$dirName/${name}.csv")
    val data = io.readResults()
    wrsPlot(
        titleS = "Corla vs Rlauxe",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}${scaleType.name}",
        wrs = data,
        xname = "true margin", xfld = { it.mvrMargin},
        yname = "samplesNeeded", yfld = { it.samplesUsed },
        catName = "type", catfld = { category(it) },
        scaleType = scaleType,
    )
}