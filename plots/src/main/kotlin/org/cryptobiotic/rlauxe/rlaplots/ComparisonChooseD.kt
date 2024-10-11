package org.cryptobiotic.rlauxe.rlaplots

fun main() {
    //plotSuccessVsTheta()
    //plotFailuresVsTheta()
    plotNSvsMD()
}

fun plotSuccessVsTheta() {
    val filename = "/home/stormy/temp/sim/full/comparisonChooseD1.csv"
    val thetaFilter: ClosedFloatingPointRange<Double> = 0.5001.. .506
    val srts: List<SRT> = readAndFilter(filename, thetaFilter)
    val ntrials = srts[0].ntrials
    val N = srts[0].N
    val eta0Factor = srts[0].eta0Factor
    val d = srts[0].d

    srtPlot(
        "Comparison Audit: % success at 20% cutoff",
        "for N=$N eta0Factor=$eta0Factor ntrials=$ntrials d=$d",
        srts,
        "/home/stormy/temp/sim/dvalues/plotSuccessVsTheta.svg",
        "theta", "pctSuccess", "reportedMeanDiff",
        xfld = { it.theta },
        yfld = { extractDecile(it, 20) },
        catfld = { it.reportedMeanDiff },
    )
}

fun plotFailuresVsTheta() {
    val filename = "/home/stormy/temp/sim/full/comparisonChooseD1.csv"
    val thetaFilter: ClosedFloatingPointRange<Double> = 0.0.. .5
    val srts: List<SRT> = readAndFilter(filename, thetaFilter)
    val ntrials = srts[0].ntrials
    val N = srts[0].N
    val eta0Factor = srts[0].eta0Factor
    val d = srts[0].d

    srtPlot(
        "Comparison Audit: % false positives at 20% cutoff",
        "for N=$N eta0Factor=$eta0Factor ntrials=$ntrials d=$d",
        srts,
        "/home/stormy/temp/sim/dvalues/plotFailuresVsTheta.svg",
        "theta", "falsePositives%", "reportedMeanDiff",
        xfld = { it.theta },
        yfld = { extractDecile(it, 20) },
        catfld = { it.reportedMeanDiff },
    )
}

// incoherent as it stands, do not use
fun plotNSvsMD() {
    val filename = "/home/stormy/temp/sim/full/comparisonChooseD1.csv"
    val thetaFilter: ClosedFloatingPointRange<Double> = 0.48 ..0.52
    val srts: List<SRT> = readAndFilter(filename, thetaFilter)
    val ntrials = srts[0].ntrials
    val N = srts[0].N
    val eta0Factor = srts[0].eta0Factor
    val d = srts[0].d

    srtPlot(
        titleS = "Comparison Audit: n samples needed",
       subtitleS = "for N=$N eta0Factor=$eta0Factor ntrials=$ntrials d=$d",
        srts,
        "/home/stormy/temp/sim/dvalues/plotNSvsMD.dup.svg",
        "reportedMeanDiff", "nsamples", "theta",
        xfld = { it.reportedMeanDiff },
        yfld = { it.nsamples },
        catfld = { it.theta },
    )
}