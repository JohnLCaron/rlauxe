package org.cryptobiotic.rlauxe.rlaplots

fun main() {
    val wtf: ClosedFloatingPointRange<Double> = 0.5..0.52
    //comparison99("/home/stormy/temp/sim/failures/comparison99.svg", 0.48..0.52)
    //comparison99("/home/stormy/temp/sim/failures/comparison99false.svg", 0.49..0.50)

    listOf(1.4, 1.5, 1.6, 1.7, 1.8).forEach { eta0Factor->
        testChooseDF(
            "/home/stormy/temp/sim/dvalues/testChooseDF.csv",
            "/home/stormy/temp/sim/dvalues/testChooseDF.${eta0Factor}.svg",
            thetaFilter = 0.495..0.5, d = 5000, eta0Factor = eta0Factor
        )
    }

    testChooseD(
        "/home/stormy/temp/sim/dvalues/testChooseDF.csv",
        "/home/stormy/temp/sim/dvalues/testChooseD.svg",
        thetaFilter=0.495..0.5, reportedMeanDiff = -0.01, eta0Factor=1.8)
    testChooseF(
        "/home/stormy/temp/sim/dvalues/testChooseDF.csv",
        "/home/stormy/temp/sim/dvalues/testChooseF.svg",
        thetaFilter=0.495..0.5, reportedMeanDiff = -0.01, d=5000)

    /*

    testChooseDF(
        "/home/stormy/temp/sim/dvalues/testChooseDFAcc.csv",
        "/home/stormy/temp/sim/dvalues/testChooseDFAcc1.2.svg",
        thetaFilter=0.495..0.52, eta0Factor=1.2)
    testChooseDF(
        "/home/stormy/temp/sim/dvalues/testChooseDFAcc.csv",
        "/home/stormy/temp/sim/dvalues/testChooseDFAcc1.3.svg",
        thetaFilter=0.495..0.52, eta0Factor=1.3)
    testChooseDF(
        "/home/stormy/temp/sim/dvalues/testChooseDFAcc.csv",
        "/home/stormy/temp/sim/dvalues/testChooseDFAcc1.4.svg",
        thetaFilter=0.495..0.52, eta0Factor=1.4)

     */
}

fun comparison99(saveFile: String, thetaFilter: ClosedRange<Double>? = null, reportedMeanDiff: Double? = null) {
    val filename = "/home/stormy/temp/sim/failures/comparison99.csv"
    val srts: List<SRT> = readAndFilter(filename, thetaFilter, reportedMeanDiff = reportedMeanDiff)

    val ntrials = srts[0].ntrials
    val N = srts[0].N
    val eta0Factor = srts[0].eta0Factor
    val d = srts[0].d

    srtPlot(
        titleS = "Comparison Audit: successRLAs at 40% cutoff",
        subtitleS = "for N=$N d=$d eta0Factor=$eta0Factor ntrials=$ntrials",
        srts,
        saveFile = saveFile,
        "theta", "pctSuccess", "reportedMeanDiff",
        xfld = { it.theta },
        yfld = { extractDecile(it, 40) },
        catfld = { it.reportedMeanDiff },
    )
}

fun testChooseDF(input: String, saveFile: String,
                 thetaFilter: ClosedRange<Double>? = null,
                 d: Int?=null, eta0Factor: Double?=null, ) {
    val srts: List<SRT> = readAndFilter(input, thetaFilter, d=d, eta0Factor=eta0Factor)

    val ntrials = srts[0].ntrials
    val N = srts[0].N
    val eta0Factor = srts[0].eta0Factor
    val d = srts[0].d

    srtPlot(
        titleS = "Comparison Accelerated: successRLAs at 20% cutoff",
        subtitleS = "for N=$N d=$d eta0Factor=$eta0Factor ntrials=$ntrials",
        srts,
        saveFile = saveFile,
        "theta", "pctSuccess", "reportedMeanDiff",
        xfld = { it.theta },
        yfld = { extractDecile(it, 20) },
        catfld = { it.reportedMeanDiff },
    )
}

fun testChooseD(input: String, saveFile: String, thetaFilter: ClosedRange<Double>? = null, reportedMeanDiff: Double, eta0Factor: Double) {
    val srts: List<SRT> = readAndFilter(input, thetaFilter, reportedMeanDiff=reportedMeanDiff, eta0Factor=eta0Factor)

    val ntrials = srts[0].ntrials
    val N = srts[0].N
    val eta0Factor = srts[0].eta0Factor

    srtPlot(
        titleS = "Comparison Audit: successRLAs at 20% cutoff",
        subtitleS = "for N=$N eta0Factor=$eta0Factor ntrials=$ntrials, reportedMeanDiff=$reportedMeanDiff",
        srts,
        saveFile = saveFile,
        "theta", "pctSuccess", "d",
        xfld = { it.theta },
        yfld = { extractDecile(it, 20) },
        catfld = { it.d.toDouble() },
    )
}

fun testChooseF(input: String, saveFile: String, thetaFilter: ClosedRange<Double>? = null, reportedMeanDiff: Double, d: Int) {
    val srts: List<SRT> = readAndFilter(input, thetaFilter, reportedMeanDiff=reportedMeanDiff, d=d)

    val ntrials = srts[0].ntrials
    val N = srts[0].N
    val d = srts[0].d

    srtPlot(
        titleS = "Comparison Audit: successRLAs at 20% cutoff",
        subtitleS = "for N=$N d=$d ntrials=$ntrials, reportedMeanDiff=$reportedMeanDiff",
        srts,
        saveFile = saveFile,
        "theta", "pctSuccess", "eta0Factor",
        xfld = { it.theta },
        yfld = { extractDecile(it, 20) },
        catfld = { it.eta0Factor },
    )
}