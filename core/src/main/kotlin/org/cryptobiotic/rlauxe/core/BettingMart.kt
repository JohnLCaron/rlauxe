package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.doubleIsClose

/**
 * Finds the betting martingale for the hypothesis that the population mean is less than or equal to t,
 * for a population of size Nc, based on a series of draws x.
 */
class BettingMart(
    val bettingFn : BettingFn,
    val Nc: Int,             // max number of cards for this contest, only used by populationMeanIfH0
    val withoutReplacement: Boolean = true,
    val noerror: Double, // for comparison assorters who need rate counting. set to 0 for polling
    val riskLimit: Double = 0.05, // α ∈ (0, 1)
    val upperBound: Double,  // aka u
): RiskTestingFn {
    private val showEachSample = false

    init {
        require(riskLimit > 0.0 && riskLimit < 1.0 )
        require(upperBound > 0.0)
    }

    // run until sampleNumber == maxSample (batch mode) or terminateOnNullReject (ballot at a time)
    override fun testH0(maxSamples: Int,
                        terminateOnNullReject: Boolean,
                        showSequences: Boolean,
                        startingTestStatistic: Double,
                        drawSample : () -> Double) : TestH0Result {
        require(maxSamples <= Nc)

        var sampleNumber = 0        // – j ← 0: sample number
        var testStatistic = startingTestStatistic     // – T ← 1: test statistic
        var mj = 0.5                // – m = µ_j = 1/2: population mean under the null hypothesis = H0
        val prevSamples = PrevSamplesWithRates(noerror) // – S ← 0: sample sum

        val bets = mutableListOf<Double>()  // for some tests, could remove in production
        val pvalues = mutableListOf<Double>()

        // keep sequences for debugging, when showSequences is true
        val xs = mutableListOf<Double>()
        val etas = mutableListOf<Double>()
        val tjs = mutableListOf<Double>()
        val testStatistics = mutableListOf<Double>()

        while (sampleNumber < maxSamples) {
            val xj: Double = drawSample()
            sampleNumber++
            require(xj >= 0.0)
            require(xj <= upperBound)

            val lamj = bettingFn.bet(prevSamples)
            bets.add(lamj)

            // population mean under the null hypothesis
            mj = populationMeanIfH0(Nc, withoutReplacement, prevSamples)
            val eta = lamToEta(lamj, mu=mj, upper=upperBound)
            //println(" testH0: lamj=$lamj eta=$eta mean=$mj upperBound=$upperBound round=${lamToEta(lamj, mj, upperBound)}")

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
                // terms[i] = (1 + λi (Xi − µi )) ALPHA eq 10
                val ttj = 1.0 + lamj * (xj - mj) // (1 + λi (Xi − µi )) ALPHA eq 10, SmithRamdas eq 34 (WoR)
                if (doubleIsClose(ttj, 0.0)) 1.0 else ttj // 4
            }
            testStatistic *= tj // Tj ← Tj-1 & tj

            if (showSequences) {
                xs.add(xj)
                etas.add(eta)
                tjs.add(tj)
                testStatistics.add(testStatistic)
            }

            if (showEachSample) println("    bet=${df(lamj)} (eta=${df(eta)}) $sampleNumber: $xj tj=${df(tj)} Tj=${df(testStatistic)} pj=${df(1/testStatistic)}")

            // – S ← S + Xj
            prevSamples.addSample(xj)

            val pvalue = 1.0 / testStatistic
            pvalues.add(pvalue)

            if (terminateOnNullReject && (pvalue < riskLimit)) {
                break
            }
        }

        if (showSequences) {
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
            else -> TestH0Status.LimitReached
        }

        return TestH0Result(status, sampleNumber, prevSamples.mean(), pvalues, bets, prevSamples.errorRates())
    }
}
