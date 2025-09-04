package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test

// audit variance for SF2024 OA contest 18 (mayoral)
// the cvrExports are already extracted with createSF2024cvrs90().
// the audit and contests are already setup with createSfElectionFromCsvExportOA().
// each repetition will choose a new prn and do a full sort. (alternatively could segment sortedCards by sampleLimit).
class SFoaAuditVariance {
    val nruns = 50
    val nsimEst = 10
    val mvrsFuzzPct = .00

    val sfDir = "/home/stormy/rla/cases/sf2024"
    val topDir = "/home/stormy/rla/cases/sf2024oa"
    val auditDir = "$topDir/audit"
    val cvrCsv = "$topDir/cvrExport.csv"

    @Test
    fun genAuditVariationClcaPlots() {
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        repeat(nruns) { run ->
            val sfoaGenerator = SFoaSingleRoundAuditTaskGenerator( run, auditDir, mvrsFuzzPct, parameters=emptyMap())
            tasks.add(sfoaGenerator.generateNewTask())
        }
        val results: List<WorkflowResult> = runWorkflows(tasks)
        println(stopwatch.took())

        val name = "sfoaVariance"
        val dirName = "/home/stormy/rla/sfoaAll/$name"
        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        regenSfoa()
    }

    @Test
    fun regenSfoa() {
        val name = "sfoaVariance"
        val dirName = "/home/stormy/rla/sfoaAll/$name"
        val subtitle = "scatter plot of SF 2024 OneAudit contests, Ntrials=$nruns"
        showNSamplesVsMarginScatter(dirName, name, subtitle, ScaleType.LogLinear)
    }

    fun showNSamplesVsMarginScatter(dirName: String, name:String, subtitle: String, scaleType: ScaleType,) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val data = io.readResults()

        wrsScatterPlot(
            titleS = "$name nmvrs vs samplesNeeded, no errors",
            subtitleS = subtitle,
            wrs=data,
            writeFile="$dirName/${name}Nmvrs${scaleType.name}",
            xname="margin", xfld = { it.margin },
            yname = "samplesNeeded", yfld = { it.samplesUsed },
            catName = "auditType", catfld = { category(it) },
            scaleType=scaleType,
        )
    }

}