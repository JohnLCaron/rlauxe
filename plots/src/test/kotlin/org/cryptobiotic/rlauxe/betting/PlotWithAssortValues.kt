package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.WorkflowResult
import org.cryptobiotic.rlauxe.workflow.runRepeatedWorkflowsAndAverage
import org.cryptobiotic.rlauxe.workflow.sampleSizesVsFuzzPctStdDev
import org.cryptobiotic.rlauxe.workflow.sampleSizesVsMarginStdDev
import org.cryptobiotic.rlauxe.workflow.showSampleSizesVsFuzzPct
import org.cryptobiotic.rlauxe.workflow.showSampleSizesVsMargin
import kotlin.io.path.Path
import kotlin.test.Test

class PlotWithAssortValues {

    @Test
    fun compareMaxRiskVsMargin() {
        val nruns = 100
        val name = "compareMaxRiskVsMargin"
        val dirName = "$testdataDir/plots/betting/$name"
        val N = 50000

        val maxRisk = listOf(.50, .75, .90, )
        val margins = listOf(.005, .0075, .01, .0125, .015, .02, .03, .04, .05)

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        maxRisk.forEach { maxRisk ->
            margins.forEach { margin ->
                val clcaGenerator = ClcaSingleRoundAssortTaskGenerator(
                    N, margin, upper = 1.0, maxRisk = maxRisk,
                    parameters = mapOf("margin" to margin, "cat" to maxRisk)
                )
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))
            }
        }

        // run tasks concurrently and average the results
        println("---auditWithAssortValues running ${tasks.size} tasks nruns= $nruns")
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        validateOutputDir(Path(dirName))
        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)
        val subtitle = "Nc=${N} nruns=${nruns}"
        // fun showSampleSizesVsMargin(name: String, dirName: String, subtitle: String, yscale: ScaleType, catName: String = "auditType") {
        showSampleSizesVsMargin(name, dirName, subtitle, yscale=ScaleType.LogLog,  catName="maxRisk")
        // fun sampleSizesVsMarginStdDev(dirName: String, name:String, subtitle: String,
        //                               catName: String, catfld: ((WorkflowResult) -> String) = { category(it) } ) {
        sampleSizesVsMarginStdDev(dirName, name, subtitle,  catName="maxRisk")
    }

    @Test
    fun compareMaxRiskVsErrorRate() {
        val nruns = 100
        val name = "compareMaxRiskVsErrorRate"
        val dirName = "$testdataDir/plots/betting/$name"
        val N = 50000

        val maxRisk = listOf(.50, .75, .90, )
        val margin = .01
        val rates = listOf(.0001, .0005, .001, .003, .005, .01)

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        rates.forEach { rate ->
            maxRisk.forEach { maxRisk ->
                val clcaGenerator = ClcaSingleRoundAssortTaskGenerator(
                    N, margin, upper = 1.0, maxRisk = maxRisk, errorRates=rate,
                    parameters = mapOf("margin" to margin, "fuzzPct" to rate, "cat" to maxRisk)
                )
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))
            }
        }

        // run tasks concurrently and average the results
        println("---compareMaxRiskVsErrorRate running ${tasks.size} tasks nruns= $nruns")
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        validateOutputDir(Path(dirName))
        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)
        val subtitle = "Nc=${N} nruns=${nruns} margin=$margin"
        // fun showSampleSizesVsMargin(name: String, dirName: String, subtitle: String, yscale: ScaleType, catName: String = "auditType") {
        showSampleSizesVsFuzzPct(dirName, name, subtitle, scaleType=ScaleType.LogLog,  catName="maxRisk")
        sampleSizesVsFuzzPctStdDev(dirName, name, subtitle, catName="maxRisk", catfld= { category(it) })
    }
}