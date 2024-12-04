package org.cryptobiotic.rlauxe.rlaplots

import org.cryptobiotic.rlauxe.util.df
import kotlin.math.log10

class PlotSampleSizes(val dir: String, val filename: String) {
    fun showSamples(catfld: (SRT) -> String) {
        val reader = SRTcsvReader("$dir/${filename}.cvs")
        showSamples(reader.readCalculations(), catfld)
    }

    fun showSamples(data: List<SRT>, catfld: (SRT) -> String) {
        val ntrials = data[0].ntrials
        val N = data[0].N

        srtPlot(
            titleS = "$filename estimated sample sizes",
            subtitleS = "for ntrials=$ntrials, N=$N",
            data,
            "$dir/${filename}.html",
            "margin", "log10(nsamples)", "type",
            xfld = { it.reportedMargin },
            yfld = { log10(it.wsamples) },
            catfld = catfld,
        )
    }

    fun showFuzzedSamples() {
        val reader = SRTcsvReader("$dir/${filename}.cvs")
        showFuzzedSamples(reader.readCalculations())
    }

    fun showFuzzedSamples(data: List<SRT>) {
        val ntrials = data[0].ntrials
        val N = data[0].N

        srtPlot(
            titleS = "$filename estimated sample sizes with fuzzed sampling",
            subtitleS = "for ntrials=$ntrials, N=$N",
            data,
            "$dir/${filename}.html",
            "margin", "log10(nsamples)", "fuzzPct",
            xfld = { it.reportedMargin },
            yfld = { log10(it.wsamples) },
            catfld = { df(it.fuzzPct) },
        )
    }
}