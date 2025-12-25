package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.text.toDouble

class AuditsWithErrors {

    @Test
    fun compareAuditsWithFuzz() {
        val nruns = 100
        val name = "margin2WithStdDev"
        val dirName = "$testdataDir/plots/samplesNeeded/$name"
        val N = 50000
        val margin = .02

        val fuzzPcts = listOf(.00, .001, .0025, .005, .0075, .01, .02, .03, .05)
        val cvrPercents = listOf(0.75, 0.83, 0.90, 0.96)

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        fuzzPcts.forEach { fuzzPct ->

            val pollingGenerator = PollingSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, mvrsFuzzPct=fuzzPct,
                parameters=mapOf("nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct, "cat" to "polling")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, mvrsFuzzPct=fuzzPct,
                clcaConfigIn= ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct),
                parameters=mapOf("nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct, "cat" to "clca")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            /*
            cvrPercents.forEach { cvrPercent ->
                val oneauditGenerator = OneAuditSingleRoundAuditTaskGenerator(
                    N, margin, 0.0, 0.0, cvrPercent, mvrsFuzzPct=fuzzPct,
                    auditConfigIn = AuditConfig(
                        AuditType.ONEAUDIT, true,
                        oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.eta0Eps)
                    ),
                    parameters=mapOf("nruns" to nruns, "fuzzPct" to fuzzPct, "cvrPercent" to "${(100 * cvrPercent).toInt()}%"),
                )
                tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator))
            }

            val raireGenerator = RaireSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, mvrsFuzzPct=fuzzPct,
                clcaConfigIn= ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct),
                parameters=mapOf("nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct)
            )
            tasks.add(RepeatedWorkflowRunner(nruns, raireGenerator)) */
        }

        // run tasks concurrently and average the results
        println("---genAuditWithFuzzPlots running ${tasks.size} tasks nruns= $nruns")
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        validateOutputDir(Path(dirName))
        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)
        val subtitle = "margin=${margin} Nc=${N} nruns=${nruns}"
        sampleSizesVsFuzzPctStdDev(dirName, name, subtitle, catName="auditType", catfld= { category(it) })

        /*
                val subtitle = "margin=${margin} Nc=${N} nruns=${nruns}"
        // save
        // showSampleSizesVsFuzzPct(dirName, name, subtitle, ScaleType.Linear, catName="auditType", catfld= { compareCategories(it) })
        // showSampleSizesVsFuzzPct(dirName, name, subtitle, ScaleType.LogLinear, catName="auditType", catfld= { compareCategories(it) })
        // showSampleSizesVsFuzzPct(dirName, name, subtitle, ScaleType.LogLog, catName="auditType", catfld= { compareCategories(it) })

        showSampleSizesVsFuzzPct(dirName, name, subtitle, ScaleType.Linear, catName="auditType", catfld= { category(it) })
        showSampleSizesVsFuzzPct(dirName, name, subtitle, ScaleType.LogLinear, catName="auditType", catfld= { category(it) })
        showSampleSizesVsFuzzPct(dirName, name, subtitle, ScaleType.LogLog, catName="auditType", catfld= { category(it) })
         */
    }


    @Test
    fun clcaAuditsWithFuzz() {
        val nruns = 100
        val name = "clcaAuditsWithFuzz"
        val dirName = "$testdataDir/plots/samplesNeeded/$name"

        val N = 50000
        val margins = listOf(.01, .02, .04)
        val fuzzPcts = listOf(.00, .001, .0025, .005, .0075, .01)

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        for (margin in margins) {
            fuzzPcts.forEach { fuzzPct ->
                val clcaGenerator = ClcaSingleRoundAuditTaskGenerator(
                    N, margin, 0.0, 0.0, mvrsFuzzPct = fuzzPct,
                    clcaConfigIn = ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct),
                    parameters = mapOf("nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct, "cat" to margin)
                )
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))
            }
        }

        // run tasks concurrently and average the results
        println("---clcaAuditsWithFuzz running ${tasks.size} tasks nruns= $nruns")
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        validateOutputDir(Path(dirName))
        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        val subtitle = "Nc=${N} nruns=${nruns}"
        sampleSizesVsFuzzPctStdDev(dirName, name, subtitle, catName="margin", catfld= { category(it) })
    }
}

fun compareCategories(wr: WorkflowResult): String {
    return when (wr.Dparam("auditType")) {
        1.0 -> "oneaudit-${wr.parameters["cvrPercent"]}"
        2.0 -> "polling"
        3.0 -> "clca"
        4.0 -> "raire"
        else -> "unknown"
    }
}

fun showSampleSizesVsFuzzPct(dirName: String, name:String, subtitle: String, scaleType: ScaleType,
                             catName: String, catfld: ((WorkflowResult) -> String) = { category(it) } ) {
    val io = WorkflowResultsIO("$dirName/${name}.csv")
    val data = io.readResults()
    wrsPlot(
        titleS = "$name samples needed",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}${scaleType.name}",
        wrs = data,
        xname = "fuzzPct", xfld = { it.Dparam("fuzzPct") },
        yname = "samplesNeeded", yfld = { it.samplesUsed },
        catName = catName, catfld = catfld,
        scaleType = scaleType
    )
}

fun sampleSizesVsFuzzPctStdDev(dirName: String, name:String, subtitle: String,
                             catName: String, catfld: ((WorkflowResult) -> String) = { category(it) } ) {
    val io = WorkflowResultsIO("$dirName/${name}.csv")
    val data = io.readResults()

    wrsErrorBars(
        titleS = "$name samples needed",
        subtitleS = subtitle,
        wrs = data,
        xname = "fuzzPct", xfld = { it.Dparam("fuzzPct") },
        yname = "samplesNeeded", yfld = { it.samplesUsed },
        yupperFld = { it.samplesUsed + it.usedStddev },
        ylowerFld = { it.samplesUsed - it.usedStddev },
        // scaleType = ScaleType.LogLog, // only linear
        catName = catName, catFld = catfld,
        writeFile = "$dirName/${name}Linear",
    )
}
