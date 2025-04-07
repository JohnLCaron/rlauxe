package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.dfn
import kotlin.math.log10
import kotlin.test.Test

class CompareAuditsNoErrors {
    val name = "AuditsNoErrors"
    val dirName = "/home/stormy/temp/audits/$name" // you need to make this directory first

    val nruns = 100  // number of times to run workflow
    val nsimEst = 10
    val N = 50000

    @Test
    fun genAuditsNoErrorsPlots() {
        val margins = listOf(.01, .015, .02, .03, .04, .05, .06, .07, .08, .10, .20)

        val cvrPercents = listOf(0.05, 0.5, .80, .95, .99)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val pollingGenerator = PollingSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, 0.0,
                nsimEst = nsimEst,
                parameters=mapOf("nruns" to nruns, "cat" to "poll")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, 0.0,
                nsimEst = nsimEst,
                clcaConfigIn= ClcaConfig(ClcaStrategyType.noerror, 0.0),
                parameters=mapOf("nruns" to nruns, "cat" to "clca")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            cvrPercents.forEach { cvrPercent ->
                val oneauditGenerator = OneAuditSingleRoundAuditTaskGenerator(
                    N, margin, 0.0, 0.0, cvrPercent, 0.0,
                    auditConfigIn = AuditConfig(
                        AuditType.ONEAUDIT, true, nsimEst = nsimEst,
                        oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.eta0Eps)
                    ),
                    parameters=mapOf("nruns" to nruns, "cat" to "oneaudit-${(100 * cvrPercent).toInt()}%"),
                )
                tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator))
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
        val subtitle = "Nc=${N} nruns=${nruns}"
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleType.Linear)
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleType.LogLinear)
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleType.LogLog)
    }

    fun showSampleSizesVsMargin(name: String, dirName: String, yscale: ScaleTypeOld) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showSampleSizesVsMargin(results, null, "auditType", yscale) {
            when (it.parameters["auditType"]) {
                1.0 -> "oneaudit ${dfn(it.Dparam("cvrPercent"), 3)}"
                2.0 -> "polling"
                3.0 -> "clca"
                else -> "unknown"
            }
        }
    }

    @Test
    fun clcaNoErrorsPlots() {
        val name = "clcaNoErrors"
        val dirName = "/home/stormy/temp/workflow/$name"
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val clcaGenerator = ClcaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, 0.0,
                clcaConfigIn= ClcaConfig(ClcaStrategyType.oracle, 0.0),
                parameters=mapOf("nruns" to nruns)
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        showSampleSizesVsMargin(name, dirName, ScaleTypeOld.Linear)
        showSampleSizesVsMargin(name, dirName, ScaleTypeOld.Log)
        showSampleSizesVsMargin(name, dirName, ScaleTypeOld.Pct)
    }

    @Test
    fun pollingNoErrorsPlots() {
        val name = "pollingNoErrors"
        val dir = "/home/stormy/temp/workflow/$name"
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

        val writer = WorkflowResultsIO("$dir/${name}.cvs")
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
    fun oaNoErrorsPlots() {
        val name = "oaNoErrors2"
        val dirName = "/home/stormy/temp/workflow/$name"
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        val fuzzPct = 0.0
        val cvrPercent = 0.95

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val oneauditGenerator1 = OneAuditSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, cvrPercent, mvrsFuzzPct=fuzzPct,
                mapOf("nruns" to nruns.toDouble(), "cat" to "default"),
                auditConfigIn = AuditConfig(
                    AuditType.ONEAUDIT, true, nsimEst = 100,
                    oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.default)
                )
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator1))
            val oneauditGenerator2 = OneAuditSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, cvrPercent, mvrsFuzzPct=fuzzPct,
                mapOf("nruns" to nruns.toDouble(), "cat" to "max99"),
                auditConfigIn = AuditConfig(
                    AuditType.ONEAUDIT, true, nsimEst = 100,
                    oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.bet99)
                )
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator2))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        val subtitle = "Nc=${N} nruns=${nruns} cvrPercent=$cvrPercent"
        showSampleSizesVsMarginByStrategy(name, dirName, subtitle, ScaleTypeOld.Linear)
        showSampleSizesVsMarginByStrategy(name, dirName, subtitle, ScaleTypeOld.Log)
        showSampleSizesVsMarginByStrategy(name, dirName, subtitle, ScaleTypeOld.Pct)
        showFailuresVsMarginByStrategy(name, dirName, subtitle=subtitle)
    }

    fun showSampleSizesVsMarginByStrategy(name: String, dirName: String, subtitle: String, yscale: ScaleTypeOld) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showSampleSizesVsMargin(results, subtitle, "strategy", yscale) { category(it) }
    }

    fun showFailuresVsMarginByStrategy(name: String, dirName: String, subtitle: String) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showFailuresVsMargin(results, subtitle, "strategy") { category(it) }
    }

}


fun showSampleSizesVsMargin(name: String, dirName: String, subtitle: String, yscale: ScaleType) {
    val io = WorkflowResultsIO("$dirName/${name}.cvs")
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