package org.cryptobiotic.rlauxe.nostyles

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

class CompareAuditsNoStyles {
    val nruns = 100
    val nsimEst = 100
    val Nc = 10000
    val Nb = 20000

    val dirName = "/home/stormy/rla/nostyle/compareWithStyle"
    val name = "compareWithStyle"

    // Used in Polling Vs CLCA with/out CSD Estimated Sample sizes

    @Test
    fun compareAuditsByStyles() {
        val margins = listOf(.01, .02, .03, .04, .05, .06, .08, .10, .15, .20)

        val stopwatch = Stopwatch()
        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val pollingConfigNS = AuditConfig(AuditType.POLLING, false, nsimEst = nsimEst)
            val pollingGeneratorNS = PollingContestAuditTaskGenerator(
                Nc, margin, 0.0, 0.0, 0.0,
                mapOf("nruns" to nruns.toDouble(), "Nb" to Nb.toDouble(), "cat" to "pollingNoStyles"),
                auditConfig = pollingConfigNS,
                Nb=Nb)
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGeneratorNS))

            val pollingConfig = AuditConfig(AuditType.POLLING, true, nsimEst = nsimEst)
            val pollingGenerator = PollingContestAuditTaskGenerator(
                Nc, margin, 0.0, 0.0, 0.0,
                mapOf("nruns" to nruns.toDouble(), "Nb" to Nb.toDouble(), "cat" to "pollingWithStyles"),
                auditConfig = pollingConfig,
                Nb=Nc)
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaConfigNS = AuditConfig(
                AuditType.CLCA, false, nsimEst = nsimEst,
                clcaConfig = ClcaConfig(ClcaStrategyType.noerror)
            )
            val clcaGeneratorNS = ClcaContestAuditTaskGenerator(Nc, margin, 0.0, 0.0, 0.0,
                mapOf("nruns" to nruns.toDouble(), "cat" to "clcaNoStyles"),
                auditConfig = clcaConfigNS,
                Nb=Nb
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGeneratorNS))

            val clcaConfig = AuditConfig(
                AuditType.CLCA, true, nsimEst = nsimEst,
                clcaConfig = ClcaConfig(ClcaStrategyType.noerror)
            )
            val clcaGenerator = ClcaContestAuditTaskGenerator(Nc, margin, 0.0, 0.0, 0.0,
                mapOf("nruns" to nruns.toDouble(), "cat" to "clcaWithStyles"),
                auditConfig = clcaConfig,
                Nb=Nc
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))
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
        val subtitle = "Nb=${Nb} Nc=${Nc} nruns=${nruns}"
        showNmvrsByAuditType(name, dirName, subtitle, ScaleType.LogLinear)
        showNmvrsByAuditType(name, dirName, subtitle, ScaleType.LogLog)
    }
}

fun showNmvrsByAuditType(name: String, dirName: String, subtitle: String, scaleType: ScaleType) {
    val io = WorkflowResultsIO("$dirName/${name}.csv")
    val data = io.readResults()

    wrsPlot(
        titleS = "$name overall number of ballots sampled",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}${scaleType.name}",
        wrs = data,
        xname = "margin", xfld = { it.margin },
        yname = "nmvrs", yfld = { it.nmvrs },
        catName = "auditType", catfld = { category(it) },
        scaleType = scaleType
    )
}