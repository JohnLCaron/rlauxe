package org.cryptobiotic.rlauxe.cobra

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ClcaStrategyType
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class GenCobraPlots {
    val dirName = "/home/stormy/rla/corba"
    val name = "corba"

    val N = 10000
    val nruns = 1000
    val nsimEst = 10
    val p2prior = .001

    @Test
    fun genAdaptiveComparison() {
        val p2s = listOf(.001, .002, .005, .0075, .01, .02, .03, .04, .05)
        val reportedMeans = listOf(0.501, 0.502, 0.503, 0.504, 0.505, 0.506, 0.5075, 0.508, 0.51, 0.52, 0.53, 0.54, 0.55, 0.56, 0.58, 0.6,)

        val config = AuditConfig(
            AuditType.CLCA, true, nsimEst = nsimEst,
            clcaConfig = ClcaConfig(ClcaStrategyType.noerror)
        )

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        reportedMeans.forEach { reportedMean ->
            p2s.forEach { p2 ->
                     val cobra = CobraSingleRoundAuditTaskGenerator(
                    N, reportedMean=reportedMean, p2oracle = p2, p2prior = p2prior,
                    parameters = mapOf("nruns" to nruns, "cat" to p2),
                    auditConfig = config,
                )
                tasks.add(RepeatedWorkflowRunner(nruns, cobra))
            }
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)

        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        regenPlots()
    }

    @Test
    fun regenPlots() {
        val subtitle = "Nc=${N} nruns=${nruns} est p2 rate=$p2prior"
        samplesVsTheta(name, dirName, name, subtitle, ScaleType.LogLinear) { true }
        failureVsTheta(name, dirName, "${name}Failures", subtitle, ScaleType.Linear) { true }
        failureVsTheta(name, dirName, "${name}FailuresUnder", subtitle, ScaleType.Linear) { it.mvrMargin <= 0.0 }
        failureVsTheta(name, dirName, "${name}FailuresOver", subtitle, ScaleType.Linear) { it.mvrMargin in 0.0..0.025 }
        //showFailuresVsMargin(name, dirName, subtitle)
    }

    fun samplesVsTheta(name:String, dirName: String, writeto : String, subtitle: String, scaleType: ScaleType, filter: (WorkflowResult) -> Boolean) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val data = io.readResults().filter(filter)
        if (data.isEmpty()) return
        wrsPlot(
            titleS = "Corba: true > estimated p2 rate",
            subtitleS = subtitle,
            writeFile = "$dirName/${writeto}",
            wrs = data,
            xname = "true margin", xfld = { it.mvrMargin },
            yname = "samplesNeeded", yfld = { it.samplesUsed },
            catName = "true p2 rate", catfld = { category(it) },
            scaleType = scaleType,
            // colorChoices = { colorChoices(it) },
        )
    }

    fun failureVsTheta(name:String, dirName: String, writeto : String, subtitle: String, scaleType: ScaleType, filter: (WorkflowResult) -> Boolean) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val data = io.readResults().filter(filter)
        if (data.isEmpty()) return
        wrsPlot(
            titleS = "Corba: true > estimated p2 rate",
            subtitleS = subtitle,
            writeFile = "$dirName/${writeto}",
            wrs = data,
            xname = "true margin", xfld = { it.mvrMargin },
            yname = "Success", yfld = { 100.0 - it.failPct },
            catName = "true p2 rate", catfld = { category(it) },
            scaleType = scaleType,
            // colorChoices = { colorChoices(it) },
        )
    }
}