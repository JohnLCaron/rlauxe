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
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.ConcurrentTask
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.io.path.Path
import kotlin.test.Test

class ExtraVsMarginOneAudit {
    val Nc = 50000
    val ntrials = 1000
    val nsimTrials = 100
    val name = "extraVsMarginOA"
    val dirName = "$testdataDir/plots/extra/$name"
    val fuzzMvrs = 0.0 // TODO

    @Test
    fun extraVsMargin() {
        val margins = listOf(.01, .015, .02, .03, .04, .05, .06, .07, .08, .09, .10)
        val cvrPercents = listOf(0.01, 0.25, 0.50, 0.75, 0.90, 0.96)

        val election =
            ElectionInfo.forTest(AuditType.ONEAUDIT, MvrSource.testClcaSimulated) // TODO where do you get the mvrs ??
        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .05,)
        val sampleControl = ContestSampleControl(
            minRecountMargin = 0.0,
            minMargin = 0.0,
            contestSampleCutoff = null,
            auditSampleCutoff = null
        )

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTask<List<WorkflowResult>>>()

        val config = Config(
            election, creation, round =
                AuditRoundConfig(
                    SimulationControl(nsimTrials = nsimTrials), sampling =
                        sampleControl,
                    ClcaConfig(fuzzMvrs = fuzzMvrs), null
                )
        )

        margins.forEach { margin ->
            cvrPercents.forEach { cvrPercent ->
                //     val Nc: Int,
                //    val margin: Double,
                //    val underVotePct: Double,
                //    val phantomPct: Double,
                //    val cvrPercent: Double,
                //    val mvrsFuzzPct: Double,
                //    val parameters : Map<String, Any>,
                //    val auditConfigIn: Config? = null,
                val oneauditGenerator = OneAuditContestAuditTaskGenerator(
                    Nc, margin, 0.0, 0.0, cvrPercent, fuzzMvrs,
                    parameters = mapOf(
                        "nruns" to ntrials.toDouble(),
                        "cat" to "oneaudit-${(100 * cvrPercent).toInt()}%"
                    ),
                    configIn = config
                )
                tasks.add(RepeatedWorkflowRunner(ntrials, oneauditGenerator))
            }
        }

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
        val subtitle = "OneAudit Nc=${Nc} ntrials=${ntrials} nsimTrials=$nsimTrials"

        showExtraVsMargin(dirName, name, subtitle, ScaleType.LogLinear, "cvrPct",
            catOrdering = CvrPctOrdering())
        showEstSizesVsMarginPct(dirName, name, subtitle, ScaleType.LogLinear, "cvrPct") { category(it) }

        showEstSizesVsMarginPct(dirName, name, subtitle, ScaleType.Linear, "cvrPct") { category(it) }
        showExtraVsMargin(dirName, name, subtitle, ScaleType.Linear, "cvrPct")
        showNroundsVsMargin(dirName, name, subtitle, ScaleType.Linear, "cvrPct",
            catOrdering = CvrPctOrdering())
    }

    @Test
    fun addFailures() {
        val subtitle = "OneAudit Nc=${Nc} ntrials=${ntrials} nsimTrials=$nsimTrials fuzzMvrs=$fuzzMvrs"
        showFailuresVsMargin(dirName, name, subtitle, ScaleType.Linear, "cvrPct") { category(it) }
    }
}

class CvrPctOrdering: Comparator<String> {
    override fun compare(o1: String, o2: String): Int {
        val pct1 = o1.split("-","%")[1]
        val pct2 = o2.split("-","%")[1]
        return pct1.compareTo(pct2)
    }
}
