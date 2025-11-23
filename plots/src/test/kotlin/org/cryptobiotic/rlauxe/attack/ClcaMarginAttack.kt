package org.cryptobiotic.rlauxe.attack

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ClcaStrategyType
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class ClcaMarginAttack {
    val name = "clcaMarginAttack"
    var dirName = "/home/stormy/rla/attack/$name"

    val N = 100000
    val nruns = 100
    val nsimEst = 10
    val fuzzPct = .00
    var phantomPct = .00

    @Test
    fun genSamplesVsMarginByStrategy() {
        val allMargins = listOf(.001, .002, .003, .004, .005, .006, .008, .01)
        val margins = allMargins.filter { it > phantomPct }
        val stopwatch = Stopwatch()
        val config = AuditConfig(AuditType.CLCA, true)
        val extra = 1.001 // to ensure margin goes below 0

        val tasks = mutableListOf<RepeatedWorkflowRunner>()
        margins.forEach { margin ->
            val clcaGenerator2 = ClcaSingleRoundAuditTaskGenerator(N, margin, 0.0, phantomPct, fuzzPct,
                parameters= mapOf("nruns" to nruns, "cat" to "generalAdaptive", "fuzzPct" to fuzzPct),
                p1flips=margin*extra,
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator2))

            val clcaGenerator3 = ClcaSingleRoundAuditTaskGenerator(N, margin, 0.0, phantomPct, fuzzPct,
                parameters= mapOf("nruns" to nruns, "cat" to "fuzzPct", "fuzzPct" to fuzzPct),
                config = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct)),
                p1flips=margin*extra,
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator3))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        regenPlots()
    }

    @Test
    fun regenPlots() {
        val subtitle = "fuzzPct=${fuzzPct} phantomPct=${phantomPct} Nc=${N} nruns=${nruns}"
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.Linear)
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.LogLog)
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.LogLinear)
        showFalsePositivesVsMargin(dirName, name, subtitle)
    }

    fun showSampleSizesVsMargin(dirName: String, name:String, subtitle: String, scaleType: ScaleType) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val data = io.readResults()
        wrsPlot(
            titleS = "$name samples needed",
            subtitleS = subtitle,
            writeFile = "$dirName/${name}${scaleType.name}",
            wrs = data,
            xname = "reportedMargin", xfld = { it.margin},
            yname = "samplesNeeded", yfld = { it.samplesUsed },
            catName = "strategy", catfld = { category(it) },
            scaleType = scaleType
        )
    }

    fun showFalsePositivesVsMargin(dirName: String, name:String, subtitle: String) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val data = io.readResults()
        wrsPlot(
            titleS = "$name successPct",
            subtitleS = subtitle,
            writeFile = "$dirName/${name}FalsePositives",
            wrs = data,
            xname = "margin", xfld = { it.margin },
            yname = "successPct", yfld = { 100.0 - it.failPct },
            catName = "strategy", catfld = { category(it) },
        )
    }

    @Test
    fun runOne() {
        val config = AuditConfig(AuditType.CLCA, true, nsimEst = nsimEst)
        val reportedMargin = .01
        val flip1 = .01
        val taskgen = ClcaSingleRoundAuditTaskGenerator(
            N, margin=reportedMargin, 0.0, 0.0, 0.0,
            parameters = mapOf("nruns" to nruns, "cat" to flip1),
            config = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.oracle)),
            p1flips=flip1,
        )
        val task: ClcaSingleRoundSingleContestAuditTask = taskgen.generateNewTask()
        val result =  task.run()
        println(result)
    }
}