package org.cryptobiotic.rlauxe.rlaplots

import org.cryptobiotic.rlauxe.workflow.WorkflowResult
import kotlin.math.log10

class WorkflowResultsPlotter(val dir: String, val filename: String) {

    fun showSampleSizesVsMargin(data: List<WorkflowResult>, catName: String, catfld: (WorkflowResult) -> String) {
        val exemplar = data[0]

        wrsPlot(
            titleS = "$filename estimated sample sizes",
            subtitleS = "N=${exemplar.N}",
            data,
            "$dir/${filename}",
            "margin", "log10(samplesNeeded)", catName,
            xfld = { it.margin },
            yfld = { log10(it.samplesNeeded.toDouble()) },
            catfld = catfld,
        )
    }
}