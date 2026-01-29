package org.cryptobiotic.rlauxe.attack

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class OaPhantomAttack {
    var name = "oaPhantomAttack"
    var dirName = "$testdataDir/attack/$name"
    var phantomPct = 0.02

    val N = 100000
    val nruns = 100
    val nsimEst = 10
    val fuzzPct = .01
    val cvrPercent = 0.5

    @Test
    fun genSamplesVsMarginWithPhantoms() {
        val allMargins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .025, .03, .04, .05, .06, .07, .08, .10)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<RepeatedWorkflowRunner>()
        allMargins.forEach { margin ->
            val oneauditGeneratorReportedMean = OneAuditSingleRoundAuditTaskGeneratorWithFlips(
                Nc=N, margin=margin, underVotePct=0.0, phantomPct=phantomPct, cvrPercent=cvrPercent, mvrsFuzzPct=fuzzPct,
                parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "reportedMean", "fuzzPct" to fuzzPct),
                auditConfigIn = AuditConfig(
                    AuditType.ONEAUDIT, true, nsimEst = nsimEst,
                ),
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGeneratorReportedMean))

            val oneauditGeneratorBet99 = OneAuditSingleRoundAuditTaskGeneratorWithFlips(
                Nc=N, margin=margin, underVotePct=0.0, phantomPct=phantomPct, cvrPercent=cvrPercent, mvrsFuzzPct=fuzzPct,
                parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "simulate", "fuzzPct" to fuzzPct),
                auditConfigIn = AuditConfig(
                    AuditType.ONEAUDIT, true, nsimEst = nsimEst,
                    oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.simulate)
                ),
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGeneratorBet99))

            val oneauditGeneratorEta0Eps = OneAuditSingleRoundAuditTaskGeneratorWithFlips(
                Nc=N, margin=margin, underVotePct=0.0, phantomPct=phantomPct, cvrPercent=cvrPercent, mvrsFuzzPct=fuzzPct,
                parameters=mapOf("nruns" to nruns.toDouble(), "cat" to "calcMvrsNeeded", "fuzzPct" to fuzzPct),
                auditConfigIn = AuditConfig(
                    AuditType.ONEAUDIT, true, nsimEst = nsimEst,
                    oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.calcMvrsNeeded)
                ),
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGeneratorEta0Eps))
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
        showFailuresVsMargin(dirName, name, subtitle)
    }

    fun showSampleSizesVsMargin(dirName: String, name:String, subtitle: String, scaleType: ScaleType) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
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
        val io = WorkflowResultsIO("$dirName/${name}.csv")
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