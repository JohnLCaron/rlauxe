package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.df
import kotlin.test.Test

class AuditsWithPhantoms {
    val name = "AuditsWithPhantoms"
    val dirName = "/home/stormy/rla/audits/$name"

    val mvrFuzzPct = .01
    val nruns = 500  // number of times to run workflow
    val nsimEst = 10  // number of times to run workflow
    val N = 50000
    val margin = .045

    @Test
    fun genAuditWithPhantomsPlots() {
        val phantoms = listOf(.00, .005, .01, .02, .03, .04, .05)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        phantoms.forEach { phantom ->
            val pollingGenerator = PollingSingleRoundAuditTaskGenerator(N, margin, 0.0, phantomPct=phantom, mvrFuzzPct,
                auditConfig= AuditConfig(AuditType.POLLING, true, nsimEst = nsimEst),
                parameters=mapOf("nruns" to nruns, "phantom" to phantom, "mvrFuzz" to mvrFuzzPct, "cat" to "polling"))
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaSingleRoundAuditTaskGenerator(N, margin, 0.0, phantomPct=phantom, mvrFuzzPct,
                auditConfig= AuditConfig(AuditType.CLCA, true, nsimEst = nsimEst),
                parameters=mapOf("nruns" to nruns, "phantom" to phantom, "mvrFuzz" to mvrFuzzPct, "cat" to "clca"))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            /* val oneauditGenerator = OneAuditSingleRoundAuditTaskGenerator(
                N, margin, 0.0, phantomPct=phantom, cvrPercent = .95, mvrsFuzzPct=mvrFuzzPct, skewPct = .05,
                parameters=mapOf("nruns" to nruns, "phantom" to phantom, "mvrFuzz" to mvrFuzzPct, "cat" to "oneaudit"),
                auditConfigIn = AuditConfig(
                    AuditType.ONEAUDIT, true,
                    oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.eta0Eps)
                )
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator)) */
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
        val subtitle = "margin=${df(margin)} Nc=${N} nruns=${nruns} mvrFuzz=${mvrFuzzPct}"
        showSampleSizesVsPhantomPct(dirName, name, subtitle, ScaleType.Linear, catName="auditType")
        showSampleSizesVsPhantomPct(dirName, name, subtitle, ScaleType.LogLinear, catName="auditType")
        showSampleSizesVsPhantomPct(dirName, name, subtitle, ScaleType.LogLog, catName="auditType")
    }

    fun showSampleSizesVsPhantomPct(dirName: String, name:String, subtitle: String, scaleType: ScaleType,
                                      catName: String, catfld: ((WorkflowResult) -> String) = { category(it) } ) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val data = io.readResults()
        wrsPlot(
            titleS = "$name samples needed",
            subtitleS = subtitle,
            writeFile = "$dirName/${name}${scaleType.name}",
            wrs = data,
            xname = "phantomPct", xfld = { it.Dparam("phantom") },
            yname = "samplesNeeded", yfld = { it.samplesUsed },
            catName = catName, catfld = catfld,
            scaleType = scaleType
        )
    }

    @Test
    fun genAuditsWithPhantomsPlotsMarginShift() {
        val nruns = 100  // number of times to run workflow
        val nsimEst = 100
        val margin = .045
        val phantoms = listOf(.00, .005, .01, .02, .03, .035, .04, .0425)
        val stopwatch = Stopwatch()

        val auditConfig = AuditConfig(
            AuditType.CLCA, true, nsimEst = nsimEst,
            clcaConfig = ClcaConfig(ClcaStrategyType.noerror)
        )

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        phantoms.forEach { phantom ->
            val clcaGenerator = ClcaSingleRoundAuditTaskGenerator(N, margin, 0.0, phantomPct=phantom, mvrFuzzPct,
                auditConfig=auditConfig,
                parameters=mapOf("nruns" to nruns, "phantom" to phantom, "mvrFuzz" to mvrFuzzPct, "cat" to "phantoms"))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            val clcaGenerator2 = ClcaSingleRoundAuditTaskGenerator(N, margin-phantom, 0.0, phantomPct=0.0, mvrFuzzPct,
                auditConfig=auditConfig,
                parameters=mapOf("nruns" to nruns, "phantom" to phantom, "mvrFuzz" to mvrFuzzPct, "cat" to "marginShift"))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator2))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val name = "phantomMarginShift"
        val dirName = "/home/stormy/rla/samples/$name"

        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        regenMarginShiftPlots()
    }

    @Test
    fun regenMarginShiftPlots() {
        val name = "phantomMarginShift"
        val dirName = "/home/stormy/rla/samples/$name"

        val subtitle = "margin=${df(.045)} Nc=${N} nruns=${300} mvrFuzz=${mvrFuzzPct}"
        showSampleSizesVsPhantomPct(dirName, name, subtitle, ScaleType.Linear, catName="auditType")
        showSampleSizesVsPhantomPct(dirName, name, subtitle, ScaleType.LogLinear, catName="auditType")
        showSampleSizesVsPhantomPct(dirName, name, subtitle, ScaleType.LogLog, catName="auditType")
    }

}