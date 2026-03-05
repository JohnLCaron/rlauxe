package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import org.jetbrains.kotlinx.kandy.util.color.Color
import kotlin.io.path.Path
import kotlin.test.Test

class CompareCorlaAndRlauxe {
    val nruns = 100
    val name = "compareCorlaAndRlauxe"
    val dirName = "$testdataDir/plots/corla/$name"
    val N = 100000
    val risk = .03

    @Test
    fun compareCorlaAndRlauxe() {
        val margins =
            listOf(.005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .10)
        val fuzzPcts = listOf(.00, .001, .0025, .005, .0075, .01)

        val config = AuditConfig(AuditType.CLCA, riskLimit = risk)

        val stopwatch = Stopwatch()
        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        for (margin in margins) {
            fuzzPcts.forEach { fuzzPct ->
                val corlaTask = CorlaSingleRoundAuditTaskGenerator(
                    N, margin, 0.0, 0.0, fuzzPct,
                    parameters = mapOf("nruns" to nruns, "cat" to "corla$fuzzPct"),
                    auditConfig = config,
                )
                tasks.add(RepeatedWorkflowRunner(nruns, corlaTask))

                val rlauxeTask = ClcaSingleRoundAuditTaskGenerator(
                    N, margin, 0.0, 0.0, fuzzPct,
                    parameters=mapOf("nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct, "cat" to "rlauxe$fuzzPct"),
                    config = config,
                )
                tasks.add(RepeatedWorkflowRunner(nruns, rlauxeTask))
            }
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks, nthreads=20)
        println("that took ${stopwatch.took()}")

        validateOutputDir(Path(dirName))
        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        regenPlots()
    }

    @Test
    fun regenPlots() {
        val subtitle = "Nc=${N} nruns=${nruns} riskLimit=$risk"
        showSampleSizes(name, dirName, subtitle, ScaleType.Linear)
        showSampleSizes(name, dirName, subtitle, ScaleType.LogLinear)
        showSampleSizes(name, dirName, subtitle, ScaleType.LogLog)
    }
}

fun showSampleSizes(name:String, dirName: String, subtitle: String, scaleType: ScaleType) {
    val io = WorkflowResultsIO("$dirName/${name}.csv")
    val data = io.readResults()
    wrsPlot(
        titleS = "Corla vs Rlauxe",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}${scaleType.name}",
        wrs = data,
        xname = "true margin", xfld = { it.mvrMargin},
        yname = "samplesNeeded", yfld = { it.samplesUsed },
        catName = "type.fuzz", catfld = { category(it) },
        scaleType = scaleType,
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