package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ClcaStrategyType
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

// generate cvrs as usual. Use ClcaAttackSampler to flip enough votes to flip the winner.

class CompareCorlaWithAttack {
    val nruns = 10  // number of times to run workflow
    val N = 100000
    val name = "corlaWithAttack"
    val dirName = "/home/stormy/rla/corla/$name"

    @Test
    fun corlaWithAttack() {
        val p2s = listOf(.001, .002, .005, .0075, .01, .02, .03, .04, .05)
        val reportedMeans = listOf(0.501, 0.502, 0.503, 0.504, 0.505, 0.506, 0.5075, 0.508, 0.51, 0.52, 0.53, 0.54, 0.55, 0.56, 0.58, 0.6,)

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        reportedMeans.forEach { mean ->
            p2s.forEach { p2 ->
                val margin = mean2margin(mean)
                val theta = mean - p2
                val corlaGenerator = CorlaSingleRoundAuditTaskGenerator(
                    N, margin, 0.0, 0.0, 0.0,
                    clcaConfigIn = ClcaConfig(ClcaStrategyType.noerror),
                    parameters = mapOf("nruns" to nruns, "theta" to theta, "p2" to p2, "cat" to df(p2)),
                    p2flips = p2
                )
                tasks.add(RepeatedWorkflowRunner(nruns, corlaGenerator))
            }
        }
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        regenPlots()
    }

    @Test
    fun regenPlots() {
        val subtitle = "Nc=${N} nruns=${nruns}"
        showSampleSizesVsTheta(name, dirName, subtitle, ScaleType.Linear)
        showSampleSizesVsTheta(name, dirName, subtitle, ScaleType.LogLinear)
        showSampleSizesVsTheta(name, dirName, subtitle, ScaleType.LogLog)

        showFailuresVsTheta(name, dirName, "${name}Failures", "Nc=${N} nruns=${nruns}") { true }
        showFailuresVsTheta(name, dirName, "${name}FailuresUnder", "Nc=${N} nruns=${nruns}") { it.Dparam("theta") <= .5 }
        showFailuresVsTheta(name, dirName, "${name}FailuresOver", "Nc=${N} nruns=${nruns}") { it.Dparam("theta") in 0.5..0.52 }
    }

    fun showFailuresVsTheta(name: String, dir: String, write: String, subtitle: String, filter: (WorkflowResult) -> Boolean) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val data = io.readResults().filter(filter)

        wrsPlot(
            titleS = "$write failurePct",
            subtitleS = subtitle,
            data,
            "$dir/$write",
            "theta",
            "failurePct",
            "p2rate",
            xfld = { it.Dparam("theta") },
            yfld = { it.failPct },
            catfld =  { category(it) },
        )
    }
}