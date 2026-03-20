package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.workflow.*
import org.jetbrains.kotlinx.kandy.util.color.Color
import kotlin.io.path.Path
import kotlin.test.Test

class CompareCorlaAndRlauxe {
    val nruns = 10
    val N = 100000
    val risk = .03

    @Test
    fun compareCorlaAndRlauxe() {
        val name = "compareCorlaAndRlauxe"
        val dirName = "$testdataDir/plots/corla/$name"

        val margins = listOf(.005, .0075, .01, .015, .02,)
            // listOf(.005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .10)

        val fuzzPcts = listOf(.001, .0025, .005) // listOf(.00, .001, .0025, .005, .0075, .01)

        val config = Config.from(AuditType.CLCA, riskLimit = risk)

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
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println("that took ${stopwatch.took()}")

        validateOutputDir(Path(dirName))
        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        val subtitle = "Nc=${N} nruns=${nruns} riskLimit=$risk"
        showSampleSizes(name, dirName, subtitle, ScaleType.Linear)
        showSampleSizes(name, dirName, subtitle, ScaleType.LogLinear)
        showSampleSizes(name, dirName, subtitle, ScaleType.LogLog)
        sampleSizesVsMarginStdDev(name=name, dirName=dirName, subtitle=subtitle)
        sampleSizesVsMarginScatter(name=name, dirName=dirName, subtitle=subtitle)
    }

    @Test
    fun genAuditWithPhantomsPlots() {
        val name = "compareCorlaAndRlauxeWithPhantoms"
        val dirName = "$testdataDir/plots/corla/$name"

        val fuzzPct = 0.0
        val margin = .02
        val config = Config.from(AuditType.CLCA, riskLimit = risk)

        val phantoms = listOf(.00, .001, .002, .005, .01, .02)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        phantoms.forEach { phantomPct ->
            val corlaTask = CorlaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, phantomPct, mvrsFuzzPct=fuzzPct,
                parameters = mapOf("nruns" to nruns, "phantom" to phantomPct, "cat" to "corla"),
                auditConfig = config,
            )
            tasks.add(RepeatedWorkflowRunner(nruns, corlaTask))

            val rlauxeTask = ClcaSingleRoundAuditTaskGenerator(
                N, margin, 0.0, phantomPct, mvrsFuzzPct=fuzzPct,
                parameters=mapOf("nruns" to nruns.toDouble(), "phantom" to phantomPct, "cat" to "clca"),
                config = config,
            )
            tasks.add(RepeatedWorkflowRunner(nruns, rlauxeTask))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        validateOutputDir(Path(dirName))
        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        val subtitle = "margin=${df(margin)} Nc=${N} nruns=${nruns} mvrFuzz=${fuzzPct}"
        // showSampleSizesVsPhantomPct in AuditsWithPhantoms
        showSampleSizesVsPhantomPct(dirName, name, subtitle, ScaleType.Linear, catName="auditType")
        showSampleSizesVsPhantomPct(dirName, name, subtitle, ScaleType.LogLinear, catName="auditType")
        showSampleSizesVsPhantomPct(dirName, name, subtitle, ScaleType.LogLog, catName="auditType")
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
        xname = "margin", xfld = { it.mvrMargin },
        yname = "samplesNeeded", yfld = { it.samplesUsed },
        catName = "type.fuzz", catfld = { category(it) },
        scaleType = scaleType,
        colorChoices = { colorChoices(it) },
        )
}

fun sampleSizesVsMarginStdDev(
    dirName: String, name: String, subtitle: String,
) {
    val io = WorkflowResultsIO("$dirName/${name}.csv")
    val data = io.readResults()

    wrsErrorBars(
        titleS = "$name samples needed",
        subtitleS = subtitle,
        wrs = data,
        xname = "margin", xfld = { it.mvrMargin },
        yname = "samplesNeeded", yfld = { it.samplesUsed },
        yupperFld = { it.samplesUsed + it.usedStddev },
        ylowerFld = { it.samplesUsed - it.usedStddev },
        // scaleType = ScaleType.LogLog, // only linear
        catName = "type.fuzz", catFld = { category(it) },
        writeFile = "$dirName/${name}Stddev",
        colorChoices = { colorChoices(it) },
        )
}

fun sampleSizesVsMarginScatter(
    dirName: String, name: String, subtitle: String,
) {
    val io = WorkflowResultsIO("$dirName/${name}.csv")
    val data = io.readResults()

    // fun wrsScatterPlot(
    //    titleS: String,
    //    subtitleS: String,
    //    wrs: List<WorkflowResult>,
    //    writeFile: String, // no suffix
    //    xname: String, yname: String, runName: String,
    //    xfld: (WorkflowResult) -> Double,
    //    yfld: (WorkflowResult) -> Double,
    //    runFld: (WorkflowResult) -> String,
    //    scaleType: ScaleType = ScaleType.Linear,
    //    colorChoices: ((Set<String>) -> Array<Pair<String, Color>>)? = null
    //)
    wrsScatterPlot(
        titleS = "$name samples needed",
        subtitleS = subtitle,
        wrs = data,
        xname = "margin", xfld = { it.mvrMargin },
        yname = "samplesNeeded", yfld = { it.samplesUsed },
        // scaleType = ScaleType.LogLog, // only linear
        runName = "type.fuzz", runFld = { category(it) },
        colorChoices = { colorChoices(it) },
        writeFile = "$dirName/${name}Scatter",
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