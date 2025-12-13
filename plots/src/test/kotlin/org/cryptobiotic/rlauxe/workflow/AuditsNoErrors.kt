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
    val dirName = "$testdataDir/plots/oneaudit4/$name" // you need to make this directory first

    val nruns = 100  // number of times to run workflow
    val nsimEst = 10
    val N = 50000

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
                        AuditType.ONEAUDIT, true, nsimEst = nsimEst,
                        oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.optimalComparison)
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

    fun showSampleSizesVsMargin(name: String, dirName: String, yscale: ScaleTypeOld) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotterOld(dirName, name)
        plotter.showSampleSizesVsMargin(results, null, "auditType", yscale) { category(it) }


        /* {
            when (it.parameters["auditType"]) {
                1.0 -> "oneaudit ${dfn(it.Dparam("cvrPercent"), 3)}"
                2.0 -> "polling"
                3.0 -> "clca"
                else -> "unknown"
            }
        } */
    }

    @Test
    fun clcaNoErrorsPlots() {
        val name = "clcaNoErrors"
        val dirName = "$testdataDir/plots/workflows/$name"
        validateOutputDir(Path(dirName))
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val generalAdaptive = ClcaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, 0.0,
                clcaConfigIn= ClcaConfig(ClcaStrategyType.generalAdaptive, 0.0),
                parameters=mapOf("nruns" to nruns, "cat" to "generalAdaptive")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, generalAdaptive))

            val noerror = ClcaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, 0.0,
                parameters=mapOf("nruns" to nruns, "cat" to "adaptive")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, noerror))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        showSampleSizesVsMargin(name, dirName, ScaleTypeOld.Linear)
    }

    @Test
    fun pollingNoErrorsPlots() {
        val name = "pollingNoErrors"
        val dir = "$testdataDir/plots/workflows/$name"
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

}


fun showSampleSizesVsMargin(name: String, dirName: String, subtitle: String, yscale: ScaleType) {
    val io = WorkflowResultsIO("$dirName/${name}.csv")
    val data = io.readResults()
    wrsPlot(
        titleS = "$name samples needed",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}${yscale.name}",
        wrs = data,
        xname = "true margin", xfld = { it.margin },
        yname = "samplesNeeded", yfld = { it.samplesUsed },
        catName = "auditType", catfld = { category(it) },
        scaleType = yscale
    )
}