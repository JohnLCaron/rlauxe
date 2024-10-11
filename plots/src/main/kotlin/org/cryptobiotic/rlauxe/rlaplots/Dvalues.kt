package org.cryptobiotic.rlauxe.rlaplots

import kotlin.collections.List

fun main() {
    //showTNwithBravo("/home/stormy/temp/sim/dvalues/pollingAlpha.csv", .53, 10000, "/home/stormy/temp/sim/dvalues/pollingBravo.csv")
    showTNwithBravo("/home/stormy/temp/sim/dvalues/comparisonAlpha9.csv", .53, 10000, "/home/stormy/temp/sim/dvalues/comparisonAlpha9")
}

fun showTNwithBravo(filename: String, theta: Double, N: Int, saveFile: String) {
    val alphaSrts: List<SRT> = readFilterTN(filename, theta, N)

    val ntrials = alphaSrts[0].ntrials
    val N = alphaSrts[0].N

    srtPlot(
        titleS = "Polling Audit: pct votes sampled",
        subtitleS = "for N=$N theta=$theta ntrials=$ntrials",
        alphaSrts,
        "${saveFile}${theta}-${N}n.svg",
        "reportedMeanDiff", "pctSamples", "d",
        xfld = { it.reportedMeanDiff },
        yfld = { it.pctSamples },
        catfld = { it.d.toDouble() },
        readFilterTN("/home/stormy/temp/sim/dvalues/pollingBravo.csv", theta, N).sortedBy { it.reportedMeanDiff },
    )
}