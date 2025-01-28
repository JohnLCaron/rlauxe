package org.cryptobiotic.rlauxe.rlaplots

import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.workflow.WorkflowResult
import kotlin.math.log10

enum class Scale { Linear, Log, Pct;
    fun desc(what: String): String =
        when (this) {
            Linear -> what
            Log -> "log10($what)"
            Pct -> "$what %"
        }
}

class WorkflowResultsPlotter(val dir: String, val filename: String) {

    fun showNmvrsVsMargin(data: List<WorkflowResult>, catName: String, yscale: Scale = Scale.Linear, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!
        val fuzzPct = exemplar.parameters["fuzzPct"]
        val fuzzPctLabel = if (fuzzPct != null) " fuzzPct=$fuzzPct" else ""

        wrsPlot(
            titleS = "$filename estimated number of MVRS",
            subtitleS = "Nc=${exemplar.Nc} nruns=${nruns.toInt()}" + fuzzPctLabel,
            data,
            "$dir/${filename}${yscale.name}",
            "margin",
            yscale.desc("number of Mvrs"),
            catName,
            xfld = { it.margin },
            yfld = { it: WorkflowResult -> when (yscale) {
                Scale.Linear -> it.nmvrs
                Scale.Log -> log10(it.nmvrs)
                Scale.Pct -> {
                    val Nb = it.parameters["Nb"] ?: it.Nc.toDouble()
                    (100*it.nmvrs/Nb)
                }
            }},
            catfld = catfld,
        )
    }

    fun showSampleSizesVsMargin(data: List<WorkflowResult>, catName: String, yscale: Scale = Scale.Linear, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!
        val fuzzPct = exemplar.parameters["fuzzPct"]
        val fuzzPctLabel = if (fuzzPct == null) "" else " fuzzPct=$fuzzPct"

        wrsPlot(
            titleS = "$filename estimated sample sizes",
            subtitleS = "Nc=${exemplar.Nc} nruns=${nruns.toInt()}" + fuzzPctLabel,
            data,
            "$dir/${filename}${yscale.name}",
            "margin",
            yscale.desc("samplesNeeded"),
            catName,
            xfld = { it.margin },
            yfld = { it: WorkflowResult -> when (yscale) {
                Scale.Linear -> it.samplesNeeded
                Scale.Log -> log10(it.samplesNeeded)
                Scale.Pct -> (100*it.samplesNeeded/it.Nc.toDouble())
            }},
            catfld = catfld,
        )
    }

    fun showEstSizesVsMargin(data: List<WorkflowResult>, catName: String, yscale: Scale = Scale.Linear, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!
        val fuzzPct = exemplar.parameters["fuzzPct"]
        val fuzzPctLabel = if (fuzzPct == null) "" else " fuzzPct=$fuzzPct"

        wrsPlot(
            titleS = "$filename nmvrs - samplesNeeded",
            subtitleS = "Nc=${exemplar.Nc} nruns=${nruns.toInt()}" + fuzzPctLabel,
            data,
            "$dir/${filename}${yscale.name}",
            "margin",
            yscale.desc("extra samples"),
            catName,
            xfld = { it.margin },
            yfld = { it: WorkflowResult -> when (yscale) {
                Scale.Linear -> (it.nmvrs - it.samplesNeeded)
                Scale.Log -> log10( (it.nmvrs - it.samplesNeeded))// needed?
                Scale.Pct -> (100* (it.nmvrs - it.samplesNeeded)/it.nmvrs )
            }},
            catfld = catfld,
        )
    }

