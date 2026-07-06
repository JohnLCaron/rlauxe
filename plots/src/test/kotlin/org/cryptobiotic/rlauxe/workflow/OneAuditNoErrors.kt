package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.util.ConcurrentTask
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.io.path.Path
import kotlin.math.nextUp
import kotlin.test.Test

class OneAuditNoErrors {
    val nruns = 100
    val N = 500000
    val name = "OneAuditNoErrors3"
    val dirName = "$testdataDir/plots/oneaudit/$name"
    val datafileName = "$dirName/${name}.csv"

    @Test
    fun oaNoErrorsPlots() {
        val margins =
            listOf(.005, .01, .015, .02, .03, .04, .05, .06, .07, .08, .10, .20)
        // original val cvrPercents = listOf(0.50, 0.75, 0.83, 0.90, 0.96)
        val cvrPercents = listOf(0.01, 0.10, 0.25, 0.50, 0.75, 0.90)

        val stopwatch = Stopwatch()

        // does the order get preserved ?? NO
        val tasks = mutableListOf<ConcurrentTask<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val pollingGenerator = PollingSingleRoundAuditTaskGenerator(
                N, margin, 0.0, 0.0, 0.0,
                parameters=mapOf("nruns" to nruns, "cat" to "poll")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaGenerator = ClcaSingleRoundAuditTaskGenerator(
                Nc = N, margin=margin, underVotePct=0.0, phantomPct=0.0, mvrsFuzzPct=0.0,
                parameters=mapOf("nruns" to nruns, "cat" to "clca")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            cvrPercents.forEach { cvrPercent ->
                val oneauditGenerator = OneAuditSingleRoundAuditTaskGenerator(
                    N, margin, 0.0, 0.0, cvrPercent, 0.0,
                    auditConfigIn = Config.from(AuditType.ONEAUDIT),
                    parameters=mapOf("nruns" to nruns, "cat" to "oneaudit-${(100 * cvrPercent).toInt()}%"),
                )
                tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator))
            }
        }
        val nthreads=100
        println("start ${tasks.size} tasks with $nthreads threads")
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks, nthreads=nthreads)
        println(stopwatch.took())

        val dirName = "$testdataDir/plots/oneaudit/$name"

        validateOutputDir(Path(dirName))
        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        regenPlots(name, dirName)
    }

    @Test
    fun regenPlots() {
        regenPlots("OneAuditNoErrors4", dirName)
    }

    class CatOrdering: Comparator<String> {
        override fun compare(o1: String, o2: String): Int {
            return when {
                (o1 == "poll") -> -1 // poll is first
                (o2 == "poll") -> 1 //
                (o1 == "clca") -> 1 // clca is last
                (o2 == "clca") -> -1 //
                else -> o1.compareTo(o2) // oneaudits are alphabetic
            }
        }
    }

    fun regenPlots(name: String, dirName:String) {
        val subtitle = "Nc=${N} nruns=${nruns}"
        showSampleSizesVsMargin(datafileName, name, dirName, subtitle, ScaleType.Linear, catOrdering=CatOrdering())
        // showSampleSizesVsMargin(datafileName, name, dirName, subtitle, ScaleType.LogLinear, catOrdering=CatOrdering())
        //showSampleSizesVsMargin(name, dirName, subtitle, ScaleType.LogLog)
    }

    @Test
    fun oa3StdDev() {
        val name = "OA3StdDev"
        val subtitle3 = "Nc=${N} nruns=${nruns}"
        // fun showStddevVsMargin(dataFile: String, name: String, dirName: String, subtitle: String, yscale: ScaleType, catName: String) {
        showStddevVsMargin(datafileName, name, dirName, subtitle3, ScaleType.LogLog, catName="auditType")
    }

    @Test
    fun oaStddevVsSamplesNeeded() {
        val name3 = "oa3StdDevVsSamplesNeeded"
        val subtitle3 = "Nc=${N} nruns=${nruns}"
        showStddevVsSamplesNoClca(datafileName, name3, dirName, subtitle3, ScaleType.LogLog, catName="audit")
    }

    fun showStddevVsSamplesNoClca(dataFile: String, name: String, dirName: String, subtitle: String, yscale: ScaleType, catName: String) {
        val io = WorkflowResultsIO(dataFile)
        val data = io.readResults().filter { category(it) != "clca"}
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

    @Test
    fun oneauditWithStdDev() {
        val nruns = 100
        val name = "OneAuditWithStdDev"
        val dir = "$testdataDir/plots/oneaudit/$name"
        validateOutputDir(Path(dir))
        val margins =
            listOf(.005, .01, .015, .02, .03, .04, .05)
        val cvrPercents = listOf(0.90)

        val tasks = mutableListOf<ConcurrentTask<List<WorkflowResult>>>()
        margins.forEach { margin ->
            cvrPercents.forEach { cvrPercent ->
                val oneauditGenerator = OneAuditSingleRoundAuditTaskGenerator(
                    N, margin, 0.0, 0.0, cvrPercent, 0.0,
                    auditConfigIn = Config.from(AuditType.ONEAUDIT),
                    parameters=mapOf("nruns" to nruns, "cat" to "oneaudit-${(100 * cvrPercent).toInt()}%"),
                )
                tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator))
            }
        }
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)

        validateOutputDir(Path(dir))
        val writer = WorkflowResultsIO("$dir/${name}.csv")
        writer.writeResults(results)

        wrsErrorBars(
            titleS = "$name samples needed",
            subtitleS = "Nc=${N} nruns=${nruns}",
            wrs = results,
            xname = "margin", xfld = { it.margin },
            yname = "samplesNeeded", yfld = { it.samplesUsed.nextUp() },
            yupperFld = { it.samplesUsed + it.usedStddev },
            ylowerFld = { it.samplesUsed - it.usedStddev },
            catName = "legend",
            catFld = { category(it) },
            writeFile = "$dir/${name}Linear",
        )
    }

}