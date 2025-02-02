package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.rlaplots.SRT
import org.cryptobiotic.rlauxe.rlaplots.dd
import org.cryptobiotic.rlauxe.rlaplots.extractDecile
import org.cryptobiotic.rlauxe.rlaplots.readAndFilter
import org.cryptobiotic.rlauxe.rlaplots.srtPlot

// TODO remove

fun main() {
    val ac = CorlaPlot()
    ac.plotSuccessVsTheta()
    ac.plotSuccess20VsTheta()
    ac.plotFailuresVsTheta()
    ac.plotSuccess20VsThetaNarrow()
}

class CorlaPlot {
    val dir = "/home/stormy/temp/corla"
    val filename = "$dir/plotCorla1000.csv"

    fun plotSuccessVsTheta() {
        val thetaFilter: ClosedFloatingPointRange<Double> = 0.500001.. 1.0

        val srts: List<SRT> = readAndFilter(filename, thetaFilter)
        val ntrials = srts[0].ntrials
        val Nc = srts[0].Nc
        val p2prior = srts[0].p2prior
        val d2 = srts[0].d2

        srtPlot(
            "CorlaPlot: success avg nsamples",
            "for Nc=$Nc ntrials=$ntrials",
            srts,
            "$dir/CorlaPlot.plotSuccessVsTheta.${ntrials}",
            "theta", "nsamples", "p2oracle",
            xfld = { it.theta },
            yfld = { it.nsamples },
            catfld = { dd(it.p2oracle) },
        )
    }

    fun plotSuccess20VsTheta() {
        val srts: List<SRT> = readAndFilter(filename)
        val ntrials = srts[0].ntrials
        val Nc = srts[0].Nc
        val p2prior = srts[0].p2prior
        val d2 = srts[0].d2

        srtPlot(
            "CorlaPlot: % success at 20% cutoff",
            "for Nc=$Nc ntrials=$ntrials",
            srts,
            "$dir/CorlaPlot.plotSuccess20VsTheta.${ntrials}",
            "theta", "pctSuccess", "p2oracle",
            xfld = { it.theta },
            yfld = { extractDecile(it, 20) },
            catfld = { dd(it.p2oracle) },
        )
    }

    fun plotSuccess20VsThetaNarrow() {
        val thetaFilter: ClosedFloatingPointRange<Double> = 0.5.. .52
        val srts: List<SRT> = readAndFilter(filename, thetaFilter)

        val ntrials = srts[0].ntrials
        val Nc = srts[0].Nc
        val p2prior = srts[0].p2prior
        val d2 = srts[0].d2

        srtPlot(
            "CorlaPlot: % success at 20% cutoff",
            "for Nc=$Nc ntrials=$ntrials",
            srts,
            "$dir/CorlaPlot.plotSuccess20VsThetaNarrow.${ntrials}",
            "theta", "pctSuccess", "p2oracle",
            xfld = { it.theta },
            yfld = { extractDecile(it, 20) },
            catfld = { dd(it.p2oracle) },
        )
    }

    fun plotFailuresVsTheta() {
        val thetaFilter: ClosedFloatingPointRange<Double> = 0.0.. .5
        val srts: List<SRT> = readAndFilter(filename, thetaFilter)
        val ntrials = srts[0].ntrials
        val Nc = srts[0].Nc
        val p2prior = srts[0].p2prior
        val d2 = srts[0].d2

        srtPlot(
            "CorlaPlot: % false positives at 20% cutoff",
            "for Nc=$Nc ntrials=$ntrials",
            srts,
            "$dir/CorlaPlot.plotFailuresVsTheta.${ntrials}",
            "theta", "falsePositives%", "p2oracle",
            xfld = { it.theta },
            yfld = { extractDecile(it, 20) },
            catfld = { dd(it.p2oracle) },
        )
    }
}