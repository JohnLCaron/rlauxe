package org.cryptobiotic.rlauxe.rlaplots

fun main() {
    val ac = AdaptiveComparison()
    ac.plotSuccessVsTheta()
    ac.plotSuccess20VsTheta()
    ac.plotFailuresVsTheta()
    ac.plotSuccess20VsThetaNarrow()
}

class AdaptiveComparison {
    val filename = "/home/stormy/temp/bet/plotAdaptiveComparison0001.csv"
    fun plotSuccessVsTheta() {
        val thetaFilter: ClosedFloatingPointRange<Double> = 0.500001.. 1.0

        val srts: List<SRT> = readAndFilter(filename, thetaFilter)
        val ntrials = srts[0].ntrials
        val N = srts[0].N
        val p2prior = srts[0].p2prior
        val d2 = srts[0].d2

        srtPlot(
            "AdaptiveComparison: success avg nsamples",
            "for N=$N ntrials=$ntrials p2prior=$p2prior d2=$d2",
            srts,
            "/home/stormy/temp/bet/AdaptiveComparisonPlot.plotSuccessVsTheta.${ntrials}.html",
            "theta", "nsamples", "p2oracle",
            xfld = { it.theta },
            yfld = { it.nsamples },
            catfld = { it.p2oracle },
        )
    }

    fun plotSuccess20VsTheta() {
        val srts: List<SRT> = readAndFilter(filename)
        val ntrials = srts[0].ntrials
        val N = srts[0].N
        val p2prior = srts[0].p2prior
        val d2 = srts[0].d2

        srtPlot(
            "AdaptiveComparison: % success at 20% cutoff",
            "for N=$N ntrials=$ntrials p2prior=$p2prior d2=$d2",
            srts,
            "/home/stormy/temp/bet/AdaptiveComparisonPlot.plotSuccess20VsTheta.${ntrials}.html",
            "theta", "pctSuccess", "p2oracle",
            xfld = { it.theta },
            yfld = { extractDecile(it, 20) },
            catfld = { it.p2oracle },
        )
    }

    fun plotSuccess20VsThetaNarrow() {
        val thetaFilter: ClosedFloatingPointRange<Double> = 0.5.. .52
        val srts: List<SRT> = readAndFilter(filename, thetaFilter)

        val ntrials = srts[0].ntrials
        val N = srts[0].N
        val p2prior = srts[0].p2prior
        val d2 = srts[0].d2

        srtPlot(
            "AdaptiveComparison: % success at 20% cutoff",
            "for N=$N ntrials=$ntrials p2prior=$p2prior d2=$d2",
            srts,
            "/home/stormy/temp/bet/AdaptiveComparisonPlot.plotSuccess20VsThetaNarrow.${ntrials}.html",
            "theta", "pctSuccess", "p2oracle",
            xfld = { it.theta },
            yfld = { extractDecile(it, 20) },
            catfld = { it.p2oracle },
        )
    }

    fun plotFailuresVsTheta() {
        val thetaFilter: ClosedFloatingPointRange<Double> = 0.0.. .5
        val srts: List<SRT> = readAndFilter(filename, thetaFilter)
        val ntrials = srts[0].ntrials
        val N = srts[0].N
        val p2prior = srts[0].p2prior
        val d2 = srts[0].d2

        srtPlot(
            "AdaptiveComparison: % false positives at 20% cutoff",
            "for N=$N ntrials=$ntrials p2prior=$p2prior d2=$d2",
            srts,
            "/home/stormy/temp/bet/AdaptiveComparisonPlot.plotFailuresVsTheta.${ntrials}.html",
            "theta", "falsePositives%", "p2oracle",
            xfld = { it.theta },
            yfld = { extractDecile(it, 20) },
            catfld = { it.p2oracle },
        )
    }
}