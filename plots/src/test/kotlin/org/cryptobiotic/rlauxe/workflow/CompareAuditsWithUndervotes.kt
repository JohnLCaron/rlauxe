package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test

class CompareAuditsWithUndervotes {
    val nruns = 100  // number of times to run workflow
    val nsimEst = 100  // number of times to run simulation
    val name = "auditsWithUndervotes"
    val dirName = "/home/stormy/temp/samples/$name"
    val mvrFuzzPct = .01
    val margin = .04
    val N = 50000

    @Test
    fun genAuditWithUnderVotesPlots() {
        val cvrPercent = .50
        val undervotes = listOf(.00, .05, .10, .15, .20, .25, .30, .35, .40, .45, .50)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        undervotes.forEach { undervote ->
            val pollingGenerator = PollingWorkflowTaskGenerator(N, margin, undervote, phantomPct=0.0, mvrsFuzzPct=mvrFuzzPct,
                auditConfig=AuditConfig(AuditType.POLLING, true, nsimEst = nsimEst),
                parameters=mapOf("nruns" to nruns, "undervote" to undervote, "cat" to "polling"))
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaWorkflowTaskGenerator(N, margin, undervote, 0.0, mvrFuzzPct,
                auditConfig=AuditConfig(AuditType.CLCA, true, nsimEst = nsimEst),
                parameters=mapOf("nruns" to nruns, "undervote" to undervote, "cat" to "clca"))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            val oneauditGenerator = OneAuditWorkflowTaskGenerator(N, margin, undervote, 0.0, cvrPercent=cvrPercent, mvrsFuzzPct=mvrFuzzPct,
                auditConfigIn=AuditConfig(AuditType.ONEAUDIT, true, nsimEst = nsimEst),
                parameters=mapOf("nruns" to nruns, "undervote" to undervote, "cat" to "oneaudit"))
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/auditsWithUndervotes.cvs")
        writer.writeResults(results)

        regenPlots()
    }

    @Test
    fun regenPlots() {
        val name = "auditsWithUndervotes"
        val dirName = "/home/stormy/temp/workflow/$name"

        val subtitle = "margin=${margin} Nc=${N} nruns=${nruns}"
        showSampleSizesVsUndervotePct(dirName, name, subtitle, ScaleType.Linear, catName="auditType")
        showSampleSizesVsUndervotePct(dirName, name, subtitle, ScaleType.LogLinear, catName="auditType")
    }

    fun showSampleSizesVsUndervotePct(dirName: String, name:String, subtitle: String, scaleType: ScaleType,
                                 catName: String, catfld: ((WorkflowResult) -> String) = { it -> category(it) } ) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val data = io.readResults()
        wrsPlot(
            titleS = "$name samples needed",
            subtitleS = subtitle,
            writeFile = "$dirName/${name}${scaleType.name}",
            wrs = data,
            xname = "underVotePct", xfld = { it.Dparam("undervote") },
            yname = "samplesNeeded", yfld = { it.samplesNeeded },
            catName = catName, catfld = catfld,
            scaleType = scaleType
        )
    }

}