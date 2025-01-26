package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.rlaplots.SRT
import org.cryptobiotic.rlauxe.rlaplots.SRTcsvReader
import org.cryptobiotic.rlauxe.rlaplots.srtPlot
import org.cryptobiotic.rlauxe.util.df
import kotlin.math.log10

// TODO candidate for removal

class PlotSampleSizes(val dir: String, val filename: String) {
    fun showSamples(catfld: (SRT) -> String) {
        val reader = SRTcsvReader("$dir/${filename}.cvs")
        showSamples(reader.readCalculations(), catfld)
    }

    fun showSamples(data: List<SRT>, catfld: (SRT) -> String) {
        val ntrials = data[0].ntrials
        val Nc = data[0].Nc

        srtPlot(
            titleS = "$filename estimated sample sizes",
            subtitleS = "for ntrials=$ntrials, Nc=$Nc",
            data,
            "$dir/${filename}",
            "margin", "log10(nsamples)", "category",
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
        val Nc = data[0].Nc

        srtPlot(
            titleS = "$filename estimated sample sizes with fuzzed sampling",
            subtitleS = "for ntrials=$ntrials, N=$Nc",
            data,
            "$dir/${filename}",
            "margin", "log10(nsamples)", "fuzzPct",
            xfld = { it.reportedMargin },
            yfld = { log10(it.wsamples) },
            catfld = { df(it.fuzzPct) },
        )
    }

    fun showMeanDifference(catfld: (SRT) -> String) {
        val reader = SRTcsvReader("$dir/${filename}.cvs")
        showMeanDifference(reader.readCalculations(), catfld)
    }

    fun showMeanDifference(data: List<SRT>, catfld: (SRT) -> String) {
        val ntrials = data[0].ntrials
        val Nc = data[0].Nc

        srtPlot(
            titleS = "$filename estimated sample sizes vs mean difference",
            subtitleS = "for ntrials=$ntrials, N=$Nc",
            data,
            "$dir/${filename}",
            "% reported mean difference from theta", "nsamples", "category",
            xfld = { it.reportedMeanDiff },
            // yfld = { log10(it.wsamples) },
            yfld = { it.wsamples },
            catfld = catfld,
        )
    }
}