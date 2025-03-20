package org.cryptobiotic.rlauxe.nostyles

import org.cryptobiotic.rlauxe.concur.*
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class GenPollingNoStyles {
    val name = "pollingNoStyle"
    val dirName = "/home/stormy/temp/nostyle/$name"

    val Nc = 10000
    val nruns = 10

    // Used in docs

    @Test
    fun plotPollingNoStyle() {

        val Nbs = listOf(10000, 20000, 50000, 100000)
        val margins = listOf(.01, .02, .03, .04, .05, .06, .08, .10, .15, .20)
        val auditConfig = AuditConfig(AuditType.POLLING, false, samplePctCutoff=0.5, nsimEst = 10)

        val stopwatch = Stopwatch()
        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        Nbs.forEach { Nb ->
            margins.forEach { margin ->
                val pollingGenerator = PollingWorkflowTaskGenerator(
                    Nc, margin, 0.0, 0.0, 0.0,
                    mapOf("nruns" to nruns, "Nb" to Nb, "cat" to Nb),
                    auditConfig = auditConfig,
                    Nb)

                tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))
            }
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())


        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        regenPlots()
    }

    @Test
    fun regenPlots() {
        val subtitle = "Nc=${Nc} nruns=${nruns}"
        showNmvrsByNb(name, dirName, subtitle, ScaleType.Linear)
        showNmvrsByNb(name, dirName, subtitle, ScaleType.LogLinear)
        showNmvrsByNb(name, dirName, subtitle, ScaleType.LogLog)
        showNmvrPctByNb(name, dirName, subtitle)
    }

    /*
    fun showNmvrsVsMargin(name: String, dirName: String, yscale: ScaleTypeOld) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showNmvrsVsMargin(results, "Nballots", yscale) { categoryNb(it) }
    }

    fun showFailuresVsMargin(name: String, dirName: String, ) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showFailuresVsMargin(results, catName="Nballots") { categoryNb(it) }
    }

    fun categoryNb(wr: WorkflowResult): String {
        val Nb = wr.parameters["Nb"] as Int
        return nfn(Nb, 6)
    } */
}

fun showNmvrsByNb(name: String, dirName: String, subtitle: String, scaleType: ScaleType) {
    val io = WorkflowResultsIO("$dirName/${name}.cvs")
    val data = io.readResults()

    wrsPlot(
        titleS = "$name overall number of ballots sampled",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}${scaleType.name}",
        wrs = data,
        xname = "margin", xfld = { it.margin },
        yname = "nmvrs", yfld = { it.nmvrs },
        catName = "Nballots", catfld = { category(it) },
        scaleType = scaleType
    )
}

fun showNmvrPctByNb(name: String, dirName: String, subtitle: String) {
    val io = WorkflowResultsIO("$dirName/${name}.cvs")
    val data = io.readResults()

    wrsPlot(
        titleS = "$name percent ballots sampled",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}Pct",
        wrs = data,
        xname = "margin", xfld = { it.margin },
        yname = "% of Nb", yfld = { (100*it.nmvrs/it.Dparam("Nb")) },
        catName = "Nballots", catfld = { category(it) },
        scaleType = ScaleType.Linear
    )
}
