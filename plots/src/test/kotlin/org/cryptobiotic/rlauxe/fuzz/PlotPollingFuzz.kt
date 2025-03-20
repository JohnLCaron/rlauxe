package org.cryptobiotic.rlauxe.fuzz

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.ScaleType
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class PlotPollingFuzz {
    val name = "pollByMvrFuzzPct"
    val dirName = "/home/stormy/temp/fuzz"
    val N = 10000
    val nruns = 100

    @Test
    fun plotPollingFuzz() {

        val fuzzPcts = listOf(0.0, 0.001, 0.0025, .005, .01, .02, .05)
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        val config = AuditConfig(AuditType.POLLING, true, nsimEst = 100)

        val stopwatch = Stopwatch()
        val tasks = mutableListOf<RepeatedWorkflowRunner>()
        fuzzPcts.forEach { mvrsFuzzPct ->
            margins.forEach { margin ->
                val clcaGenerator = PollingSingleRoundAuditTaskGenerator(N, margin, 0.0, 0.0, mvrsFuzzPct,
                    parameters=mapOf("nruns" to nruns, "fuzzPct" to mvrsFuzzPct),
                    auditConfig=config,
                )
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))
            }
        }
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        val subtitle = "Nc=${N} nruns=${nruns}"
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.Linear)
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.LogLog)
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.LogLinear)
        showFailuresVsMargin(dirName, name, subtitle)
    }

    @Test
    fun regenPlots() {
        val subtitle = "Nc=${N} nruns=${nruns}"
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.Linear)
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.LogLog)
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.LogLinear)
        showFailuresVsMargin(dirName, name, subtitle)
    }
}