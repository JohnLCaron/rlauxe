package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.io.path.Path
import kotlin.test.Test

import kotlin.math.pow

class AuditsWithErrors {
    val nruns = 1000
    val N = 100000

    @Test
    fun clcaFuzzByMargin() {
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        val fuzzPcts = listOf(.00, .001, .0025, .005, .0075, .01)

        val stopwatch = Stopwatch()
        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        for (margin in margins) {
            fuzzPcts.forEach { fuzzPct ->
                val fuzzTask = ClcaSingleRoundAuditTaskGenerator(
                    N, margin, 0.0, 0.0, fuzzPct,
                    parameters=mapOf("nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct, "cat" to fuzzPct)
                )
                tasks.add(RepeatedWorkflowRunner(nruns, fuzzTask))
            }
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val name = "clcaFuzzByMargin"
        val dirName = "$testdataDir/plots/samplesNeeded/$name"
        validateOutputDir(Path(dirName))
        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)
    }

    @Test
    fun showFuzzByMargin() {
        val name = "clcaFuzzByMargin"
        val dirName = "$testdataDir/plots/samplesNeeded/clcaFuzzByMargin"
        val subtitle = "Nc=${N} nruns=${nruns}"
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleType.LogLog, catName="fuzzPct")
    }

    @Test
    fun clcaFuzzByMarginStddev() {
        val name3 = "clcaFuzzByMarginStddev"
        val dirName = "$testdataDir/plots/samplesNeeded/clcaFuzzByMargin"
        val dataFilename = "$dirName/clcaFuzzByMargin.csv"
        val subtitle3 = "Nc=${N} nruns=${nruns}"
        showStddevVsMargin(dataFilename, name3, dirName, subtitle3, ScaleType.Linear, catName="fuzzPct")
        showStddevVsMargin(dataFilename, name3, dirName, subtitle3, ScaleType.LogLinear, catName="fuzzPct")
        showStddevVsMargin(dataFilename, name3, dirName, subtitle3, ScaleType.LogLog, catName="fuzzPct")
    }

    @Test
    fun clcaFuzzStddevVsSamplesNeeded() {
        val name3 = "clcaStddevVsSamplesNeeded"
        val dirName = "$testdataDir/plots/samplesNeeded/clcaFuzzByMargin"
        val dataFilename = "$dirName/clcaFuzzByMargin.csv"
        val subtitle3 = "Nc=${N} nruns=${nruns}"
        showStddevVsSamplesNeeded(dataFilename, name3, dirName, subtitle3, ScaleType.Linear, catName="fuzzPct")
        showStddevVsSamplesNeeded(dataFilename, name3, dirName, subtitle3, ScaleType.LogLinear, catName="fuzzPct")
        showStddevVsSamplesNeeded(dataFilename, name3, dirName, subtitle3, ScaleType.LogLog, catName="fuzzPct")
    }

    @Test
    fun clcaFuzzStddevVsSamplesModel() {
        val name3 = "clcaStddevVsSamplesModeled"
        val dirName = "$testdataDir/plots/samplesNeeded/clcaFuzzByMargin"
        val dataFileName = "$dirName/clcaFuzzByMargin.csv"
        val subtitle3 = "Nc=${N} nruns=${nruns}"
        showStddevVsSamplesModel(dataFileName, name3, dirName, subtitle3, ScaleType.Linear)
        showStddevVsSamplesModel(dataFileName, name3, dirName, subtitle3, ScaleType.LogLinear)
        showStddevVsSamplesModel(dataFileName, name3, dirName, subtitle3, ScaleType.LogLog)
    }

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

////////////////////////////////////////////////////////////////////////////////////////////////////////////////

fun showSampleSizesVsFuzzPct(
    dirName: String, name: String, subtitle: String, scaleType: ScaleType,
    catName: String, catfld: ((WorkflowResult) -> String) = { category(it) },
) {
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

fun sampleSizesVsFuzzPctStdDev(
    dirName: String, name: String, subtitle: String,
    catName: String, catfld: ((WorkflowResult) -> String) = { category(it) },
) {
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

fun sampleSizesVsMarginWithErrorBars(
    dirName: String, name: String, subtitle: String,
    catName: String, catfld: ((WorkflowResult) -> String) = { category(it) },
) {
    val io = WorkflowResultsIO("$dirName/${name}.csv")
    val data = io.readResults()

    wrsErrorBars(
        titleS = "$name samples needed",
        subtitleS = subtitle,
        wrs = data,
        xname = "margin", xfld = { it.Dparam("margin") },
        yname = "samplesNeeded", yfld = { it.samplesUsed },
        yupperFld = { it.samplesUsed + it.usedStddev },
        ylowerFld = { it.samplesUsed - it.usedStddev },
        // scaleType = ScaleType.LogLog, // only linear
        catName = catName, catFld = catfld,
        writeFile = "$dirName/${name}Linear",
    )
}


// fun showStddevVsMargin(name: String, dirName: String, subtitle: String, yscale: ScaleType, catName: String) {
fun showStddevVsMargin(dataFile: String, name: String, dirName: String, subtitle: String, yscale: ScaleType, catName: String) {
    val io = WorkflowResultsIO(dataFile)
    val data = io.readResults()
    wrsPlot(
        titleS = "$name samples needed",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}${yscale.name}",
        wrs = data,
        xname = "margin", xfld = { it.margin },
        yname = "stddevSamples", yfld = { it.usedStddev },
        catName = catName, catfld = { category(it) },
        scaleType = yscale
    )
}

fun showStddevVsSamplesNeeded(dataFile: String, name: String, dirName: String, subtitle: String, yscale: ScaleType, catName: String) {
    val io = WorkflowResultsIO(dataFile)
    val data = io.readResults()
    wrsPlot(
        titleS = "$name stddev vs samplesNeeded",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}${yscale.name}",
        wrs = data,
        xname = "nsamples", xfld = { it.samplesUsed },
        yname = "stddev", yfld = { it.usedStddev },
        catName = catName, catfld = { category(it) },
        scaleType = yscale
    )
}

fun showStddevVsSamplesModel(dataFile: String, name: String, dirName: String, subtitle: String, yscale: ScaleType) {
    val io = WorkflowResultsIO(dataFile)
    val data = io.readResults()

    wrsPlotMultipleFields(
        titleS = "stddev vs samplesNeeded with model",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}${yscale.name}",
        wrs = data,
        xname = "nsamples", xfld = { it.samplesUsed },
        yname = "stddev",
        yfld = { cat: String, wr: WorkflowResult ->
            when (cat) {
                "stddev" -> wr.usedStddev
                "linearFit" -> linearModel(wr.samplesUsed)
                // "logFit" -> logModel(wr.samplesUsed)
                else -> 0.0
            }
        },
        catName = "legend",
        catflds = listOf("stddev", "linearFit"),  // "logFit"),
        scaleType = yscale,
    )
}

fun logModel(nsamples: Double) = .135 * nsamples.pow(.586)
fun linearModel(nsamples: Double) = -23.85 + .586 * nsamples

