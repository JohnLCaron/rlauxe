package org.cryptobiotic.rlauxe.nostyles

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.concur.*
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class GenPollingNoStyles {
    val name = "pollingNoStyle"
    val dirName = "$testdataDir/nostyle/$name"

    val Nc = 10000
    val nruns = 100
    val nsimEst = 100

    // Used in docs

    @Test
    fun plotPollingNoStyle() {
        val Npops = listOf(10000, 20000, 50000, 100000)
        val margins = listOf(.01, .02, .03, .04, .05, .06, .08, .10, .15, .20)
        val auditConfig = AuditConfig(AuditType.POLLING, false, nsimEst = nsimEst)

        val stopwatch = Stopwatch()
        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        Npops.forEach { Npop ->
            margins.forEach { margin ->
                val pollingGenerator = PollingContestAuditTaskGenerator(
                    Nc, margin, 0.0, 0.0, 0.0,
                    mapOf("nruns" to nruns, "Npop" to Npop, "cat" to Npop),
                    auditConfig = auditConfig,
                    Npop)

                tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))
            }
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
        val subtitle = "Nc=${Nc} nruns=${nruns}"
        showNmvrsByNb(name, dirName, subtitle, ScaleType.Linear)
        showNmvrsByNb(name, dirName, subtitle, ScaleType.LogLinear)
        showNmvrsByNb(name, dirName, subtitle, ScaleType.LogLog)
        showNmvrPctByNb(name, dirName, subtitle)
    }
}

fun showNmvrsByNb(name: String, dirName: String, subtitle: String, scaleType: ScaleType) {
    val io = WorkflowResultsIO("$dirName/${name}.csv")
    val data = io.readResults()

    wrsPlot(
        titleS = "$name overall number of ballots sampled",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}${scaleType.name}",
        wrs = data,
        xname = "margin", xfld = { it.margin },
        yname = "nmvrs", yfld = { it.nmvrs },
        catName = "Nballots", catfld = { category(it) },
        scaleType = scaleType
    )
}

fun showNmvrPctByNb(name: String, dirName: String, subtitle: String) {
    val io = WorkflowResultsIO("$dirName/${name}.csv")
    val data = io.readResults()

    wrsPlot(
        titleS = "$name percent ballots sampled",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}Pct",
        wrs = data,
        xname = "margin", xfld = { it.margin },
        yname = "% of Npop", yfld = { (100*it.nmvrs/it.Dparam("Npop")) },
        catName = "Nballots", catfld = { category(it) },
        scaleType = ScaleType.Linear
    )
}
