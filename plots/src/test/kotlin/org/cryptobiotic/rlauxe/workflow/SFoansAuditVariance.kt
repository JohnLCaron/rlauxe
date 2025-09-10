package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test

// audit variance for SF2024 OA contest 18 (mayoral)
// the cvrExports are already extracted with createSF2024cvrs90().
// the audit and contests are already setup with createSfElectionFromCsvExportOA().
// each repetition will choose a new prn and do a full sort. (alternatively could segment sortedCards by sampleLimit).
class SFoansAuditVariance {
    val nruns = 50
    val nsimEst = 10
    val mvrsFuzzPct = .00

    val topDir = "/home/stormy/rla/cases/sf2024oaNS"
    val auditDir = "$topDir/audit"

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

        val name = "sfoans2024"
        val dirName = "/home/stormy/rla/oneaudit4/$name"
        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        regenSfoa()
    }

    @Test
    fun regenSfoa() {
        val name = "sfoans2024"
        val dirName = "/home/stormy/rla/oneaudit4/$name"
        val subtitle = "scatter plot of SF 2024 OneAudit contests 'no style', Ntrials=$nruns"
        showNSamplesVsMarginScatter(dirName, name, subtitle, ScaleType.LogLinear)
    }

    fun showNSamplesVsMarginScatter(dirName: String, name:String, subtitle: String, scaleType: ScaleType,) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val data = io.readResults()

        wrsScatterPlot(
            titleS = "$name nmvrs vs margin, no errors",
            subtitleS = subtitle,
            wrs=data,
            writeFile="$dirName/${name}Nmvrs${scaleType.name}",
            xname="margin", xfld = { it.margin },
            yname = "samplesNeeded", yfld = { it.samplesUsed },
            runName = "assertion", runFld = { runName(it) },
            scaleType=scaleType,
        )
    }

}