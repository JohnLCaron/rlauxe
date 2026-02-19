package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.io.path.Path
import kotlin.test.Test

class OneAuditWithErrors {

    @Test
    fun oneAuditsWithFuzz() {
        val nruns = 200
        val name = "OneAuditWithFuzz"
        val dirName = "$testdataDir/plots/oneaudit/$name"

        val cvrPercent = 0.90
        val N = 50000
        val margins = listOf(.01, .02, .04)
        val fuzzPcts = listOf(.00, .001, .0025, .005, .0075, .01)

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()

        for (margin in margins) {
            fuzzPcts.forEach { fuzzPct ->
                val oneauditGenerator = OneAuditSingleRoundAuditTaskGeneratorWithFlips(
                    N, margin, 0.0, 0.0, cvrPercent, mvrsFuzzPct = fuzzPct,
                    auditConfigIn = AuditConfig(AuditType.ONEAUDIT),
                    parameters = mapOf("nruns" to nruns.toDouble(), "fuzzPct" to fuzzPct, "cat" to margin)
                )
                tasks.add(RepeatedWorkflowRunner(nruns, oneauditGenerator))
            }
        }

        // run tasks concurrently and average the results
        println("---oneAuditsWithFuzz running ${tasks.size} tasks nruns= $nruns")
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        validateOutputDir(Path(dirName))
        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        val subtitle = "Nc=${N} nruns=${nruns} cvrPercent=${cvrPercent*100}%"
        sampleSizesVsFuzzPctStdDev(dirName, name, subtitle, catName="margin", catfld= { category(it) })
    }
}
