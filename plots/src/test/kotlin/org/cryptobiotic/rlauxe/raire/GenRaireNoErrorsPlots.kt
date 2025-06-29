package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.rlaplots.showSampleSizesVsMargin
import org.cryptobiotic.rlauxe.rlaplots.ScaleType
import org.cryptobiotic.rlauxe.rlaplots.WorkflowResultsIO
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

// candidate for removal

class GenRaireNoErrorsPlots {
    val nruns = 100
    val nsimEst = 100
    val N = 20000
    val name = "raireNoErrors"
    val dirName = "/home/stormy/temp/$name"

    @Test
    fun raireNoErrorsPlots() {
        val margins =
            listOf(.005, .006, .008, .01, .012, .016, .02, .03, .04, .05)

        val config = AuditConfig(AuditType.CLCA, true, nsimEst = nsimEst)

        val stopwatch = Stopwatch()
        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        margins.forEach { margin ->
            val raireGenerator = RaireContestAuditTaskGenerator(
                N, margin, 0.0, 0.0, 0.0, nsimEst=nsimEst,
                auditConfig=config,
                parameters=mapOf("nruns" to nruns, "cat" to "raire")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, raireGenerator))

            val noerrorGenerator = ClcaContestAuditTaskGenerator(
                N, margin, 0.0, 0.0, 0.0, nsimEst=nsimEst,
                auditConfig=config,
                parameters=mapOf("nruns" to nruns, "cat" to "clcaNoerror")
            )
            tasks.add(RepeatedWorkflowRunner(nruns, noerrorGenerator))
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
        val subtitle = "Nc=${N} nruns=${nruns}"
        // fun showSampleSizesVsMargin(dirName: String, name:String, subtitle: String, scaleType: ScaleType, catName: String) {
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.Linear, catName = "cat")
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.LogLinear, catName = "cat")
    }
}
