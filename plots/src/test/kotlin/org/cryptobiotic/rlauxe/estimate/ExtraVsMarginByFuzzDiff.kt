package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ClcaStrategyType
import org.cryptobiotic.rlauxe.audit.SimulationStrategy
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.io.path.Path
import kotlin.test.Test

class ExtraVsMarginByFuzzDiff {
    val Nc = 50000
    val ntrials = 1000
    val nsimEst = 100
    val name = "extraVsMarginCalc003ga3"
    val dirName = "$testdataDir/plots/extra/$name"
    val fuzzMvrs = 0.003

    // Used in docs
    @Test
    fun extraVsMargin() {
        val margins = listOf(.01, .015, .02, .03, .04, .05, .06, .07, .08, .09, .10)
        val fuzzDiffs = listOf(-.003, -.0015, 0.0, .0015, .003)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTaskG<List<WorkflowResult>>>()
        fuzzDiffs.forEach { fuzzDiff ->
            val simFuzzPct = fuzzMvrs+fuzzDiff
            val auditConfig = AuditConfig(
                AuditType.CLCA, nsimEst = nsimEst, simFuzzPct = simFuzzPct,
                persistedWorkflowMode =  PersistedWorkflowMode.testSimulated,
                simulationStrategy = SimulationStrategy.optimistic,
                clcaConfig = ClcaConfig(fuzzMvrs=fuzzMvrs, strategy=ClcaStrategyType.generalAdaptive2)
            )

            margins.forEach { margin ->
                val clcaGenerator1 = ClcaContestAuditTaskGenerator("'extraVsMargin simFuzzPct=$simFuzzPct, margin=$margin'",
                    Nc, margin, 0.1, 0.0, fuzzMvrs,
                    parameters=mapOf("nruns" to ntrials.toDouble(), "simFuzzPct" to simFuzzPct, "fuzzMvrs" to fuzzMvrs),
                    config=auditConfig)
                tasks.add(RepeatedWorkflowRunner(ntrials, clcaGenerator1))
            }

        }
        println("run ${tasks.size} tasks $ntrials trials each with ${nsimEst} simulations each trial")
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        println(stopwatch.took())

        validateOutputDir(Path(dirName))
        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        regenPlots()
    }

    @Test
    fun regenPlots() {
        val subtitle = "Nc=${Nc} ntrials=${ntrials} fuzzMvrs=$fuzzMvrs"

        showExtraVsMargin(dirName, name, subtitle, ScaleType.Linear, "simFuzzPct") { categoryFuzzDiff(it) }
        showEstSizesVsMarginPct(dirName, name, subtitle, ScaleType.Linear, "simFuzzPct")  { categoryFuzzDiff(it) }
        showNroundsVsMargin(dirName, name, subtitle, ScaleType.Linear, "simFuzzPct")  { categoryFuzzDiff(it) }
        // showRatioVsMargin(dirName, name, subtitle, ScaleType.Linear, "simFuzzPct")  { categoryFuzzDiff(it) }
    }

    fun showExtraVsMargin(dirName: String, name:String, subtitle: String, scaleType: ScaleType,
                                 catName: String, catfld: ((WorkflowResult) -> String) = { category(it) } ) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val data = io.readResults()
        wrsPlot(
            titleS = "$name extra samples",
            subtitleS = subtitle,
            writeFile = "$dirName/${name}${scaleType.name}",
            wrs = data,
            xname = "margin", xfld = { it.margin},
            yname = "extraSamples", yfld = { it.nmvrs - it.samplesUsed },
            catName = catName, catfld = catfld,
            scaleType = scaleType
        )
    }

    fun showEstSizesVsMarginPct(dirName: String, name:String, subtitle: String, scaleType: ScaleType, catName: String,
                                catfld: (WorkflowResult) -> String) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val data = io.readResults()

        wrsPlot(
            titleS = "$name extraSamples/nmvrs %",
            subtitleS = subtitle,
            wrs = data,
            writeFile = "$dirName/${name}Pct${scaleType.name}",
            xname = "margin", xfld = { it.margin },
            yname = "extra samples %",
            yfld =  { 100* (it.nmvrs - it.samplesUsed)/it.nmvrs },
            catName = catName, catfld = catfld,
            scaleType = scaleType
        )
    }

    fun showNroundsVsMargin(dirName: String, name:String, subtitle: String, scaleType: ScaleType, catName: String, catfld: (WorkflowResult) -> String) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val data = io.readResults()

        wrsPlot(
            titleS = "$name number of audit rounds",
            subtitleS = subtitle,
            wrs = data,
            writeFile = "$dirName/${name}Nrounds${scaleType.name}",
            xname = "margin", xfld = { it.margin },
            yname = "auditRounds", yfld = { it.nrounds},
            catName = catName, catfld = catfld,
            scaleType = scaleType
        )
    }

    fun showRatioVsMargin(dirName: String, name:String, subtitle: String, scaleType: ScaleType, catName: String, catfld: (WorkflowResult) -> String) {
        val io = WorkflowResultsIO("$dirName/${name}.csv")
        val data = io.readResults()

        wrsPlot(
            titleS = "$name extraSamples/nrounds",
            subtitleS = subtitle,
            wrs = data,
            writeFile = "$dirName/${name}Ratio${scaleType.name}",
            xname = "margin", xfld = { it.margin },
            yname = "extraSamples/nrounds", yfld = { it.wtf },
            catName = catName, catfld = catfld,
            scaleType = scaleType
        )
    }
}