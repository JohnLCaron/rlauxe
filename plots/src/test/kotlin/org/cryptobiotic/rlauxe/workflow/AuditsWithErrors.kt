package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test

class AuditsWithErrors {
    val nruns = 100
    val nsimEst = 10
    val name = "AuditsWithErrors2"
    val dirName = "/home/stormy/rla/plots/oneaudit4/$name"
    val N = 50000
    val margin = .02

    @Test
    fun genAuditWithFuzzPlots() {
        val fuzzPcts = listOf(.00, .001, .0025, .005, .0075, .01, .02, .03, .05)
        val cvrPercents = listOf(0.5, 0.75, 0.83, 0.90, 0.96)

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        fuzzPcts.forEach { fuzzPct ->
            val pollingGenerator = PollingSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, fuzzPct, nsimEst=nsimEst,
                parameters=mapOf("nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct)
            )
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, fuzzPct, nsimEst=nsimEst,
                clcaConfigIn= ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct),
                parameters=mapOf("nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct, "cat" to "clca")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            cvrPercents.forEach { cvrPercent ->
                val oneauditGenerator = OneAuditSingleRoundAuditTaskGenerator(
                    N, margin, 0.0, 0.0, cvrPercent, mvrsFuzzPct=fuzzPct,
                    auditConfigIn = AuditConfig(
                        AuditType.ONEAUDIT, true, nsimEst = nsimEst,
                        oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.eta0Eps)
                    ),
                    parameters=mapOf("nruns" to nruns, "fuzzPct" to fuzzPct, "cvrPercent" to "${(100 * cvrPercent).toInt()}%"),
                )
                tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator))
            }

            val raireGenerator = RaireSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, fuzzPct, nsimEst=nsimEst,
                clcaConfigIn= ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct),
                parameters=mapOf("nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct)
            )
            tasks.add(RepeatedWorkflowRunner(nruns, raireGenerator))
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
        val subtitle = "margin=${margin} Nc=${N} nruns=${nruns}"
        showSampleSizesVsFuzzPct(dirName, name, subtitle, ScaleType.Linear, catName="auditType", catfld= { compareCategories(it) })
        showSampleSizesVsFuzzPct(dirName, name, subtitle, ScaleType.LogLinear, catName="auditType", catfld= { compareCategories(it) })
        showSampleSizesVsFuzzPct(dirName, name, subtitle, ScaleType.LogLog, catName="auditType", catfld= { compareCategories(it) })
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
                             catName: String, catfld: ((WorkflowResult) -> String) = { it -> category(it) } ) {
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