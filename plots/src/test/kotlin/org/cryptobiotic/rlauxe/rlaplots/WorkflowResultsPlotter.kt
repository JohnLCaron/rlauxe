package org.cryptobiotic.rlauxe.rlaplots

import org.cryptobiotic.rlauxe.workflow.WorkflowResult
import kotlin.math.log10

class WorkflowResultsPlotter(val dir: String, val filename: String) {

    fun showSampleSizesVsMargin(data: List<WorkflowResult>, catName: String, useLog: Boolean = true, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!

        wrsPlot(
            titleS = "$filename estimated sample sizes",
            subtitleS = "N=${exemplar.N} nruns=${nruns.toInt()}",
            data,
            if (useLog) "$dir/${filename}Log" else "$dir/${filename}Linear",
            "margin", if (useLog) "log10(samplesNeeded)" else "samplesNeeded", catName,
            xfld = { it.margin },
            yfld = { if (useLog) log10(it.samplesNeeded) else it.samplesNeeded},
            catfld = catfld,
        )
    }

    fun showSampleSizesVsErrorPct(data: List<WorkflowResult>, catName: String, useLog: Boolean = true, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!

        wrsPlot(
            titleS = "$filename estimated sample sizes",
            subtitleS = "margin=${exemplar.margin} N=${exemplar.N} nruns=${nruns.toInt()}",
            data,
            if (useLog) "$dir/${filename}Log" else "$dir/${filename}Linear",
            "errorPct", if (useLog) "log10(samplesNeeded)" else "samplesNeeded", catName,
            xfld = { it.parameters["fuzzPct"]!! },
            yfld = { if (useLog) log10(it.samplesNeeded) else it.samplesNeeded},
            catfld = catfld,
        )
    }

    fun showFailuresVsErrorPct(data: List<WorkflowResult>, catName: String, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!

        wrsPlot(
            titleS = "$filename failurePct",
            subtitleS = "margin=${exemplar.margin} N=${exemplar.N} nruns=${nruns.toInt()}",
            data,
            "$dir/${filename}Failures",
            "errorPct", "failurePct", catName,
            xfld = { it.parameters["fuzzPct"]!! },
            yfld = { it.failPct},
            catfld = catfld,
        )
    }

    fun showNroundsVsErrorPct(data: List<WorkflowResult>, catName: String, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!

        wrsPlot(
            titleS = "$filename number of audit rounds",
            subtitleS = "margin=${exemplar.margin} N=${exemplar.N} nruns=${nruns.toInt()}",
            data,
            "$dir/${filename}Nrounds",
            "errorPct", "auditRounds", catName,
            xfld = { it.parameters["fuzzPct"]!! },
            yfld = { it.nrounds},
            catfld = catfld,
        )
    }

}