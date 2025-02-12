package org.cryptobiotic.rlauxe.rlaplots

import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.workflow.WorkflowResult
import kotlin.math.log10

enum class ScaleTypeOld { Linear, Log, Pct;
    fun desc(what: String): String =
        when (this) {
            Linear -> what
            Log -> "log10($what)"
            Pct -> "$what %"
        }
}

class WorkflowResultsPlotter(val dir: String, val filename: String) {

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
                ScaleTypeOld.Linear -> it.samplesNeeded
                ScaleTypeOld.Log -> log10(it.samplesNeeded)
                ScaleTypeOld.Pct -> (100*it.samplesNeeded/it.Nc.toDouble())
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
                ScaleTypeOld.Linear -> it.samplesNeeded
                ScaleTypeOld.Log -> log10(it.samplesNeeded)
                ScaleTypeOld.Pct -> (100*it.samplesNeeded/it.Nc.toDouble())
            }},
            catfld = catfld,
        )

    }

    fun showEstSizesVsMarginStrategy(data: List<WorkflowResult>, subtitle: String, catName: String, yscale: ScaleTypeOld = ScaleTypeOld.Linear, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!
        val fuzzPct = exemplar.parameters["fuzzPct"]
        val fuzzPctLabel = if (fuzzPct == null) "" else " fuzzPct=$fuzzPct"

        wrsPlot(
            titleS = "$filename extra samples",
            subtitleS = subtitle,
            data,
            "$dir/${filename}${yscale.name}",
            "margin",
            yscale.desc("extra samples"),
            catName,
            xfld = { it.margin },
            yfld = { it: WorkflowResult -> when (yscale) {
                ScaleTypeOld.Linear -> (it.nmvrs - it.samplesNeeded)
                ScaleTypeOld.Log -> log10( (it.nmvrs - it.samplesNeeded))// needed?
                ScaleTypeOld.Pct -> (100* (it.nmvrs - it.samplesNeeded)/it.nmvrs )
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
                ScaleTypeOld.Linear -> (it.nmvrs - it.samplesNeeded)
                ScaleTypeOld.Log -> log10( (it.nmvrs - it.samplesNeeded))// needed?
                ScaleTypeOld.Pct -> (100* (it.nmvrs - it.samplesNeeded)/it.nmvrs )
            }},
            catfld = catfld,
        )
    }

    fun showEstCostVsVersion(data: List<WorkflowResult>, catName: String, yscale: ScaleTypeOld = ScaleTypeOld.Linear, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!
        val fuzzPct = exemplar.parameters["fuzzPct"]
        val fuzzPctLabel = if (fuzzPct == null) "" else " fuzzPct=$fuzzPct"

        wrsPlot(
            titleS = "$filename estimated cost",
            subtitleS = "Nc=${exemplar.Nc} nruns=${nruns}" + fuzzPctLabel,
            data,
            "$dir/${filename}Cost${yscale.name}",
            "margin",
            yscale.desc("estimated cost"),
            catName,
            xfld = { it.margin },
            yfld = { it: WorkflowResult -> when (yscale) {
                ScaleTypeOld.Linear -> estimatedCost(it)
                ScaleTypeOld.Log -> log10( estimatedCost(it) )// needed?
                ScaleTypeOld.Pct -> (100 * estimatedCost(it)/it.nmvrs )
            }},
            catfld = catfld,
        )
    }

    fun estimatedCost(wr: WorkflowResult): Double {
        val extraCost= wr.nmvrs - wr.samplesNeeded
        val roundCost = if (wr.nrounds > 2) 500.0 * (wr.nrounds - 2) else 0.0
        return extraCost + roundCost
    }

    fun showFailuresVsTheta(data: List<WorkflowResult>, subtitle: String, catName: String, catfld: (WorkflowResult) -> String) {

        wrsPlot(
            titleS = "$filename failurePct",
            subtitleS = subtitle,
            data,
            "$dir/${filename}Failures",
            "theta",
            "failurePct",
            catName,
            xfld = { it.Dparam("theta") },
            yfld = { it.failPct },
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

    fun showNroundsVsTheta(data: List<WorkflowResult>, subtitle: String? = null, catName: String, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]

        wrsPlot(
            titleS = "$filename number of audit rounds",
            subtitleS = subtitle ?: "N=${exemplar.Nc} nruns=${exemplar.parameters["nruns"]!!}",
            data,
            "$dir/${filename}Nrounds",
            "theta",
            "auditRounds",
            catName,
            xfld = { it.Dparam("theta") },
            yfld = { it.nrounds},
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

    fun showSampleSizesVsFuzzPct(data: List<WorkflowResult>, catName: String, yscale: ScaleTypeOld = ScaleTypeOld.Linear, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!

        wrsPlot(
            titleS = "$filename samples needed",
            subtitleS = "margin=${exemplar.margin} N=${exemplar.Nc} nruns=${nruns}",
            data,
            "$dir/${filename}${yscale.name}",
            xname="fuzzPct",
            yscale.desc("samplesNeeded"),
            catName=catName,
            xfld = { it.Dparam("fuzzPct") },
            yfld = { it: WorkflowResult -> when (yscale) {
                ScaleTypeOld.Linear -> it.samplesNeeded
                ScaleTypeOld.Log -> log10(it.samplesNeeded)
                ScaleTypeOld.Pct -> (100*it.samplesNeeded/it.Nc.toDouble())
            }},
            catfld = catfld,
        )
    }

    fun showFailuresVsFuzzPct(data: List<WorkflowResult>, catName: String, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!

        wrsPlot(
            titleS = "$filename failurePct",
            subtitleS = "margin=${exemplar.margin} N=${exemplar.Nc} nruns=${nruns}",
            data,
            "$dir/${filename}Failures",
            "fuzzPct",
            "failurePct",
            catName,
            xfld = { it.Dparam("fuzzPct")},
            yfld = { it.failPct},
            catfld = catfld,
        )
    }

    fun showNroundsVsFuzzPct(data: List<WorkflowResult>, catName: String, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!

        wrsPlot(
            titleS = "$filename number of audit rounds",
            subtitleS = "margin=${exemplar.margin} N=${exemplar.Nc} nruns=${nruns}",
            data,
            "$dir/${filename}Nrounds",
            "fuzzPct",
            "auditRounds",
            catName,
            xfld = { it.Dparam("fuzzPct") },
            yfld = { it.nrounds},
            catfld = catfld,
        )
    }

    fun showSampleSizesVsUndervotePct(data: List<WorkflowResult>, writeName: String, catName: String, useLog: Boolean = true, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!

        wrsPlot(
            titleS = "$writeName samples needed",
            subtitleS = "margin=${df(exemplar.margin)} N=${exemplar.Nc} nruns=${nruns}",
            data,
            if (useLog) "$dir/${writeName}Log" else "$dir/${writeName}Linear",
            "underVotePct",
            if (useLog) "log10(samplesNeeded)" else "samplesNeeded",
            catName,
            xfld = { it.Dparam("undervote") },
            yfld = { if (useLog) log10(it.samplesNeeded) else it.samplesNeeded},
            catfld = catfld,
        )
    }

    fun showSampleSizesVsPhantomPct(data: List<WorkflowResult>, writeName: String, catName: String, useLog: Boolean = true, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!
        val mvrFuzz = exemplar.parameters["mvrFuzz"]!!

        wrsPlot(
            titleS = "$filename samples needed",
            subtitleS = "margin=${df(exemplar.margin)} N=${exemplar.Nc} nruns=${nruns} mvrFuzz=${mvrFuzz}",
            data,
            if (useLog) "$dir/${writeName}Log" else "$dir/${writeName}Linear",
            "phantomPct",
            if (useLog) "log10(samplesNeeded)" else "samplesNeeded",
            catName,
            xfld = { it.Dparam("phantom") },
            yfld = { if (useLog) log10(it.samplesNeeded) else it.samplesNeeded},
            catfld = catfld,
        )
    }

    fun showFailuresVsPhantomPct(data: List<WorkflowResult>, catName: String, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!
        val mvrFuzz = exemplar.parameters["mvrFuzz"]!!

        wrsPlot(
            titleS = "$filename failurePct",
            subtitleS = "margin=${df(exemplar.margin)} N=${exemplar.Nc} nruns=${nruns} mvrFuzz=${mvrFuzz}",
            data,
            "$dir/${filename}Failures",
            "phantomPct",
            "failurePct",
            catName,
            xfld = { it.Dparam("phantom") },
            yfld = { it.failPct},
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

fun categorySimFuzzVersion(wr: WorkflowResult): String {
    val diff =  categoryFuzzDiff(wr)
    val ver = wr.parameters["cat"]
    return "ver$ver ${diff}%"
}

fun categorySimFuzzCat(wr: WorkflowResult): String {
    val diff =  categoryFuzzDiff(wr)
    val cat = wr.parameters["cat"]
    return "$cat ${diff}%"
}

