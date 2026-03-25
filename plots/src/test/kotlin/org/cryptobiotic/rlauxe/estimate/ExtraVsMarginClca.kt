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

class ExtraVsMarginClca {
    val Nc = 50000
    val ntrials = 1000
    val nsimTrials = 100
    val name = "extraVsMarginWithPhantoms"
    val dirName = "$testdataDir/plots/extra/$name"
    val phantomPct = .01

    // Used in docs
    @Test
    fun extraVsMargin() {
        val margins = listOf(.02, .025, .03, .04, .05, .06, .07, .08, .09, .10)
        val mvrFuzzs = listOf(.001, .002, .003, .004, .005)

        val election = ElectionInfo.forTest(AuditType.CLCA, MvrSource.testClcaSimulated)
        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit = .05,)
        val sampleControl = ContestSampleControl(
            minRecountMargin = 0.0,
            minMargin = 0.0,
            contestSampleCutoff = null,
            auditSampleCutoff = null
        )

        val stopwatch = Stopwatch()

        val tasks = mutableListOf<ConcurrentTask<List<WorkflowResult>>>()
        mvrFuzzs.forEach { fuzzMvrs ->

            val config = Config(
                election, creation, round =
                    AuditRoundConfig(
                        SimulationControl(nsimTrials = nsimTrials), sampling =
                            sampleControl,
                        ClcaConfig(fuzzMvrs = fuzzMvrs), null
                    )
            )

            margins.forEach { margin ->
                val clcaGenerator1 = ClcaContestAuditTaskGenerator(
                    "'extraVsMargin fuzzMvrs=$fuzzMvrs, margin=$margin'",
                    Nc, margin, 0.1, phantomPct, fuzzMvrs,
                    parameters = mapOf("nruns" to ntrials.toDouble(), "fuzzMvrs" to fuzzMvrs),
                    config = config
                )
                tasks.add(RepeatedWorkflowRunner(ntrials, clcaGenerator1))
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
        val subtitle = "CLCA Nc=${Nc} phantomPct=$phantomPct ntrials=${ntrials} nsimTrials=$nsimTrials"

        showExtraVsMargin(dirName, name, subtitle, ScaleType.LogLinear, "fuzzMvrs") { categoryFuzzMvrs(it) }
        //showEstSizesVsMarginPct(dirName, name, subtitle, ScaleType.LogLinear, "fuzzMvrs")  { categoryFuzzMvrs(it) }

        showExtraVsMargin(dirName, name, subtitle, ScaleType.Linear, "fuzzMvrs") { categoryFuzzMvrs(it) }
        //showEstSizesVsMarginPct(dirName, name, subtitle, ScaleType.Linear, "fuzzMvrs")  { categoryFuzzMvrs(it) }
        showNroundsVsMargin(dirName, name, subtitle, ScaleType.Linear, "fuzzMvrs") { categoryFuzzMvrs(it) }
    }

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
        yname = "auditRounds", yfld = { it.nrounds },
        catName = catName, catfld = catfld,
        scaleType = scaleType
    )
}

fun showFailuresVsMargin(dirName: String, name:String, subtitle: String, scaleType: ScaleType, catName: String, catfld: (WorkflowResult) -> String) {
    val io = WorkflowResultsIO("$dirName/${name}.csv")
    val data = io.readResults()

    wrsPlot(
        titleS = "$name failure pct",
        subtitleS = subtitle,
        wrs = data,
        writeFile = "$dirName/${name}Failures${scaleType.name}",
        xname = "margin", xfld = { it.margin },
        yname = "failPct", yfld = { it.failPct },
        catName = catName, catfld = catfld,
        scaleType = scaleType
    )
}
