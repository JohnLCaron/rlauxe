package org.cryptobiotic.rlauxe.oneround

import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class GenVsMarginOracle {
    var name = "clcaVsMarginByFlips"
    val dirName = "/home/stormy/temp/oneround/oracle"

    val N = 50000
    val nruns = 100
    val nsimEst = 10

    @Test
    fun genSamplesVsMarginByFlips() {
        val margins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .025, .03, .04, .05, .06, .07, .08)
        val flips = listOf(.000, .001, .002, .005, .0075, .01, .02, .03, .04, .05)

        val stopwatch = Stopwatch()

        val config = AuditConfig(AuditType.CLCA, true, nsimEst = nsimEst)

        val tasks = mutableListOf<RepeatedWorkflowRunner>()
        margins.forEach { margin ->
            flips.forEach { flip ->
                val clcaGenerator1 = ClcaOneRoundAuditTaskGenerator(
                    N, margin, 0.0, 0.0, 0.0,
                    parameters = mapOf("nruns" to nruns, "cat" to flip),
                    auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.oracle)),
                    p1flips=flip,
                )
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator1))
            }
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
        val subtitle = "Nc=${N} nruns=${nruns}"
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.Linear)
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.LogLog)
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.LogLinear)
        showFailuresVsMargin(dirName, name, subtitle)
    }

    fun showSampleSizesVsMargin(dirName: String, name:String, subtitle: String, scaleType: ScaleType) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val data = io.readResults()
        wrsPlot(
            titleS = "$name samples needed Oracle",
            subtitleS = subtitle,
            writeFile = "$dirName/${name}${scaleType.name}",
            wrs = data,
            xname = "true margin", xfld = { it.mvrMargin},
            yname = "samplesNeeded", yfld = { it.samplesNeeded },
            catName = "flipPct", catfld = { category(it) },
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
            xname = "true margin", xfld = { it.mvrMargin},
            yname = "failPct", yfld = { it.failPct },
            catName = "flipPct", catfld = { category(it) },
        )
    }

    @Test
    fun runOne() {
        val config = AuditConfig(AuditType.CLCA, true, nsimEst = nsimEst)
        val reportedMargin = .01
        val flip2 = .01
        val taskgen = ClcaOneRoundAuditTaskGenerator(
            N, margin=reportedMargin, 0.0, 0.0, 0.0,
            parameters = mapOf("nruns" to nruns, "cat" to flip2),
            auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.oracle)),
            p2flips=flip2,
        )
        val task: OneRoundAuditTask = taskgen.generateNewTask()
        val result =  task.run()
        println(result)
    }
}