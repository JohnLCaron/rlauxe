package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.ScaleTypeOld
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class CompareCorlaWithErrors {
    val nruns = 250  // number of times to run workflow
    val N = 10000
    val name = "corlaWithTwoPercentErrors"
    val dirName = "/home/stormy/temp/corla/$name"
    val mvrsFuzzPct = .02

    /*
    @Test
    fun corlaWithFuzz() {
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05)

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val corlaGenerator = CorlaWorkflowTaskGenerator(
                N, margin, 0.0, 0.0, mvrsFuzzPct,
                clcaConfigIn=ClcaConfig(ClcaStrategyType.noerror, 0.0),
                parameters=mapOf("nruns" to nruns, "cat" to "corla")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, corlaGenerator))

            val noerrorGenerator = ClcaWorkflowTaskGenerator(
                N, margin, 0.0, 0.0, mvrsFuzzPct,
                clcaConfigIn=ClcaConfig(ClcaStrategyType.noerror, 0.0),
                parameters=mapOf("nruns" to nruns, "cat" to "clcaNoerror")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, noerrorGenerator))

            val clcaGenerator = ClcaWorkflowTaskGenerator(
                N, margin, 0.0, 0.0, mvrsFuzzPct,
                clcaConfigIn=ClcaConfig(ClcaStrategyType.fuzzPct, mvrsFuzzPct),
                parameters=mapOf("nruns" to nruns, "cat" to "clcaFuzzPct")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            val clcaGeneratorHalf = ClcaWorkflowTaskGenerator(
                N, margin, 0.0, 0.0, mvrsFuzzPct,
                clcaConfigIn=ClcaConfig(ClcaStrategyType.fuzzPct, mvrsFuzzPct/2),
                parameters=mapOf("nruns" to nruns, "cat" to "clcaFuzzPct/2")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGeneratorHalf))

            val clcaGeneratorTwice = ClcaWorkflowTaskGenerator(
                N, margin, 0.0, 0.0, mvrsFuzzPct,
                clcaConfigIn=ClcaConfig(ClcaStrategyType.fuzzPct, 2*mvrsFuzzPct),
                parameters=mapOf("nruns" to nruns, "cat" to "2*clcaFuzzPct")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGeneratorTwice))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        val subtitle = "Nc=${N} nruns=${nruns} mvrsFuzzPct=$mvrsFuzzPct"
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleTypeOld.Linear)
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleTypeOld.Log)
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleTypeOld.Pct)
        showFailuresVsMargin(name, dirName, subtitle)
        showNroundsVsMargin(name, dirName, subtitle)
    }

    @Test
    fun regenNoerrorsPlots() {
        val subtitle = "Nc=${N} nruns=${nruns} mvrsFuzzPct=$mvrsFuzzPct"

        showSampleSizesVsMargin(name, dirName, subtitle, ScaleTypeOld.Linear)
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleTypeOld.Log)
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleTypeOld.Pct)
        showFailuresVsMargin(name, dirName, subtitle)
        showNroundsVsMargin(name, dirName, subtitle)
    }

     */
}