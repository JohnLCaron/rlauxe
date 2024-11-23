package org.cryptobiotic.rlauxe.core

// wrapper around BettingMart; only use for polling
class AlphaMart(
    val estimFn : EstimFn,  // estimator of the population mean
    val N: Int,             // max number of cards for this contest
    val withoutReplacement: Boolean = true,
    val riskLimit: Double = 0.05, // α ∈ (0, 1)
    val upperBound: Double = 1.0,  // aka u
): RiskTestingFn {
    val betting: BettingMart

    init {
        val bettingFn = EstimAdapter(N, withoutReplacement, upperBound, estimFn)
        betting =  BettingMart(bettingFn, N, withoutReplacement, 0.0, riskLimit, upperBound)
    }

    override fun testH0(
        maxSample: Int,
        terminateOnNullReject: Boolean,
        showDetails: Boolean,
        drawSample: () -> Double,
    ): TestH0Result {
        return betting.testH0(maxSample, terminateOnNullReject, showDetails, drawSample)
    }
}

/** Turn EstimFn into a BettingFn */
class EstimAdapter(
    val N: Int,
    val withoutReplacement: Boolean = true,
    val upperBound: Double,
    val estimFn : EstimFn,  // estimator of the population mean
): BettingFn {

    override fun bet(prevSamples: PrevSamplesWithRates): Double {
        // let bettingmart handle edge cases
        val mu = populationMeanIfH0(N, withoutReplacement, prevSamples)
        //if (upperBound < mu) {
        //    populationMeanIfH0(N, withoutReplacement, prevSamples)
        //}
        //require (upperBound >= mu)
        val eta = estimFn.eta(prevSamples)
        //require (upperBound > eta)
        return etaToLam(eta, mu, upperBound)
    }
}
