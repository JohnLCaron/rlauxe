package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.io.path.Path
import kotlin.math.log10
import kotlin.test.Test

class AuditsNoErrors {
    val name = "AuditsNoErrors4"
    val dirName = "$testdataDir/plots/samplesNeeded/$name" // you need to make this directory first
    val N = 50000
    val nruns = 100

    @Test
    fun genAuditsNoErrorsPlots() {

        val margins = listOf(.01, .015, .02, .03, .04, .05, .06, .07, .08, .10, .20)
        val cvrPercents = listOf(0.5, 0.75, 0.83, 0.90, 0.96)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val pollingGenerator = PollingSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, 0.0,
                parameters=mapOf("nruns" to nruns, "cat" to "poll")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, 0.0,
                clcaConfigIn= ClcaConfig(ClcaStrategyType.generalAdaptive, 0.0),
                parameters=mapOf("nruns" to nruns, "cat" to "clca")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            cvrPercents.forEach { cvrPercent ->
                val oneauditGenerator = OneAuditSingleRoundAuditTaskGenerator(
                    N, margin, 0.0, 0.0, cvrPercent, 0.0,
                    auditConfigIn = AuditConfig(
                        AuditType.ONEAUDIT, true,
                        oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.generalAdaptive)
                    ),
                    parameters=mapOf("nruns" to nruns, "cat" to "oneaudit-${(100 * cvrPercent).toInt()}%"),
                )
                tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator))
            }
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        regenPlots()
    }

    @Test
    fun regenPlots() {
        val subtitle = "Nc=${N} nruns=${nruns}"
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleType.Linear)
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleType.LogLinear)
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleType.LogLog)
    }

    @Test
    fun clcaNoErrors() {
        val name = "clcaNoErrors"
        val dirName = "$testdataDir/plots/samplesNeeded/$name"
        validateOutputDir(Path(dirName))
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        val nruns = 1

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val noerror = ClcaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, 0.0,
                parameters=mapOf("nruns" to 1, "cat" to "clca")
            )
            tasks.add(RepeatedWorkflowRunner(1, noerror))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        //     fun showSampleSizesVsMargin(dirName: String, name:String, subtitle: String, scaleType: ScaleType, catName: String) {
        val subtitle = "Nc=${N} nruns=${nruns}"
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleType.LogLog)
    }

    @Test
    fun clcaNoErrorsmaxLoss() {
        val name = "clcaNoErrorsmaxLoss"
        val dirName = "$testdataDir/plots/samplesNeeded/$name"
        validateOutputDir(Path(dirName))
        val margins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        val maxLoss = listOf(.70, .80, .90, 1.0)
        val nruns = 1

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        maxLoss.forEach { maxLoss ->
            margins.forEach { margin ->
                val noerror = ClcaSingleRoundAuditTaskGenerator(
                    N, margin, 0.0, 0.0, 0.0,
                    clcaConfigIn= ClcaConfig(maxLoss=maxLoss),
                    parameters = mapOf("nruns" to nruns, "cat" to maxLoss)
                )
                tasks.add(RepeatedWorkflowRunner(nruns, noerror))
            }
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        //     fun showSampleSizesVsMargin(dirName: String, name:String, subtitle: String, scaleType: ScaleType, catName: String) {
        val subtitle = "Nc=${N} nruns=${nruns}"
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleType.Linear, catName="maxLoss")
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleType.LogLinear, catName="maxLoss")
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleType.LogLog, catName="maxLoss")
    }

    @Test
    fun pollingNoErrorsOld() {
        val nruns = 100
        val name = "pollingNoErrors"
        val dir = "$testdataDir/plots/samplesNeeded/$name"
        validateOutputDir(Path(dir))
        val margins = listOf(.01, .015, .02, .03, .04, .05, .06, .07, .08, .10)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val nsamplesGenerator = PollingSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, 0.0,
                parameters=mapOf("nruns" to nruns)
            )
            tasks.add(RepeatedWorkflowRunner(nruns, nsamplesGenerator))
        }
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dir/${name}.csv")
        writer.writeResults(results)

        wrsPlotMultipleFields(
            titleS = "$name samples needed",
            subtitleS = "Nc=${N} nruns=${nruns}",
            results,
            "$dir/${name}Linear",
            "margin",
            "samplesNeeded",
            "legend",
            xfld = { it.margin },
            yfld = { cat: String, wr: WorkflowResult -> when (cat) {
                "needed" -> wr.samplesUsed
                "plusStdev" -> wr.samplesUsed + wr.usedStddev
                "minusStdev" -> wr.samplesUsed - wr.usedStddev
                else -> 0.0
            }},
            catflds = listOf("needed", "plusStdev", "minusStdev"),
        )

        wrsPlotMultipleFields(
            titleS = "$name samples needed",
            subtitleS = "Nc=${N} nruns=${nruns}",
            results,
            "$dir/${name}Log",
            "margin",
            "samplesNeeded",
            "legend",
            xfld = { it.margin },
            yfld = { cat: String, wr: WorkflowResult -> when (cat) {
                "needed" -> log10(wr.samplesUsed)
                "plusStdev" -> log10(wr.samplesUsed + wr.usedStddev)
                "minusStdev" -> log10(wr.samplesUsed - wr.usedStddev)
                else -> 0.0
            }},
            catflds = listOf("needed", "plusStdev", "minusStdev"),
        )
    }

    @Test
    fun pollingWithStdDev() {
        val nruns = 100
        val name = "pollingWithStdDev"
        val dir = "$testdataDir/plots/samplesNeeded/$name"
        validateOutputDir(Path(dir))
        val margins = listOf(.01, .015, .02, .03, .04, .05, .06, .07, .08, .10)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val nsamplesGenerator = PollingSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, 0.0,
                parameters=mapOf("nruns" to nruns, "cat" to "polling")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, nsamplesGenerator))
        }
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dir/${name}.csv")
        writer.writeResults(results)

        wrsErrorBars(
            titleS = "$name samples needed",
            subtitleS = "Nc=${N} nruns=${nruns}",
            wrs = results,
            xname = "margin", xfld = { it.margin },
            yname = "samplesNeeded", yfld = { it.samplesUsed },
            yupperFld = { it.samplesUsed + it.usedStddev },
            ylowerFld = { it.samplesUsed - it.usedStddev },
            // scaleType = ScaleType.LogLog, // only linear
            catName = "legend",
            catFld = { category(it) },
            writeFile = "$dir/${name}Linear",
        )
    }

}

fun showSampleSizesVsMargin(name: String, dirName: String, subtitle: String, yscale: ScaleType, catName: String = "auditType") {
    val io = WorkflowResultsIO("$dirName/${name}.csv")
    val data = io.readResults()
    wrsPlot(
        titleS = "$name samples needed",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}${yscale.name}",
        wrs = data,
        xname = "margin", xfld = { it.margin },
        yname = "samplesNeeded", yfld = { it.samplesUsed },
        catName = catName, catfld = { category(it) },
        scaleType = yscale
    )
}
