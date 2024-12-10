package org.cryptobiotic.rlauxe.cobra

import org.cryptobiotic.rlauxe.rlaplots.SRT
import org.cryptobiotic.rlauxe.rlaplots.SRTcsvReader
import org.cryptobiotic.rlauxe.rlaplots.dd
import org.cryptobiotic.rlauxe.rlaplots.extractDecile
import org.cryptobiotic.rlauxe.rlaplots.readAndFilter
import org.cryptobiotic.rlauxe.rlaplots.srtPlot

fun main() {
    val ac = PlotCobraDetails(dir, filename)
    ac.plotSuccessVsTheta()
    ac.plotSuccess20VsTheta()
    ac.plotFailuresVsTheta()
    ac.plotSuccess20VsThetaNarrow()
}

val dir = "/home/stormy/temp/pvalues"
val filename = "plotAdaptiveComparison0001"

class PlotCobraDetails(val dir: String, val filename: String) {
    val pathname = "$dir/${filename}.csv"

    fun plotSuccessVsTheta() {
        val thetaFilter: ClosedFloatingPointRange<Double> = 0.500001.. 1.0

        val srts: List<SRT> = readAndFilter(pathname, thetaFilter)
        val ntrials = srts[0].ntrials
        val Nc = srts[0].Nc
        val p2prior = srts[0].p2prior
        val d2 = srts[0].d2

        srtPlot(
            "AdaptiveComparison: success avg nsamples",
            "for Nc=$Nc ntrials=$ntrials p2prior=$p2prior d2=$d2",
            srts,
            "$dir/${filename}.plotSuccessVsTheta",
            "theta", "nsamples", "p2oracle",
            xfld = { it.theta },
            yfld = { it.nsamples },
            catfld = { dd(it.p2oracle) },
        )
    }

    fun plotSuccess20VsTheta() {
        val srts: List<SRT> = readAndFilter(pathname)
        val ntrials = srts[0].ntrials
        val Nc = srts[0].Nc
        val p2prior = srts[0].p2prior
        val d2 = srts[0].d2

        srtPlot(
            "AdaptiveComparison: % success at 20% cutoff",
            "for Nc=$Nc ntrials=$ntrials p2prior=$p2prior d2=$d2",
            srts,
            "$dir/${filename}.plotSuccess20VsTheta",
            "theta", "pctSuccess", "p2oracle",
            xfld = { it.theta },
            yfld = { extractDecile(it, 20) },
            catfld = { dd(it.p2oracle) },
        )
    }

    fun plotSuccess20VsThetaNarrow() {
        val thetaFilter: ClosedFloatingPointRange<Double> = 0.5.. .52
        val srts: List<SRT> = readAndFilter(pathname, thetaFilter)

        val ntrials = srts[0].ntrials
        val Nc = srts[0].Nc
        val p2prior = srts[0].p2prior
        val d2 = srts[0].d2

        srtPlot(
            "AdaptiveComparison: % success at 20% cutoff",
            "for Nc=$Nc ntrials=$ntrials p2prior=$p2prior d2=$d2",
            srts,
            "$dir/${filename}.plotSuccess20VsThetaNarrow",
            "theta", "pctSuccess", "p2oracle",
            xfld = { it.theta },
            yfld = { extractDecile(it, 20) },
            catfld = { dd(it.p2oracle) },
        )
    }

    fun plotFailuresVsTheta() {
        val thetaFilter: ClosedFloatingPointRange<Double> = 0.0.. .5
        val srts: List<SRT> = readAndFilter(pathname, thetaFilter)
        val ntrials = srts[0].ntrials
        val Nc = srts[0].Nc
        val p2prior = srts[0].p2prior
        val d2 = srts[0].d2

        srtPlot(
            "AdaptiveComparison: % false positives at 20% cutoff",
            "for Nc=$Nc ntrials=$ntrials p2prior=$p2prior d2=$d2",
            srts,
            "$dir/${filename}.plotFailuresVsTheta",
            "theta", "falsePositives%", "p2oracle",
            xfld = { it.theta },
            yfld = { extractDecile(it, 20) },
            catfld = { dd(it.p2oracle) },
        )
    }
}