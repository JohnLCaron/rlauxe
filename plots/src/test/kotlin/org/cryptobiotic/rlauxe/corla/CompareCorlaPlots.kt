package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ClcaStrategyType
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

import org.jetbrains.kotlinx.kandy.util.color.Color

class CompareCorlaPlots {
    val nruns = 100
    val nsimEst = 100
    val name = "corlaWithPhantoms2"
    val dirName = "/home/stormy/rla/corla/$name"
    val N = 100000

    @Test
    fun corlaComparePlots() {
        val margins =
            listOf(.003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .10)
        val config = AuditConfig(AuditType.CLCA, true, nsimEst = nsimEst)

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            var fuzzPct = .0
            var phantomPct = 0.0
            var name = "000"

            val corla0 = CorlaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, phantomPct, fuzzPct,
                parameters = mapOf("nruns" to nruns, "cat" to "corla.$name"),
                auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.noerror))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, corla0))

            val clca0 = ClcaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, phantomPct, fuzzPct,
                parameters = mapOf("nruns" to nruns, "cat" to "rlauxe.$name"),
                config = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.phantoms))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clca0))

            phantomPct = 0.002
            name = "002"
            val corla1 = CorlaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, phantomPct, fuzzPct,
                parameters = mapOf("nruns" to nruns, "cat" to "corla.$name"),
                auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.noerror))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, corla1))

            val clca1 = ClcaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, phantomPct, fuzzPct,
                parameters = mapOf("nruns" to nruns, "cat" to "rlauxe.$name"),
                config = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.phantoms))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clca1))

            phantomPct = 0.005
            name = "005"
            val corla2 = CorlaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, phantomPct, fuzzPct,
                parameters = mapOf("nruns" to nruns, "cat" to "corla.$name"),
                auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.noerror))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, corla2))

            val clca2 = ClcaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, phantomPct, fuzzPct,
                parameters = mapOf("nruns" to nruns, "cat" to "rlauxe.$name"),
                config = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.phantoms))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clca2))

            phantomPct = 0.01
            name = "01"
            val corla3 = CorlaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, phantomPct, fuzzPct,
                parameters = mapOf("nruns" to nruns, "cat" to "corla.$name"),
                auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.noerror))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, corla3))

            val clca3= ClcaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, phantomPct, fuzzPct,
                parameters = mapOf("nruns" to nruns, "cat" to "rlauxe.$name"),
                config = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.phantoms))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clca3))

            phantomPct = 0.02
            name = "02"
            val corla4 = CorlaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, phantomPct, fuzzPct,
                parameters = mapOf("nruns" to nruns, "cat" to "corla.$name"),
                auditConfig = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.noerror))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, corla4))

            val clca4 = ClcaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, phantomPct, fuzzPct,
                parameters = mapOf("nruns" to nruns, "cat" to "rlauxe.$name"),
                config = config.copy(clcaConfig = ClcaConfig(ClcaStrategyType.phantoms))
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clca4))
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
        showSampleSizesVsTheta(name, dirName, subtitle, ScaleType.Linear)
        showSampleSizesVsTheta(name, dirName, subtitle, ScaleType.LogLinear)
        showSampleSizesVsTheta(name, dirName, subtitle, ScaleType.LogLog)
        //showFailuresVsMargin(name, dirName, subtitle)
    }
}

fun showSampleSizesVsTheta(name:String, dirName: String, subtitle: String, scaleType: ScaleType) {
    val io = WorkflowResultsIO("$dirName/${name}.csv")
    val data = io.readResults()
    wrsPlot(
        titleS = "Corla vs Rlauxe",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}${scaleType.name}",
        wrs = data,
        xname = "true margin", xfld = { it.mvrMargin},
        yname = "samplesNeeded", yfld = { it.samplesUsed },
        catName = "audit.phantomPct", catfld = { category(it) },
        scaleType = scaleType,
        colorChoices = { colorChoices(it) },
    )
}

fun showFailuresVsMargin(name:String, dirName: String, subtitle: String) {
    val io = WorkflowResultsIO("$dirName/${name}.csv")
    val data = io.readResults()
    wrsPlot(
        titleS = "$name failurePct",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}Failure",
        wrs = data,
        xname = "true margin", xfld = { it.mvrMargin },
        yname = "failPct", yfld = { it.failPct },
        catName = "flipPct", catfld = { category(it) },
        colorChoices = { colorChoices(it) },
    )
}

fun colorChoices(cats: Set<String>): Array<Pair<String, Color>> {
    val result = mutableListOf<Pair<String, Color>>()
    cats.forEach { cat ->
        val color = if (cat.contains("corla")) Color.ORANGE else Color.LIGHT_GREEN
        result.add( Pair(cat, color))
    }
    return result.toTypedArray()
}
