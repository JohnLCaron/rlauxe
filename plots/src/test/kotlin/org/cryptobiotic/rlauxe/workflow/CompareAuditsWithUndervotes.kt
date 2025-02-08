package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsPlotter
import org.cryptobiotic.rlauxe.rlaplots.category
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test

class CompareAuditsWithUndervotes {
    val nruns = 100  // number of times to run workflow
    val nsimEst = 100  // number of times to run simulation
    val name = "auditsWithUndervotes"
    val dirName = "/home/stormy/temp/workflow/$name"
    val mvrFuzzPct = .01

    @Test
    fun genAuditWithUnderVotesPlots() {
        val N = 50000
        val margin = .04
        val cvrPercent = .50
        val undervotes = listOf(.00, .05, .10, .15, .20, .25, .30, .35, .40, .45, .50)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        undervotes.forEach { undervote ->
            val pollingGenerator = PollingWorkflowTaskGenerator(N, margin, undervote, phantomPct=0.0, mvrsFuzzPct=mvrFuzzPct,
                auditConfigIn=AuditConfig(AuditType.POLLING, true, nsimEst = nsimEst),
                parameters=mapOf("nruns" to nruns, "undervote" to undervote, "cat" to "polling"))
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaWorkflowTaskGenerator(N, margin, undervote, 0.0, mvrFuzzPct,
                auditConfigIn=AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst),
                parameters=mapOf("nruns" to nruns, "undervote" to undervote, "cat" to "clca"))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            val oneauditGenerator = OneAuditWorkflowTaskGenerator(N, margin, undervote, 0.0, cvrPercent=cvrPercent, fuzzPct=mvrFuzzPct,
                auditConfigIn=AuditConfig(AuditType.ONEAUDIT, true, nsimEst = nsimEst),
                parameters=mapOf("nruns" to nruns, "undervote" to undervote, "cat" to "oneaudit"))
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/auditsWithUndervotes.cvs")
        writer.writeResults(results)

        showSampleSizesVsUndervotePct(true)
        showSampleSizesVsUndervotePct(false)
    }

    fun showSampleSizesVsUndervotePct(useLog: Boolean) {
        val io = WorkflowResultsIO("$dirName/auditsWithUndervotes.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showSampleSizesVsUndervotePct(results, "auditsWithUndervotes","auditType", useLog=useLog)   { category(it) }
    }

}