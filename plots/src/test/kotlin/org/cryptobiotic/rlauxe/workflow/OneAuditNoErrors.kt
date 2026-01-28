package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.io.path.Path
import kotlin.math.nextUp
import kotlin.test.Test

class OneAuditNoErrors {
    val nruns = 100
    val N = 50000

    @Test
    fun oaNoErrorsPlots() {
        val margins =
            listOf(.005, .01, .015, .02, .03, .04, .05, .06, .07, .08, .10, .20)
        val cvrPercents = listOf(0.50, 0.75, 0.83, 0.90, 0.96)

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
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
                val oneauditGenerator = OneAuditSingleRoundAuditTaskGeneratorWithFlips(
                    N, margin, 0.0, 0.0, cvrPercent, 0.0,
                    auditConfigIn = AuditConfig(AuditType.ONEAUDIT, true),
                    parameters=mapOf("nruns" to nruns, "cat" to "oneaudit-${(100 * cvrPercent).toInt()}%"),
                )
                tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator))
            }
        }
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks, nthreads=40)
        println(stopwatch.took())

        val name = "OneAuditNoErrors"
        val dirName = "$testdataDir/plots/oneaudit/$name"

        validateOutputDir(Path(dirName))
        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        regenPlots(name, dirName)
    }

    fun regenPlots(name: String, dirName:String) {
        val subtitle = "Nc=${N} nruns=${nruns}"
        //showSampleSizesVsMargin(name, dirName, subtitle, ScaleType.Linear)
        showSampleSizesVsMargin(name, dirName, subtitle, ScaleType.LogLinear)
        //showSampleSizesVsMargin(name, dirName, subtitle, ScaleType.LogLog)
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

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            cvrPercents.forEach { cvrPercent ->
                val oneauditGenerator = OneAuditSingleRoundAuditTaskGeneratorWithFlips(
                    N, margin, 0.0, 0.0, cvrPercent, 0.0,
                    auditConfigIn = AuditConfig(AuditType.ONEAUDIT, true),
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