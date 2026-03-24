package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.ElectionInfo
import org.cryptobiotic.rlauxe.audit.MvrSource
import org.cryptobiotic.rlauxe.audit.PollingConfig
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.ConcurrentTask
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.io.path.Path
import kotlin.test.Test

class ExtraVsMarginPolling {
    val Nc = 50000
    val ntrials = 100
    val nsimTrials = 100
    val name = "extraVsMarginPolling"
    val dirName = "$testdataDir/plots/extra/$name"
    val phantomPct = .01

    // Used in docs
    @Test
    fun extraVsMargin() {
        val margins = listOf(.015, .02, .03, .04, .05, .06, .07, .08, .09, .10)
        val mvrFuzzs = listOf(.001, .002, .003, .004, .005)

        val election = ElectionInfo.forTest(AuditType.POLLING, MvrSource.testPrivateMvrs)
        val creation = AuditCreationConfig(AuditType.POLLING, riskLimit=.05,)

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTask<List<WorkflowResult>>>()
        // mvrFuzzs.forEach { fuzzMvrs ->

            val config = Config(election, creation, round =
                AuditRoundConfig(
                    SimulationControl(nsimTrials = nsimTrials), sampling =
                        ContestSampleControl.NONE,
                    null, PollingConfig())
                )

            // class PollingContestAuditTaskGenerator(
            //    val Nc: Int,
            //    val margin: Double,
            //    val underVotePct: Double,
            //    val phantomPct: Double,
            //    val mvrsFuzzPct: Double,
            //    val parameters : Map<String, Any>,
            //    val auditConfig: Config? = null,
            //    val Npop: Int,
            //    val nSimTrials: Int = 100,
            //    ) : ContestAuditTaskGenerator {
            margins.forEach { margin ->
                val clcaGenerator1 = PollingContestAuditTaskGenerator(
                    Nc, margin, 0.0, 0.0, 0.0,
                    parameters=mapOf("nruns" to ntrials.toDouble(), "fuzzMvrs" to 0.0),
                    config=config, Nc, nsimTrials)
                tasks.add(RepeatedWorkflowRunner(ntrials, clcaGenerator1))
            }

        // }
        println("run ${tasks.size} tasks $ntrials trials each with ${nsimTrials} simulations each trial")
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        validateOutputDir(Path(dirName))
        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        regenPlots()
    }

    @Test
    fun regenPlots() {
        val subtitle = "Polling Nc=${Nc} ntrials=${ntrials} nsimTrials=$nsimTrials"

        showExtraVsMargin(dirName, name, subtitle, ScaleType.LogLinear, "fuzzMvrs") { categoryFuzzMvrs(it) }
        //showEstSizesVsMarginPct(dirName, name, subtitle, ScaleType.LogLinear, "fuzzMvrs")  { categoryFuzzMvrs(it) }

        showExtraVsMargin(dirName, name, subtitle, ScaleType.Linear, "fuzzMvrs") { categoryFuzzMvrs(it) }
        //showEstSizesVsMarginPct(dirName, name, subtitle, ScaleType.Linear, "fuzzMvrs")  { categoryFuzzMvrs(it) }
        showNroundsVsMargin(dirName, name, subtitle, ScaleType.Linear, "fuzzMvrs")  { categoryFuzzMvrs(it) }
    }

    @Test
    fun addFailures() {
        val subtitle = "Polling Nc=${Nc} ntrials=${ntrials} nsimTrials=$nsimTrials"
        showFailuresVsMargin(dirName, name, subtitle, ScaleType.Linear, "legend")  { categoryFuzzMvrs(it) }
    }
}