package org.cryptobiotic.rlauxe.rlaplots

import org.cryptobiotic.rlauxe.workflow.WorkflowResult
import kotlin.math.log10

class WorkflowResultsPlotter(val dir: String, val filename: String) {

    fun showSampleSizesVsMargin(data: List<WorkflowResult>, catName: String, useLog: Boolean = true, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]
        val nruns = exemplar.parameters["nruns"]!!.toDouble()

        wrsPlot(
            titleS = "$filename estimated sample sizes",
            subtitleS = "N=${exemplar.N} nruns=${nruns}",
            data,
            if (useLog) "$dir/${filename}Log" else "$dir/${filename}Linear",
            "margin", if (useLog) "log10(samplesNeeded)" else "samplesNeeded", catName,
            xfld = { it.margin },
            yfld = { if (useLog) log10(it.samplesNeeded) else it.samplesNeeded},
            catfld = catfld,
        )
    }
}