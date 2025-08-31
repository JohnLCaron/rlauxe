package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.doubleIsClose

/**
 * The betting martingale for the hypothesis that the population mean is less than or equal to 1/2,
 * for a population of size Nc, based on a series of samples x.
 */
class BettingMart(
    val bettingFn : BettingFn,
    val Nc: Int,             // max number of cards for this contest, only used by populationMeanIfH0
    val withoutReplacement: Boolean = true,
    val noerror: Double, // for comparison assorters who need rate counting. set to 0 for polling
    val riskLimit: Double = 0.05, // α ∈ (0, 1)
    val upperBound: Double,  // the upper bound of the values of the sequence.
): RiskTestingFn {
    private val showEachSample = false
    private val sequences = DebuggingSequences()

    init {
        require(riskLimit > 0.0 && riskLimit < 1.0 )
        require(upperBound > 0.5)
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
        val tracker = PrevSamplesWithRates(noerror) // – S ← 0: sample sum

        var pvalueLast = 1.0
        var pvalueMin = 1.0

        if (showEachSample) println("  $sampleNumber: Tj=${df(testStatistic)} pj=${df(1/testStatistic)}")

        while (sampleNumber < maxSamples) {
            val xj: Double = drawSample()
            sampleNumber++
            require(xj >= 0.0)
            require(xj <= upperBound)

            val lamj = bettingFn.bet(tracker)

            // population mean under the null hypothesis
            mj = populationMeanIfH0(Nc, withoutReplacement, tracker)
            val eta = lamToEta(lamj, mu=mj, upper=upperBound) // informational only

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

            if (sequences.isOn) {
                sequences.add(xj, lamj, eta, tj, testStatistic)
            }
            if (showEachSample) println("  $sampleNumber: $xj bet=${df(lamj)} tj=${df(tj)} Tj=${df(testStatistic)} pj=${df(1/testStatistic)}")
            // if (sampleNumber % 100 == 0)
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
        val status = if (sampleNumber == Nc) {
            if (tracker.mean() > 0.5) TestH0Status.SampleSumRejectNull else TestH0Status.AcceptNull
        } else {
            when {
                (pvalueLast < riskLimit) -> TestH0Status.StatRejectNull
                (mj < 0.0) -> TestH0Status.SampleSumRejectNull // 5
                (mj > upperBound) -> TestH0Status.AcceptNull // 1
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
}

class DebuggingSequences {
    var isOn = false
    val xs = mutableListOf<Double>()
    val bets = mutableListOf<Double>()
    val etas = mutableListOf<Double>()
    val tjs = mutableListOf<Double>()
    val testStatistics = mutableListOf<Double>()

    fun add(x: Double, bet: Double, eta: Double, tj: Double, testStatistic: Double) {
        this.xs.add(x)
        this.bets.add(bet)
        this.etas.add(eta)
        this.tjs.add(tj)
        this.testStatistics.add(testStatistic)
    }

    fun pvalues(): List<Double> {
        return testStatistics.map { 1.0 / it }
    }
}