    fun showFailuresVsMargin(data: List<WorkflowResult>, catName: String, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!
        val fuzzPct = exemplar.parameters["fuzzPct"]
        val fuzzPctLabel = if (fuzzPct == null) "" else " fuzzPct=$fuzzPct"

        wrsPlot(
            titleS = "$filename failurePct",
            subtitleS = "Nc=${exemplar.Nc} nruns=${nruns.toInt()}" + fuzzPctLabel,
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

    fun showNroundsVsMargin(data: List<WorkflowResult>, catName: String, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!

        wrsPlot(
            titleS = "$filename number of audit rounds",
            subtitleS = "margin=${exemplar.margin} N=${exemplar.Nc} nruns=${nruns.toInt()}",
            data,
            "$dir/${filename}Nrounds",
            "fuzzPct",
            "auditRounds",
            catName,
            xfld = { it.margin },
            yfld = { it.nrounds},
            catfld = catfld,
        )
    }

    fun showSampleSizesVsFuzzPct(data: List<WorkflowResult>, catName: String, yscale: Scale = Scale.Linear, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!

        wrsPlot(
            titleS = "$filename estimated sample sizes",
            subtitleS = "margin=${exemplar.margin} N=${exemplar.Nc} nruns=${nruns.toInt()}",
            data,
            "$dir/${filename}${yscale.name}",
            xname="fuzzPct",
            yscale.desc("samplesNeeded"),
            catName=catName,
            xfld = { it.parameters["fuzzPct"]!! },
            yfld = { it: WorkflowResult -> when (yscale) {
                Scale.Linear -> it.samplesNeeded
                Scale.Log -> log10(it.samplesNeeded)
                Scale.Pct -> (100*it.samplesNeeded/it.Nc.toDouble())
            }},
            catfld = catfld,
        )
    }

    fun showFailuresVsFuzzPct(data: List<WorkflowResult>, catName: String, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!

        wrsPlot(
            titleS = "$filename failurePct",
            subtitleS = "margin=${exemplar.margin} N=${exemplar.Nc} nruns=${nruns.toInt()}",
            data,
            "$dir/${filename}Failures",
            "fuzzPct",
            "failurePct",
            catName,
            xfld = { it.parameters["fuzzPct"]!! },
            yfld = { it.failPct},
            catfld = catfld,
        )
    }

    fun showNroundsVsFuzzPct(data: List<WorkflowResult>, catName: String, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!

        wrsPlot(
            titleS = "$filename number of audit rounds",
            subtitleS = "margin=${exemplar.margin} N=${exemplar.Nc} nruns=${nruns.toInt()}",
            data,
            "$dir/${filename}Nrounds",
            "fuzzPct",
            "auditRounds",
            catName,
            xfld = { it.parameters["fuzzPct"]!! },
            yfld = { it.nrounds},
            catfld = catfld,
        )
    }

    fun showSampleSizesVsUndervotePct(data: List<WorkflowResult>, catName: String, useLog: Boolean = true, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!

        wrsPlot(
            titleS = "$filename estimated sample sizes",
            subtitleS = "margin=${df(exemplar.margin)} N=${exemplar.Nc} nruns=${nruns.toInt()}",
            data,
            if (useLog) "$dir/${filename}Log" else "$dir/${filename}Linear",
            "underVotePct",
            if (useLog) "log10(samplesNeeded)" else "samplesNeeded",
            catName,
            xfld = { it.parameters["undervote"]!! },
            yfld = { if (useLog) log10(it.samplesNeeded) else it.samplesNeeded},
            catfld = catfld,
        )
    }

    fun showSampleSizesVsPhantomPct(data: List<WorkflowResult>, catName: String, useLog: Boolean = true, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!

        wrsPlot(
            titleS = "$filename estimated sample sizes",
            subtitleS = "margin=${df(exemplar.margin)} N=${exemplar.Nc} nruns=${nruns.toInt()}",
            data,
            if (useLog) "$dir/${filename}Log" else "$dir/${filename}Linear",
            "phantomPct",
            if (useLog) "log10(samplesNeeded)" else "samplesNeeded",
            catName,
            xfld = { it.parameters["phantom"]!! },
            yfld = { if (useLog) log10(it.samplesNeeded) else it.samplesNeeded},
            catfld = catfld,
        )
    }

    fun showFailuresVsPhantomPct(data: List<WorkflowResult>, catName: String, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!

        wrsPlot(
            titleS = "$filename failurePct",
            subtitleS = "margin=${df(exemplar.margin)} N=${exemplar.Nc} nruns=${nruns.toInt()}",
            data,
            "$dir/${filename}Failures",
            "phantomPct",
            "failurePct",
            catName,
            xfld = { it.parameters["phantom"]!! },
            yfld = { it.failPct},
            catfld = catfld,
        )
    }

}