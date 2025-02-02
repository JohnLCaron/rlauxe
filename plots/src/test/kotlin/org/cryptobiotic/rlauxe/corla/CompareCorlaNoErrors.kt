package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.category
import org.cryptobiotic.rlauxe.rlaplots.Scale
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsPlotter
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class CompareCorlaNoErrors {
    val nruns = 10  // number of times to run workflow
    val N = 10000
    val name = "corlaNoErrors"
    val dirName = "/home/stormy/temp/corla/$name"

    @Test
    fun corlaNoErrorsPlots() {
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05)

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val corlaGenerator = CorlaWorkflowTaskGenerator(
                N, margin, 0.0, 0.0, 0.0,
                clcaConfigIn=ClcaConfig(ClcaStrategyType.noerror, 0.0),
                parameters=mapOf("nruns" to nruns, "cat" to "corla")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, corlaGenerator))

            val noerrorGenerator = ClcaWorkflowTaskGenerator(
                N, margin, 0.0, 0.0, 0.0,
                clcaConfigIn=ClcaConfig(ClcaStrategyType.noerror, 0.0),
                parameters=mapOf("nruns" to nruns, "cat" to "clcaNoerror")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, noerrorGenerator))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        val subtitle = "Nc=${N} nruns=${nruns}"
        showSampleSizesVsMargin(name, dirName, subtitle, Scale.Linear)
        showSampleSizesVsMargin(name, dirName, subtitle, Scale.Log)
        showSampleSizesVsMargin(name, dirName, subtitle, Scale.Pct)
        showFailuresVsMargin(name, dirName, subtitle)
        showNroundsVsMargin(name, dirName, subtitle)
    }

    @Test
    fun regenNoerrorsPlots() {
        val subtitle = "Nc=${N} nruns=${nruns}"

        showSampleSizesVsMargin(name, dirName, subtitle, Scale.Linear)
        showSampleSizesVsMargin(name, dirName, subtitle, Scale.Log)
        showSampleSizesVsMargin(name, dirName, subtitle, Scale.Pct)
        showFailuresVsMargin(name, dirName, subtitle)
        showNroundsVsMargin(name, dirName, subtitle)
    }
}

fun showSampleSizesVsMargin(name: String, dirName: String, subtitle: String, yscale: Scale) {
    val io = WorkflowResultsIO("$dirName/${name}.cvs")
    val results = io.readResults()

    val plotter = WorkflowResultsPlotter(dirName, name)
    plotter.showSampleSizesVsMargin(results, subtitle, "auditType", yscale) { category(it) }
}

fun showFailuresVsMargin(name: String, dirName: String, subtitle: String) {
    val io = WorkflowResultsIO("$dirName/${name}.cvs")
    val results = io.readResults()

    val plotter = WorkflowResultsPlotter(dirName, name)
    plotter.showFailuresVsMargin(results, subtitle, "auditType") { category(it) }
}

fun showNroundsVsMargin(name: String, dirName: String, subtitle: String) {
    val io = WorkflowResultsIO("$dirName/${name}.cvs")
    val results = io.readResults()

    val plotter = WorkflowResultsPlotter(dirName, name)
    plotter.showNroundsVsMargin(results, subtitle, "auditType") { category(it) }
}
