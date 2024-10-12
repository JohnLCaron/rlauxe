package org.cryptobiotic.rlauxe.rlaplots

fun main() {
    val ac = AdaptiveComparison()
    ac.plotSuccessVsMargin()
    ac.plotSuccess20VsMargin()
    ac.plotFailuresVsTheta()
}

class AdaptiveComparison {
    fun plotSuccessVsMargin() {
        val filename = "/home/stormy/temp/bet/plotAdaptiveComparison.csv"
        val thetaFilter: ClosedFloatingPointRange<Double> = 0.500001.. 1.0

        val srts: List<SRT> = readAndFilter(filename, thetaFilter)
        val ntrials = srts[0].ntrials
        val N = srts[0].N
        val p2prior = srts[0].p2prior
        val d2 = srts[0].d2

        srtPlot(
            "Comparison Audit: success nsamples",
            "for N=$N ntrials=$ntrials p2prior=$p2prior d2=$d2}",
            srts,
            "/home/stormy/temp/bet/plotAdaptiveComparison.plotSuccessVsMargin.${d2}.svg",
            "theta", "nsamples", "p2oracle",
            xfld = { it.theta },
            yfld = { it.nsamples },
            catfld = { it.p2oracle },
        )
    }

    fun plotSuccess20VsMargin() {
        val filename = "/home/stormy/temp/bet/plotAdaptiveComparison.csv"
        val srts: List<SRT> = readAndFilter(filename)
        val ntrials = srts[0].ntrials
        val N = srts[0].N
        val p2prior = srts[0].p2prior
        val d2 = srts[0].d2

        srtPlot(
            "Comparison Audit: % success at 20% cutoff",
            "for N=$N ntrials=$ntrials p2prior=$p2prior d2=$d2",
            srts,
            "/home/stormy/temp/bet/plotAdaptiveComparison.plotSuccess20VsMargin.${d2}.svg",
            "theta", "pctSuccess", "p2oracle",
            xfld = { it.theta },
            yfld = { extractDecile(it, 20) },
            catfld = { it.p2oracle },
        )
    }

    fun plotFailuresVsTheta() {
        val filename = "/home/stormy/temp/bet/plotAdaptiveComparison.csv"
        val thetaFilter: ClosedFloatingPointRange<Double> = 0.0.. .5
        val srts: List<SRT> = readAndFilter(filename, thetaFilter)
        val ntrials = srts[0].ntrials
        val N = srts[0].N
        val p2prior = srts[0].p2prior
        val d2 = srts[0].d2

        srtPlot(
            "Comparison Audit: % false positives at 20% cutoff",
            "for N=$N ntrials=$ntrials p2prior=$p2prior d2=$d2",
            srts,
            "/home/stormy/temp/bet/plotAdaptiveComparison.plotFailuresVsTheta.${d2}.svg",
            "theta", "falsePositives%", "p2oracle",
            xfld = { it.theta },
            yfld = { extractDecile(it, 20) },
            catfld = { it.p2oracle },
        )
    }
}