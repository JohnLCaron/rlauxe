package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.io.path.Path
import kotlin.test.Test

class AuditsWithUndervotes {
    val nruns = 100  // number of times to run workflow
    val name = "AuditsWithUndervotes"
    val dirName = "$testdataDir/plots/undervotes/$name"
    val mvrFuzzPct = .01
    val margin = .04
    val N = 50000

    @Test
    fun genAuditWithUnderVotesPlots() {
        val undervotes = listOf(.00, .10, .20, .30, .40, .50)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        undervotes.forEach { undervote ->
            val pollingGenerator = PollingSingleRoundAuditTaskGenerator(N, margin, undervote, phantomPct=0.0, mvrsFuzzPct=mvrFuzzPct,
                parameters=mapOf("nruns" to nruns, "undervote" to undervote, "cat" to "polling"))
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaSingleRoundAuditTaskGenerator(N, margin, undervote, 0.0, mvrFuzzPct,
                parameters=mapOf("nruns" to nruns, "undervote" to undervote, "cat" to "clca"))
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            val oneauditGenerator = OneAuditSingleRoundAuditTaskGenerator(
                N, margin, undervote, 0.0, cvrPercent = .90, mvrsFuzzPct=mvrFuzzPct,
                parameters=mapOf("nruns" to nruns, "undervote" to undervote, "cat" to "oneaudit-90%"),
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        validateOutputDir(Path(dirName))
        val writer = WorkflowResultsIO("$dirName/AuditsWithUndervotes.csv")
        writer.writeResults(results)

        regenPlots()
    }

    @Test
    fun regenPlots() {
        val subtitle = "margin=${margin} Nc=${N} nruns=${nruns}  mvrFuzz=${mvrFuzzPct}"
        showSampleSizesVsUndervotePct(dirName, name, subtitle, ScaleType.Linear, catName="auditType")
        showSampleSizesVsUndervotePct(dirName, name, subtitle, ScaleType.LogLinear, catName="auditType")
        showSampleSizesVsUndervotePct(dirName, name, subtitle, ScaleType.LogLog, catName="auditType")
    }

    fun showSampleSizesVsUndervotePct(dirName: String, name:String, subtitle: String, scaleType: ScaleType,
                                 catName: String, catfld: ((WorkflowResult) -> String) = { category(it) } ) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val data = io.readResults()
        wrsPlot(
            titleS = "$name samples needed",
            subtitleS = subtitle,
            writeFile = "$dirName/${name}${scaleType.name}",
            wrs = data,
            xname = "underVotePct", xfld = { it.Dparam("undervote") },
            yname = "samplesNeeded", yfld = { it.samplesUsed },
            catName = catName, catfld = catfld,
            scaleType = scaleType
        )
    }

}