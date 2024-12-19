package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.doubleIsClose

/**
 * Finds the betting martingale for the hypothesis that the population
 * mean is less than or equal to t,
 * for a population of size Nc, based on a series of draws x.
 */
class BettingMart(
    val bettingFn : BettingFn,
    val Nc: Int,             // max number of cards for this contest
    val withoutReplacement: Boolean = true,
    val noerror: Double, // for comparison assorters who need rate counting
    val riskLimit: Double = 0.05, // α ∈ (0, 1)
    val upperBound: Double,  // aka u
): RiskTestingFn {
    private val showDetail = false

    init {
        require(riskLimit > 0.0 && riskLimit < 1.0 )
        require(upperBound > 0.0)
    }

    // run until sampleNumber == maxSample (batch mode) or terminateOnNullReject (ballot at a time)
    override fun testH0(maxSamples: Int,
                        terminateOnNullReject: Boolean,
                        showDetails: Boolean,
                        startingTestStatistic: Double,
                        drawSample : () -> Double) : TestH0Result {
        if (maxSamples > Nc)
            println("wait")
        require(maxSamples <= Nc)

        var sampleNumber = 0        // – j ← 0: sample number
        var testStatistic = startingTestStatistic     // – T ← 1: test statistic
        var mj = 0.5                // – m = µ_j = 1/2: population mean under the null hypothesis = H0
        val prevSamples = PrevSamplesWithRates(noerror) // – S ← 0: sample sum

        // keep series for debugging, remove for production
        val xs = mutableListOf<Double>()
        val etas = mutableListOf<Double>()
        val bets = mutableListOf<Double>()
        val tjs = mutableListOf<Double>()
        val testStatistics = mutableListOf<Double>()
        val pvalues = mutableListOf<Double>()

        while (sampleNumber < maxSamples) {
            val xj: Double = drawSample()
            sampleNumber++ // j <- j + 1
            xs.add(xj)
            require(xj >= 0.0)
            require(xj <= upperBound)

            val lamj = bettingFn.bet(prevSamples)
            bets.add(lamj)

            // population mean under the null hypothesis
            mj = populationMeanIfH0(Nc, withoutReplacement, prevSamples)
            val eta = lamToEta(lamj, upperBound, mj)
            if (etaToLam(eta, upperBound, mj) != lamj) {
                etaToLam(eta, upperBound, mj)
            }
            etas.add(eta)

            // 1           m[i] > u -> terms[i] = 0.0   # true mean is certainly less than 1/2
            // 2           isCloseToZero(m[i], atol) -> terms[i] = 1.0
            // 3           isCloseToU(m[i], u, atol, rtol) -> terms[i] = 1.0
            // 4           isCloseToZero(terms[i], atol) -> terms[i] = 1.0
            // 5           m[i] < 0 -> terms[i] = Double.POSITIVE_INFINITY # true mean certainly greater than 1/2
            // 6           else -> terms[i] = if (Stot > N * t) Double.POSITIVE_INFINITY else terms[i]

            if (mj > upperBound || mj < 0.0) { // 1, 5
                break
            }
            val tj = if (doubleIsClose(0.0, mj) || doubleIsClose(upperBound, mj)) { // 2, 3
                1.0
            } else {
                // AlphaMart
                // val ttj = (xj * etaj / mj + (upperBound - xj) * (upperBound - etaj) / (upperBound - mj)) / upperBound // ALPHA eq 4

                // terms[i] = (1 + λi (Xi − µi )) ALPHA eq 10
                val ttj = 1.0 + lamj * (xj - mj) // (1 + λi (Xi − µi )) ALPHA eq 10, SmithRamdas eq 34 (WoR)
                if (doubleIsClose(ttj, 0.0)) 1.0 else ttj // 4
            }
            tjs.add(tj)
            testStatistic *= tj // Tj ← Tj-1 & tj
            testStatistics.add(testStatistic)

            if (showDetail) println("    $sampleNumber = $xj m=$mj lamj = $lamj tj=$tj, Tj = $testStatistic")

            // – S ← S + Xj
            prevSamples.addSample(xj)

            val pvalue = 1.0 / testStatistic
            pvalues.add(pvalue)

            if (terminateOnNullReject && (pvalue < riskLimit)) {
                break
            }
        }

        if (showDetails) {
            println("xs = ${xs}")
            println("bets = ${bets}")
            println("tjs = ${tjs}")
            println("Tjs = ${testStatistics}")
        }

        val pvalue = pvalues.last()
        val status = when {
            (pvalue < riskLimit) -> TestH0Status.StatRejectNull
            (mj < 0.0) -> TestH0Status.SampleSumRejectNull // 5
            (mj > upperBound) -> TestH0Status.AcceptNull
            else -> {
                val xr = xs.reversed() // debugging
                val retas = etas.reversed()
                val rbets = bets.reversed()
                val rtjs = tjs.reversed()
                val rTjs = testStatistics.reversed()
                TestH0Status.LimitReached
            }
        }

        return TestH0Result(status, sampleNumber, prevSamples.mean(), pvalues, bets, prevSamples.samplingErrors())
    }
}
