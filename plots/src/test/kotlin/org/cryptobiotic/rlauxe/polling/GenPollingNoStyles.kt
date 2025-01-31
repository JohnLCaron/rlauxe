package org.cryptobiotic.rlauxe.polling

import org.cryptobiotic.rlauxe.concur.*
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.random.Random
import kotlin.test.Test

class GenPollingNoStyles {

    // Used in docs

    @Test
    fun plotPollingNoStyle() {
        val name = "pollingNoStyle"
        val dirName = "/home/stormy/temp/workflow/$name"

        val Nc = 10000
        val nruns = 100
        val Nbs = listOf(10000, 20000, 50000, 100000)
        val margins = listOf(.01, .02, .03, .04, .05, .06, .08, .10, .15, .20)
        val auditConfig = AuditConfig(AuditType.POLLING, false, samplePctCutoff=0.5, nsimEst = 10)

        val stopwatch = Stopwatch()
        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        Nbs.forEach { Nb ->
            margins.forEach { margin ->
                val pollingGenerator = PollingWorkflowTaskGenerator(
                    Nc, margin, 0.0, 0.0, 0.0,
                    mapOf("nruns" to nruns.toDouble(), "Nb" to Nb.toDouble()),
                    auditConfigIn = auditConfig,
                    Nb)

                tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))
            }
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())


        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        showNmvrsVsMargin(name, dirName, Scale.Linear)
        showNmvrsVsMargin(name, dirName, Scale.Log)
        showNmvrsVsMargin(name, dirName, Scale.Pct)
        showFailuresVsMargin(name, dirName)
    }

    fun showNmvrsVsMargin(name: String, dirName: String, yscale: Scale) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showNmvrsVsMargin(results, "Nballots", yscale) { category(it) }
    }

    fun showFailuresVsMargin(name: String, dirName: String, ) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showFailuresVsMargin(results, catName="Nballots") { category(it) }
    }

    fun category(wr: WorkflowResult): String {
        val Nb = wr.parameters["Nb"] ?: 0.0
        return nfn(Nb.toInt(), 6)
    }
}