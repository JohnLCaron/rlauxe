package org.cryptobiotic.rlauxe.rlaplots

import kotlin.math.log10

class EstimateSampleSize(val dir: String, val filename: String) {
    fun showSamples() {
        val reader = SRTcsvReader("$dir/$filename")
        showSamples(reader.readCalculations())
    }

    fun showSamples(data: List<SRT>) {
        val ntrials = data[0].ntrials
        val N = data[0].N

        srtPlot(
            titleS = "sample size needed for audit",
            subtitleS = "for ntrials=$ntrials, N=$N",
            data,
            "$dir/EstimateSampleSize.html",
            "margin", "log10(nsamples)", "type",
            xfld = { it.reportedMargin },
            yfld = { log10(it.wsamples) },
            catfld = { if (it.isPolling) "polling" else "comparison"},
        )
    }
}