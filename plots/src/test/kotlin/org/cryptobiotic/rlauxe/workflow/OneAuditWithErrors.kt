package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test

class OneAuditWithErrors {
    val name = "OneAuditWithErrors4"
    val dirName = "$testdataDir/audits/$name" // you need to make this directory first

    val nruns = 100 // number of times to run workflow
    val N = 50000
    val cvrPercent = 0.95
    val margin = .04

    @Test
    fun oaWithErrorsPlots() {
        val fuzzPcts = listOf(0.0, .0001, .0003, .001, .003, .005, .0075, .01, .015, .02,)

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        fuzzPcts.forEach { fuzzPct ->
            val pollingGenerator = PollingSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, mvrsFuzzPct=fuzzPct,
                parameters=mapOf("nruns" to nruns, "cat" to "poll")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaSingleRoundAuditTaskGenerator(
                Nc = N, margin=margin, underVotePct=0.0, phantomPct=0.0, mvrsFuzzPct=fuzzPct,
                parameters=mapOf("nruns" to nruns, "cat" to "clca")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            val oneauditGeneratorAdaptive = OneAuditSingleRoundAuditTaskGenerator(
                Nc=N, margin=margin, underVotePct=0.0, phantomPct=0.0, cvrPercent=cvrPercent, mvrsFuzzPct=fuzzPct,
                parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "adaptive"),
                auditConfigIn = AuditConfig(
                    AuditType.ONEAUDIT, true, simFuzzPct=fuzzPct,
                    oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.clca),
                )
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGeneratorAdaptive))

            val oneauditGeneratorReportedMean = OneAuditSingleRoundAuditTaskGenerator(
                Nc=N, margin=margin, underVotePct=0.0, phantomPct=0.0, cvrPercent=cvrPercent, mvrsFuzzPct=fuzzPct,
                parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "optimalBet"),
                auditConfigIn = AuditConfig(
                    AuditType.ONEAUDIT, true,
                    oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.optimalComparison)
                )
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGeneratorReportedMean))

            val oneauditGeneratorBet99 = OneAuditSingleRoundAuditTaskGenerator(
                Nc=N, margin=margin, underVotePct=0.0, phantomPct=0.0, cvrPercent=cvrPercent, mvrsFuzzPct=fuzzPct,
                parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "bet99"),
                auditConfigIn = AuditConfig(
                    AuditType.ONEAUDIT, true,
                    oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.bet99)
                )
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGeneratorBet99))

            val oneauditGeneratorEta0Eps = OneAuditSingleRoundAuditTaskGenerator(
                Nc=N, margin=margin, underVotePct=0.0, phantomPct=0.0, cvrPercent=cvrPercent, mvrsFuzzPct=fuzzPct,
                parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "eta0Eps"),
                auditConfigIn = AuditConfig(
                    AuditType.ONEAUDIT, true,
                    oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.eta0Eps)
                )
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGeneratorEta0Eps))
        }
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks, nthreads=40)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.csv")
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
    val io = WorkflowResultsIO("$dirName/${name}.csv")
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
    val io = WorkflowResultsIO("$dirName/${name}.csv")
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