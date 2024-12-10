package org.cryptobiotic.rlauxe.plots

import org.cryptobiotic.rlauxe.rlaplots.SRT
import org.cryptobiotic.rlauxe.rlaplots.di
import org.cryptobiotic.rlauxe.rlaplots.readFilterTN
import org.cryptobiotic.rlauxe.rlaplots.srtPlot
import kotlin.collections.List

// CANDIDATE FOR REFACTOR

fun main() {
    //showTNwithBravo("/home/stormy/temp/sim/dvalues/pollingAlpha.csv", .53, 10000, "/home/stormy/temp/sim/dvalues/pollingBravo.csv")
    plotTNwithBravo("/home/stormy/temp/sim/dvalues/comparisonAlpha9.csv", .53, 10000, "/home/stormy/temp/sim/dvalues/comparisonAlpha9")
}

fun plotTNwithBravo(filename: String, theta: Double, Nc: Int, saveFile: String) {
    val alphaSrts: List<SRT> = readFilterTN(filename, theta, Nc)

    val ntrials = alphaSrts[0].ntrials
    val N = alphaSrts[0].Nc

    srtPlot(
        titleS = "Polling Audit: pct votes sampled",
        subtitleS = "for N=$N theta=$theta ntrials=$ntrials",
        alphaSrts,
        "${saveFile}${theta}-${N}n.svg",
        "reportedMeanDiff", "pctSamples", "d",
        xfld = { it.reportedMeanDiff },
        yfld = { it.pctSamples },
        catfld = { di(it.d) },
        readFilterTN("/home/stormy/temp/sim/dvalues/pollingBravo.csv", theta, N).sortedBy { it.reportedMeanDiff },
    )
}