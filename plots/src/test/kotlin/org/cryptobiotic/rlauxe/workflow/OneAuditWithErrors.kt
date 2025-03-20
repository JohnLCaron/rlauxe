package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.dfn
import kotlin.math.log10
import kotlin.test.Test

class OneAuditWithErrors {
    val name = "OneAuditWithErrors95"
    val dirName = "/home/stormy/temp/audits/$name" // you need to make this directory first

    val nruns = 100 // number of times to run workflow
    val nsimEst = 10
    val N = 50000
    val cvrPercent = 0.95
    val margin = .02

    @Test
    fun oaWithErrorsPlots() {
        val fuzzPcts = listOf(0.0, .0001, .00033, .00066, .001, .003, .005, .0075, .01, .02, .03, .04, .05)

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        fuzzPcts.forEach { fuzzPct ->
            val pollingGenerator = PollingSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, mvrsFuzzPct=fuzzPct,
                nsimEst = nsimEst,
                parameters=mapOf("nruns" to nruns, "cat" to "poll")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaSingleRoundAuditTaskGenerator(
                Nc = N, margin=margin, underVotePct=0.0, phantomPct=0.0, mvrsFuzzPct=fuzzPct,
                nsimEst = nsimEst,
                clcaConfigIn= ClcaConfig(ClcaStrategyType.noerror, 0.0),
                parameters=mapOf("nruns" to nruns, "cat" to "clca")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            val oneauditGenerator1 = OneAuditSingleRoundAuditTaskGenerator(
                Nc=N, margin=margin, underVotePct=0.0, phantomPct=0.0, cvrPercent=cvrPercent, mvrsFuzzPct=fuzzPct,
                parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "default"),
                auditConfigIn = AuditConfig(
                    AuditType.ONEAUDIT, true, nsimEst = 100,
                    oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.default)
                )
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator1))

            val oneauditGenerator2 = OneAuditSingleRoundAuditTaskGenerator(
                Nc=N, margin=margin, underVotePct=0.0, phantomPct=0.0, cvrPercent=cvrPercent, mvrsFuzzPct=fuzzPct,
                parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "max99"),
                auditConfigIn = AuditConfig(
                    AuditType.ONEAUDIT, true, nsimEst = 100,
                    oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.max99)
                )
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator2))
        }
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks, nthreads=40)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        regenPlots()
    }


    @Test
    fun regenPlots() {
        val subtitle = "Nc=${N} nruns=${nruns} cvrPercent=$cvrPercent margin=$margin"
        showSampleSizesVsFuzzPct(name, dirName, subtitle, ScaleType.Linear)
        showSampleSizesVsFuzzPct(name, dirName, subtitle, ScaleType.LogLinear)
        showSampleSizesVsFuzzPct(name, dirName, subtitle, ScaleType.LogLog)
        showFailuresVsFuzzPct(name, dirName, subtitle)
    }
}

fun showSampleSizesVsFuzzPct(name: String, dirName: String, subtitle: String, yscale: ScaleType) {
    val io = WorkflowResultsIO("$dirName/${name}.cvs")
    val data = io.readResults()

    wrsPlot(
        titleS = "$name samples vs fuzz",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}${yscale.name}",
        wrs=data,
        xname="mvrsFuzzPct", xfld = { it.Dparam("mvrsFuzzPct") },
        yname = "samplesNeeded", yfld = { it.samplesUsed },
        catName = "auditType", catfld = { category(it) },
        scaleType = yscale,
    )
}

fun showFailuresVsFuzzPct(name: String, dirName: String, subtitle: String) {
    val io = WorkflowResultsIO("$dirName/${name}.cvs")
    val data = io.readResults()

    wrsPlot(
        titleS = "$name failurePct",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}Failures",
        wrs = data,
        xname = "fuzzPct", xfld = { it.Dparam("mvrsFuzzPct") },
        yname = "failurePct", yfld = { it.failPct },
        catName = "strategy", catfld = { category(it) },
    )
}