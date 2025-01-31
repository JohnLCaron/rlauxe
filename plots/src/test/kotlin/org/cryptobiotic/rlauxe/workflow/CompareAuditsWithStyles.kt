package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.Scale
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsPlotter
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.random.Random
import kotlin.test.Test

class CompareAuditsWithStyles {

    // Used in docs

    @Test
    fun plotComparisonVsStyleAndPoll() {
        val Nc = 10000
        val Nb = 20000
        val nruns = 100
        val margins = listOf(.01, .02, .03, .04, .05, .06, .08, .10, .15, .20)

        val stopwatch = Stopwatch()
        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val pollingConfigNS = AuditConfig(AuditType.POLLING, false, samplePctCutoff=0.5, nsimEst = 10)
            val pollingGeneratorNS = PollingWorkflowTaskGenerator(
                Nc, margin, 0.0, 0.0, 0.0,
                mapOf("nruns" to nruns.toDouble(), "Nb" to Nb.toDouble(), "cat" to 1.0),
                auditConfigIn = pollingConfigNS,
                Nb=Nb)
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGeneratorNS))

            val pollingConfig = AuditConfig(AuditType.POLLING, true, samplePctCutoff=0.5, nsimEst = 10)
            val pollingGenerator = PollingWorkflowTaskGenerator(
                Nc, margin, 0.0, 0.0, 0.0,
                mapOf("nruns" to nruns.toDouble(), "Nb" to Nb.toDouble(), "cat" to 2.0),
                auditConfigIn = pollingConfig,
                Nb=Nc)
            tasks.add(RepeatedWorkflowRunner(nruns, pollingGenerator))

            val clcaConfigNS = AuditConfig(AuditType.CARD_COMPARISON, false, samplePctCutoff=0.5, nsimEst = 10,
                clcaConfig = ClcaConfig(ClcaStrategyType.noerror))
            val clcaGeneratorNS = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, 0.0, 0.0,
                mapOf("nruns" to nruns.toDouble(), "cat" to 3.0),
                auditConfigIn = clcaConfigNS,
                Nb=Nb
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGeneratorNS))

            val clcaConfig = AuditConfig(AuditType.CARD_COMPARISON, true, samplePctCutoff=0.5, nsimEst = 10,
                clcaConfig = ClcaConfig(ClcaStrategyType.noerror))
            val clcaGenerator = ClcaWorkflowTaskGenerator(Nc, margin, 0.0, 0.0, 0.0,
                mapOf("nruns" to nruns.toDouble(), "cat" to 4.0),
                auditConfigIn = clcaConfig,
                Nb=Nc
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))
        }

        // run tasks concurrently and average the results
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        val dirName = "/home/stormy/temp/workflow/compareWithStyle"
        val name = "compareWithStyle"
        val writer = WorkflowResultsIO("$dirName/${name}.cvs")
        writer.writeResults(results)

        showNmvrsVsMargin(name, dirName, Scale.Linear)
        showNmvrsVsMargin(name, dirName, Scale.Log)
        showNmvrsVsMargin(name, dirName, Scale.Pct)
        showFailuresVsMargin(name, dirName)
    }

    fun showNmvrsVsMargin(name: String, dirName: String, yscale: Scale) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showNmvrsVsMargin(results, "type", yscale) { category(it) }
    }

    fun showFailuresVsMargin(name: String, dirName: String, ) {
        val io = WorkflowResultsIO("$dirName/${name}.cvs")
        val results = io.readResults()

        val plotter = WorkflowResultsPlotter(dirName, name)
        plotter.showFailuresVsMargin(results, catName="type") { category(it) }
    }

    fun category(wr: WorkflowResult): String {
        return when (wr.parameters["cat"]) {
            1.0 -> "pollingNoStyles"
            2.0 -> "pollingWithStyles"
            3.0 -> "clcaNoStyles"
            4.0 -> "clcaWithStyles"
            else -> "unknown"
        }
    }
}