package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.Scale
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsPlotter
import org.cryptobiotic.rlauxe.rlaplots.category
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test

class CompareAuditsWithPhantoms {
    val mvrFuzzPct = .01

    @Test
    fun genAuditWithPhantomsPlots() {
        val nruns = 100  // number of times to run workflow
        val nsimEst = 100  // number of times to run workflow
        val N = 50000
        val margin = .045
        val cvrPercent = .50
        val phantoms = listOf(.00, .005, .01, .02, .03, .04, .05)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        phantoms.forEach { phantom ->
            val pollingGenerator = PollingWorkflowTaskGenerator(N, margin, 0.0, phantomPct=phantom, mvrFuzzPct,
                auditConfigIn=AuditConfig(AuditType.POLLING, true, nsimEst = nsimEst),
                parameters=mapOf("nruns" to nruns, "phantom" to phantom, "mvrFuzz" to mvrFuzzPct, "cat" to "polling"))
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaWorkflowTaskGenerator(N, margin, 0.0, phantomPct=phantom, mvrFuzzPct,
                auditConfigIn=AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst),
                parameters=mapOf("nruns" to nruns, "phantom" to phantom, "mvrFuzz" to mvrFuzzPct, "cat" to "clca"))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            val oneauditGenerator = OneAuditWorkflowTaskGenerator(N, margin, 0.0, phantom, cvrPercent, mvrFuzzPct,
                auditConfigIn=AuditConfig(AuditType.ONEAUDIT, true, nsimEst = nsimEst),
                parameters=mapOf("nruns" to nruns, "phantom" to phantom, "mvrFuzz" to mvrFuzzPct, "cat" to "oneaudit"))
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val name = "auditsWithPhantoms"
        val dirName = "/home/stormy/temp/workflow/$name"

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        regenPlots()
    }

    @Test
    fun regenPlots() {
        val name = "auditsWithPhantoms"
        val dirName = "/home/stormy/temp/workflow/$name"

        showSampleSizesVsPhantomPct(dirName, name, true)
        showSampleSizesVsPhantomPct(dirName, name, false)
        showFailuresVsPhantomPct(dirName, name, )
    }

    fun showSampleSizesVsPhantomPct(dirName:String, name:String, useLog: Boolean) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showSampleSizesVsPhantomPct(results, "sampleSizes", "auditType", useLog=useLog)  { category(it) }
    }

    fun showFailuresVsPhantomPct(dirName: String, name:String, ) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showFailuresVsPhantomPct(results, "auditType")   { category(it) }
    }

    @Test
    fun genAuditsWithPhantomsPlotsMarginShift() {
        val nruns = 100  // number of times to run workflow
        val nsimEst = 100
        val N = 50000
        val margin = .045
        val phantoms = listOf(.00, .005, .01, .02, .03, .04, .05)
        val stopwatch = Stopwatch()

        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
            clcaConfig = ClcaConfig(ClcaStrategyType.noerror))

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        phantoms.forEach { phantom ->
            val clcaGenerator = ClcaWorkflowTaskGenerator(N, margin, 0.0, phantomPct=phantom, mvrFuzzPct,
                auditConfigIn=auditConfig,
                parameters=mapOf("nruns" to nruns, "phantom" to phantom, "mvrFuzz" to mvrFuzzPct, "cat" to "phantoms"))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            val clcaGenerator2 = ClcaWorkflowTaskGenerator(N, margin-phantom, 0.0, phantomPct=0.0, mvrFuzzPct,
                auditConfigIn=auditConfig,
                parameters=mapOf("nruns" to nruns, "phantom" to phantom, "mvrFuzz" to mvrFuzzPct, "cat" to "marginShift"))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator2))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val name = "phantomMarginShift"
        val dirName = "/home/stormy/temp/workflow/$name"

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        regenMarginShiftPlots()
    }

    @Test
    fun regenMarginShiftPlots() {
        val name = "phantomMarginShift"
        val dirName = "/home/stormy/temp/workflow/$name"

        showSampleSizesVsPhantomPct(dirName, name, true)
        showSampleSizesVsPhantomPct(dirName, name, false)
        showFailuresVsPhantomPct(dirName, name, )
    }

}