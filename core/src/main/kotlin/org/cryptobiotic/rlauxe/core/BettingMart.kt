package org.cryptobiotic.rlauxe.core

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.betting.BettingFn
import org.cryptobiotic.rlauxe.betting.populationMeanIfH0
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.doublePrecision

/**
 * The betting martingale for the hypothesis that the population mean is less than or equal to 1/2,
 * for a population of size N, based on a series of samples x.
 */
class BettingMart(
    val bettingFn : BettingFn,
    val N: Int,             // diluted number of cards for this contest, only used by populationMeanIfH0
    val withoutReplacement: Boolean = true,
    val tracker: SampleTracker,
    val riskLimit: Double = 0.05, // α ∈ (0, 1)
    val sampleUpperBound: Double,  // the upper bound of the values of the sequence; bassort for CLCA
): RiskTestingFn {
    private val showEachSample = false
    private val sequences = DebuggingSequences()

    init {
        require(riskLimit > 0.0 && riskLimit < 1.0 )
        require(sampleUpperBound > 0.5)
    }

    // run until sampleNumber == maxSample (batch mode) or terminateOnNullReject (ballot at a time)
    override fun testH0(maxSamples: Int,
                        terminateOnNullReject: Boolean,
                        startingTestStatistic: Double,
                        drawSample : () -> Double) : TestH0Result {
        // require(!withoutReplacement || maxSamples <= Nc) TODO problems with redacted cvrs ? too many undervotes ??

        var sampleNumber = 0        // – j ← 0: sample number
        var testStatistic = startingTestStatistic     // – T ← 1: test statistic
        var mj = 0.5                // – m = µ_j = 1/2: population mean under the null hypothesis = H0

        var pvalueLast = 1.0
        var pvalueMin = 1.0

        if (showEachSample) println("** $sampleNumber: Tj=${df(testStatistic)} pj=${df(1/testStatistic)}")

        while (sampleNumber < maxSamples) {
            // population mean under the null hypothesis
            mj = populationMeanIfH0(N, withoutReplacement, tracker)  // approx .5
            // println("$sampleNumber: mj = $mj numer= ${(N * 0.5 - tracker.sum())} denom = ${(N - tracker.numberOfSamples())} ")

            // make sure mj is in bounds
            if (mj > sampleUpperBound || mj < 0.0) { // 1, 5
                populationMeanIfH0(N, withoutReplacement, tracker) // debug
                break
            }

            // choose the bet before you sample
            val lamj = bettingFn.bet(tracker)

            val xj: Double = drawSample()
            sampleNumber++
            require(xj >= 0.0)
            require(xj <= sampleUpperBound)

            // val eta = lamToEta(lamj, mu=mj, upper=sampleUpperBound) // informational only

            // rlabelgium Nonnegmean line 163
            //         terms[m>u] = 0                                       # true mean is certainly less than hypothesized
            //        terms[np.isclose(0, m, atol=atol)] = 1               # ignore
            //        terms[np.isclose(u, m, atol=atol, rtol=rtol)] = 1    # ignore
            //        terms[np.isclose(0, terms, atol=atol)] = 1           # martingale effectively vanishes; p-value 1
            //        terms[m<0] = np.inf                                  # true mean certainly greater than hypothesized
            //        terms[-1] = (np.inf if Stot > N*t else terms[-1])    # final sample makes the total greater than the null

            // SHANGRLA NonnegMean line 226
            // 1       terms[m > u] = 0                                   # true mean is certainly less than hypothesized
            // 2       terms[np.isclose(0, m, atol=atol)] = 1             # ignore
            // 3       terms[np.isclose(u, m, atol=atol, rtol=rtol)] = 1  # ignore
            // 4       terms[np.isclose(0, terms, atol=atol)] = (
            //            1                                             # martingale effectively vanishes; p-value 1
            //        )
            // 5       terms[m < 0] = np.inf                            # true mean certainly greater than hypothesized
            // 6       terms[-1] = (
            //            np.inf if Stot > N * t else terms[-1]
            //        )                                                 # final sample makes the total greater than the null

            // 1           m[i] > u -> terms[i] = 0.0   # true mean is certainly less than 1/2
            // 2           isCloseToZero(m[i], atol) -> terms[i] = 1.0
            // 3           isCloseToU(m[i], u, atol, rtol) -> terms[i] = 1.0
            // 4           isCloseToZero(terms[i], atol) -> terms[i] = 1.0 TODO wtf ?? prevent stalls ??
            // 5           m[i] < 0 -> terms[i] = Double.POSITIVE_INFINITY # true mean certainly greater than 1/2
            // 6           else -> terms[i] = if (Stot > N * t) Double.POSITIVE_INFINITY else terms[i]


            val tj = if (doubleIsClose(0.0, mj) || doubleIsClose(sampleUpperBound, mj)) { // 2, 3
                1.0
            } else {
                // terms[i] = (1 + λi (Xi − µi )) ALPHA eq 10 // approx (1 + lam * (xj - .5))
                val ttj = 1.0 + lamj * (xj - mj) // (1 + λi (Xi − µi )) ALPHA eq 10, SmithRamdas eq 34 (WoR)
                if (doubleIsClose(ttj, 0.0, doublePrecision)) {
                    logger.warn {"stalled audit: assort=$xj, lamda=$lamj, tj=$ttj, Tj-1=$testStatistic Tj=${testStatistic * ttj}"}
                }
                // if (doubleIsClose(ttj, 0.0)) 1.0 else ttj // 4  TODO this is why optimalBet is working so well
                ttj
            }

            testStatistic *= tj // Tj ← Tj-1 & tj

            if (sequences.isOn) {
                sequences.add(xj, lamj, mj, tj, testStatistic)
            }
            if (showEachSample) println("** $sampleNumber: ${df(xj)} bet=${df(lamj)} tj=${df(tj)} Tj=${df(testStatistic)} pj=${df(1/testStatistic)}")
            // if (sampleNumber % 1000 == 0)
            //    println(sampleNumber)

            // – S ← S + Xj
            tracker.addSample(xj)
            pvalueLast = 1.0 / testStatistic
            if (pvalueLast < pvalueMin) pvalueMin = pvalueLast

            if (terminateOnNullReject && (pvalueLast < riskLimit)) {
                break
            }
        }

        // if you have sampled the entire population, then you know if it passed
        val status = if (sampleNumber == N) {
            if (tracker.mean() > 0.5) TestH0Status.SampleSumRejectNull else TestH0Status.AcceptNull
        } else {
            when {
                (pvalueLast < riskLimit) -> TestH0Status.StatRejectNull
                (mj > sampleUpperBound) -> TestH0Status.AcceptNull // 1  # true mean is certainly less than 1/2
                (mj < 0.0) -> TestH0Status.SampleSumRejectNull     // 5 # true mean certainly greater than 1/2
                else -> TestH0Status.LimitReached
            }
        }
        // println(" status=$status mean = ${tracker.mean()} samplesUsed = ${sampleNumber/Nc.toDouble()}")

        // data class TestH0Result(
        //    val status: TestH0Status,  // how did the test conclude?
        //    val sampleCount: Int,      // number of samples used in testH0
        //    val sampleFirstUnderLimit: Int, // first sample index with pvalue with risk < limit, one based
        //    val pvalueMin: Double,    // smallest pvalue in the sequence
        //    val pvalueLast: Double,    // last pvalue
        //    val tracker: SampleTracker,
        //)
        return TestH0Result(status, sampleCount=sampleNumber, pvalueMin, pvalueLast, tracker)
    }

    fun setDebuggingSequences(): DebuggingSequences {
        this.sequences.isOn = true
        return this.sequences
    }

    companion object {
        private val logger = KotlinLogging.logger("BettingMart")
    }
}

class DebuggingSequences {
    var isOn = false
    val xs = mutableListOf<Double>()
    val bets = mutableListOf<Double>()
    val mjs = mutableListOf<Double>()
    val tjs = mutableListOf<Double>()
    val testStatistics = mutableListOf<Double>()

    fun add(x: Double, bet: Double, mj: Double, tj: Double, testStatistic: Double) {
        this.xs.add(x)
        this.bets.add(bet)
        this.mjs.add(mj)
        this.tjs.add(tj)
        this.testStatistics.add(testStatistic)
    }

    // That is, min(1, 1/Tj ) is an “anytime P -value” for the composite null hypothesis θ ≤ µ. ALPHA (9)
    // TODO so probably should be min (1, 1 / testStatistic)
    //   but unsure of the implications for muliple round sampling
    fun pvalues(): List<Double> {
        return testStatistics.map { 1.0 / it }
    }
}