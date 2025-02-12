package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.corla.showFailuresVsMargin
import org.cryptobiotic.rlauxe.corla.showNroundsVsMargin
import org.cryptobiotic.rlauxe.corla.showSampleSizesVsMargin
import org.cryptobiotic.rlauxe.rlaplots.ScaleTypeOld
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class CompareRaireNoErrors {
    val nruns = 100  // number of times to run workflow
    val N = 10000
    val name = "raireNoErrors"
    val dirName = "/home/stormy/temp/workflow/$name"

    @Test
    fun raireNoErrorsPlots() {
        val margins =
            listOf(.005, .006, .008, .01, .012, .016, .02, .03, .04, .05)

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val raireGenerator = RaireWorkflowTaskGenerator(
                N, margin, 0.0, 0.0, 0.0,
                clcaConfigIn=ClcaConfig(ClcaStrategyType.noerror, 0.0),
                parameters=mapOf("nruns" to nruns, "cat" to "raire")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, raireGenerator))

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
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleTypeOld.Linear)
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleTypeOld.Log)
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleTypeOld.Pct)
        showFailuresVsMargin(name, dirName, subtitle)
        showNroundsVsMargin(name, dirName, subtitle)
    }

    @Test
    fun regenNoerrorsPlots() {
        val subtitle = "Nc=${N} nruns=${nruns}"

        showSampleSizesVsMargin(name, dirName, subtitle, ScaleTypeOld.Linear)
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleTypeOld.Log)
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleTypeOld.Pct)
        showFailuresVsMargin(name, dirName, subtitle)
        showNroundsVsMargin(name, dirName, subtitle)
    }
}
