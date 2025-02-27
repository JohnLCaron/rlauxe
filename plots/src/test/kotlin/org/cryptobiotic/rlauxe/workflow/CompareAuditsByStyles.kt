package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test

class CompareAuditsByStyles {
    val nruns = 100
    val nsimEst = 100
    val Nc = 10000
    val Nb = 20000

    val dirName = "/home/stormy/temp/workflow/compareWithStyle"
    val name = "compareWithStyle"

    // Used in Polling Vs CLCA with/out CSD Estimated Sample sizes

    @Test
    fun compareAuditsByStyles() {
        val margins = listOf(.01, .02, .03, .04, .05, .06, .08, .10, .15, .20)

        val stopwatch = Stopwatch()
        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val pollingConfigNS = AuditConfig(AuditType.POLLING, false, samplePctCutoff=1.0, nsimEst = nsimEst)
            val pollingGeneratorNS = PollingWorkflowTaskGenerator(
                Nc, margin, 0.0, 0.0, 0.0,
                mapOf("nruns" to nruns.toDouble(), "Nb" to Nb.toDouble(), "cat" to "pollingNoStyles"),
                auditConfig = pollingConfigNS,
                Nb=Nb)
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGeneratorNS))

            val pollingConfig = AuditConfig(AuditType.POLLING, true, samplePctCutoff=1.0, nsimEst = nsimEst)
            val pollingGenerator = PollingWorkflowTaskGenerator(
                Nc, margin, 0.0, 0.0, 0.0,
                mapOf("nruns" to nruns.toDouble(), "Nb" to Nb.toDouble(), "cat" to "pollingWithStyles"),
                auditConfig = pollingConfig,
                Nb=Nc)
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaConfigNS = AuditConfig(AuditType.CLCA, false, samplePctCutoff=1.0, nsimEst = nsimEst,
                clcaConfig = ClcaConfig(ClcaStrategyType.noerror))
            val clcaGeneratorNS = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, 0.0, 0.0,
                mapOf("nruns" to nruns.toDouble(), "cat" to "clcaNoStyles"),
                auditConfig = clcaConfigNS,
                Nb=Nb
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGeneratorNS))

            val clcaConfig = AuditConfig(AuditType.CLCA, true, samplePctCutoff=1.0, nsimEst = nsimEst,
                clcaConfig = ClcaConfig(ClcaStrategyType.noerror))
            val clcaGenerator = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, 0.0, 0.0,
                mapOf("nruns" to nruns.toDouble(), "cat" to "clcaWithStyles"),
                auditConfig = clcaConfig,
                Nb=Nc
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        regenPlots()
    }

    @Test
    fun regenPlots() {
        val subtitle = "Nb=${Nb} Nc=${Nc} nruns=${nruns}"
        showNmvrsVsMargin(name, dirName, subtitle, ScaleType.LogLinear)
        showNmvrsVsMargin(name, dirName, subtitle, ScaleType.LogLog)
    }

    fun showNmvrsVsMargin(name: String, dirName: String, subtitle: String, scaleType: ScaleType) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
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
}