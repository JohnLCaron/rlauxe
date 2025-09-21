package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.io.path.Path
import kotlin.test.Test

// NOT USED
// audit variance for SF2024 OA contest 18 (mayoral)
// the cvrExports are already extracted with createSF2024cvrs90().
// the audit and contests are already setup with createSfElectionFromCsvExportOA().
// each repetition will choose a new prn and do a full sort. (alternatively could segment sortedCards by sampleLimit).
class SfAuditVariance {
    val nruns = 1 // no variance when there are no errors
    val nsimEst = 10
    val mvrsFuzzPct = .00

    val topDir = "/home/stormy/rla/cases/sf2024"
    val auditDir = "$topDir/audit"

    val name = "sf2024AuditVariance"
    val dirName = "/home/stormy/rla/plots/sf2024/$name"

    init {
        validateOutputDir(Path(dirName))
    }

    @Test
    fun genAuditVariationClcaPlots() {
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        repeat(nruns) { run ->
            val sfGenerator = SfSingleRoundAuditTaskGenerator(run, auditDir, mvrsFuzzPct, parameters = emptyMap())
            tasks.add(sfGenerator.generateNewTask())
        }
        val results: List<WorkflowResult> = runWorkflows(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        regenSf()
    }

    @Test
    fun regenSf() {
        val subtitle = "scatter plot of SF 2024 contests, Ntrials=$nruns"
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