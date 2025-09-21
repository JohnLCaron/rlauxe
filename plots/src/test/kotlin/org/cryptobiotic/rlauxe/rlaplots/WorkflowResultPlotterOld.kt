package org.cryptobiotic.rlauxe.rlaplots

import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.workflow.WorkflowResult
import kotlin.math.log10

// TODO replace with wrsPlot() in WorkflowResultsPlotter

enum class ScaleTypeOld { Linear, Log, Pct;
    fun desc(what: String): String =
        when (this) {
            Linear -> what
            Log -> "log10($what)"
            Pct -> "$what %"
        }
}

class WorkflowResultsPlotterOld(val dir: String, val filename: String) {

    fun showNmvrsVsMargin(data: List<WorkflowResult>, catName: String, yscale: ScaleTypeOld = ScaleTypeOld.Linear, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!
        val fuzzPct = exemplar.parameters["fuzzPct"]
        val fuzzPctLabel = if (fuzzPct != null) " fuzzPct=$fuzzPct" else ""

        wrsPlot(
            titleS = "$filename estimated number of MVRS",
            subtitleS = "Nc=${exemplar.Nc} nruns=${nruns}" + fuzzPctLabel,
            data,
            "$dir/${filename}${yscale.name}",
            "margin",
            yscale.desc("number of Mvrs"),
            catName,
            xfld = { it.margin },
            yfld = { it: WorkflowResult -> when (yscale) {
                ScaleTypeOld.Linear -> it.nmvrs
                ScaleTypeOld.Log -> log10(it.nmvrs)
                ScaleTypeOld.Pct -> {
                    val Nb = it.Dparam("Nb")
                    (100*it.nmvrs/Nb)
                }
            }},
            catfld = catfld,
        )
    }

    fun showSampleSizesVsMargin(data: List<WorkflowResult>, subtitle: String? = null, catName: String, yscale: ScaleTypeOld = ScaleTypeOld.Linear, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!
        val fuzzPct = exemplar.parameters["fuzzPct"]
        val fuzzPctLabel = if (fuzzPct == null) "" else " fuzzPct=$fuzzPct"

        wrsPlot(
            titleS = "$filename samples needed",
            subtitleS = subtitle?: "Nc=${exemplar.Nc} nruns=${nruns}" + fuzzPctLabel,
            data,
            "$dir/${filename}${yscale.name}",
            "margin",
            yscale.desc("samplesNeeded"),
            catName,
            xfld = { it.margin },
            yfld = { it: WorkflowResult -> when (yscale) {
                ScaleTypeOld.Linear -> it.samplesUsed
                ScaleTypeOld.Log -> log10(it.samplesUsed)
                ScaleTypeOld.Pct -> (100*it.samplesUsed/it.Nc.toDouble())
            }},
            catfld = catfld,
        )
    }

    fun showSampleSizesVsTheta(data: List<WorkflowResult>, subtitle: String, yscale: ScaleTypeOld, catName: String, catfld: (WorkflowResult) -> String) {

        wrsPlot(
            titleS = "$filename samples needed",
            subtitleS = subtitle,
            data,
            "$dir/${filename}${yscale.name}",
            "theta",
            yscale.desc("samplesNeeded"),
            catName,
            xfld = { it.Dparam("theta") },
            yfld = { it: WorkflowResult -> when (yscale) {
                ScaleTypeOld.Linear -> it.samplesUsed
                ScaleTypeOld.Log -> log10(it.samplesUsed)
                ScaleTypeOld.Pct -> (100*it.samplesUsed/it.Nc.toDouble())
            }},
            catfld = catfld,
        )

    }

    fun showEstSizesVsMargin(data: List<WorkflowResult>, subtitle: String, catName: String, yscale: ScaleTypeOld = ScaleTypeOld.Linear, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!
        val fuzzPct = exemplar.parameters["fuzzPct"]
        val fuzzPctLabel = if (fuzzPct == null) "" else " fuzzPct=$fuzzPct"

        wrsPlot(
            titleS = if (yscale == ScaleTypeOld.Pct) "$filename extraSamples/nmvrs %" else  "$filename nmvrs - samplesNeeded",
            subtitleS = subtitle,
            data,
            "$dir/${filename}${yscale.name}",
            "margin",
            yscale.desc("extra samples"),
            catName,
            xfld = { it.margin },
            yfld = { it: WorkflowResult -> when (yscale) {
                ScaleTypeOld.Linear -> (it.nmvrs - it.samplesUsed)
                ScaleTypeOld.Log -> log10( (it.nmvrs - it.samplesUsed))// needed?
                ScaleTypeOld.Pct -> (100* (it.nmvrs - it.samplesUsed)/it.nmvrs )
            }},
            catfld = catfld,
        )
    }

    fun showFailuresVsMargin(data: List<WorkflowResult>, subtitle: String? = null, catName: String, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!
        val fuzzPct = exemplar.parameters["fuzzPct"]
        val fuzzPctLabel = if (fuzzPct == null) "" else " fuzzPct=$fuzzPct"

        wrsPlot(
            titleS = "$filename failurePct",
            subtitleS = subtitle ?: ("Nc=${exemplar.Nc} nruns=${nruns}" + fuzzPctLabel),
            data,
            "$dir/${filename}Failures",
            "margin",
            "failurePct",
            catName,
            xfld = { it.margin },
            yfld = { it.failPct },
            catfld = catfld,
        )
    }

    fun showNroundsVsMargin(data: List<WorkflowResult>, subtitle: String? = null, catName: String, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]

        wrsPlot(
            titleS = "$filename number of audit rounds",
            subtitleS = subtitle ?: "N=${exemplar.Nc} nruns=${exemplar.parameters["nruns"]!!}",
            data,
            "$dir/${filename}Nrounds",
            "margin",
            "auditRounds",
            catName,
            xfld = { it.margin },
            yfld = { it.nrounds},
            catfld = catfld,
        )
    }

}

fun category(wr: WorkflowResult): String {
    return wr.parameters["cat"] as String
}

fun categoryFuzzPct(wr: WorkflowResult): String {
    return df(wr.Dparam("fuzzPct"))
}

fun categoryFuzzDiff(wr: WorkflowResult): String {
    return dfn(100.0*wr.Dparam("fuzzDiff"), 2)
}
