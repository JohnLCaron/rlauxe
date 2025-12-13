package org.cryptobiotic.rlauxe.fuzz

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class PlotClcaFuzz {
    val name = "clcaByMvrFuzzPct"
    val dirName = "$testdataDir/fuzz"

    // Used in docs

    // redo plots/samples/ComparisonFuzzed.html
    @Test
    fun clcaSamplesVsMarginByFuzzPct() {
        val N = 10000
        val nruns = 100  // number of times to run workflow

        val fuzzPcts = listOf(0.0, 0.001, 0.0025, .005, .01, .02, .05)
        val margins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        // do all margins and sample sizes
        val config = AuditConfig(AuditType.CLCA, true, nsimEst = 100)

        val stopwatch = Stopwatch()
        val tasks = mutableListOf<RepeatedWorkflowRunner>()
        fuzzPcts.forEach { mvrsFuzzPct ->
            margins.forEach { margin ->
                val clcaGenerator = ClcaSingleRoundAuditTaskGenerator(
                    N, margin, 0.0, 0.0, mvrsFuzzPct,
                    parameters = mapOf("nruns" to nruns, "fuzzPct" to mvrsFuzzPct),
                    config = config,
                )
                tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))
            }
        }
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        val subtitle = "Nc=${N} nruns=${nruns}"
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.LogLog)
        showFailuresVsMargin(dirName, name, subtitle)
    }
}

fun showSampleSizesVsMargin(dirName: String, name:String, subtitle: String, scaleType: ScaleType) {
    val io = WorkflowResultsIO("$dirName/${name}.csv")
    val data = io.readResults()
    wrsPlot(
        titleS = "$name samples needed",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}${scaleType.name}",
        wrs = data,
        xname = "margin", xfld = { it.margin },
        yname = "samplesNeeded", yfld = { it.samplesUsed },
        catName = "mvrFuzzPct", catfld = { categoryFuzzPct(it) },
        scaleType = scaleType
    )
}

fun showFailuresVsMargin(dirName: String, name:String, subtitle: String, ) {
    val io = WorkflowResultsIO("$dirName/${name}.csv")
    val data = io.readResults()

    wrsPlot(
        titleS = "$name failurePct",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}Failures",
        wrs = data,
        xname = "margin", xfld = { it.margin },
        yname = "failurePct", yfld = { it.failPct },
        catName = "mvrFuzzPct", catfld = { categoryFuzzPct(it) },
    )
}