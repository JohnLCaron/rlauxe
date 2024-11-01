package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.doubleIsClose

/**
 * Finds the ALPHA martingale for the hypothesis that the population
 * mean is less than or equal to t using a martingale method,
 * for a population of size N, based on a series of draws x.
 * See Stark, 2022. ALPHA: Audit that Learns from Previous Hand-Audited Ballots
 * Derived from "alpha_mart" in SHANGRLA NonnegMean.py
 */
class AlphaMart(
    val estimFn : EstimFn,  // estimator of the population mean
    val N: Int,             // number of ballot cards
    val withoutReplacement: Boolean = true,
    val riskLimit: Double = 0.05, // α ∈ (0, 1)
    val upperBound: Double = 1.0,  // aka u
): RiskTestingFn {
    private val showDetail = false

    init {
        require(riskLimit > 0.0 && riskLimit < 1.0 )
        require(upperBound > 0.0)
    }

    // run until sampleNumber == maxSample (batch mode) or terminateOnNullReject (ballot at a time)
    override fun testH0(maxSample: Int, terminateOnNullReject: Boolean, showDetails: Boolean, drawSample : () -> Double) : TestH0Result {
        require(maxSample <= N)

        var sampleNumber = 0        // – j ← 0: sample number
        var testStatistic = 1.0     // – T ← 1: test statistic
        var sampleSum = 0.0        // – S ← 0: sample sum
        var mj = 0.5                 // – m = µ_j = 1/2: population mean under the null hypothesis = H0

        val prevSamples = PrevSamples()

        // keep series for debugging, remove for production
        val xs = mutableListOf<Double>()
        val mjs = mutableListOf<Double>()
        val pvalues = mutableListOf<Double>()
        val etajs = mutableListOf<Double>()
        val tjs = mutableListOf<Double>()
        val testStatistics = mutableListOf<Double>()
        val sampleSums = mutableListOf<Double>()
        sampleSums.add(sampleSum)

        while (sampleNumber < maxSample) {
            val xj: Double = drawSample()
            sampleNumber++ // j <- j + 1
            xs.add(xj)

            val etaj = estimFn.eta(prevSamples)
            etajs.add(etaj)

            mj = populationMeanIfH0(N, withoutReplacement, prevSamples)
            mjs.add(mj)

            // terms[m > u] = 0       # true mean is certainly less than 1/2
            // terms[m < 0] = np.inf  # true mean certainly greater than 1/2
            if (mj > upperBound || mj < 0.0) {
                break
            }

            // This is eq 4 of ALPHA, p.5 :
            //      T_j = T_j-1 * ((X_j * eta_j / µ_j) + (u - X_j) * (u - eta_j) / ( u - µ_j)) / u

            val tj = if (doubleIsClose(0.0, mj) || doubleIsClose(upperBound, mj)) {
                1.0 // TODO
            } else {
                val p1 = etaj / mj
                val term1 = xj * p1
                val p2 = (upperBound - etaj) / (upperBound - mj)
                val term2 = (upperBound - xj) * p2
                val term = (term1 + term2) / upperBound

                // ALPHA eq 4
                val ttj = (xj * etaj / mj + (upperBound - xj) * (upperBound - etaj) / (upperBound - mj)) / upperBound

                require(doubleIsClose(term, ttj))
                //        terms[np.isclose(0, terms, atol=atol)] = (
                //            1  # martingale effectively vanishes; p-value 1
                //        )
                // TODO or should this be a test on testStatistic ?
                if (doubleIsClose(ttj, 0.0)) 1.0 else ttj
            }
            tjs.add(tj)
            testStatistic *= tj // Tj ← Tj-1 & tj

            testStatistics.add(testStatistic)

            if (showDetail) println("    $sampleNumber = $xj sum= $sampleSum, m=$mj etaj = $etaj tj=$tj, Tj = $testStatistic")

            // – S ← S + Xj
            sampleSum += xj
            sampleSums.add(sampleSum)
            prevSamples.addSample(xj)

            val pvalue = 1.0 / testStatistic
            pvalues.add(pvalue)

            if (terminateOnNullReject && (pvalue < riskLimit)) {
                break
            }
        }

        if (showDetails) {
            println("xs = ${xs}")
            println("mujs = ${mjs}")
            println("etaj = ${etajs}")
            println("tjs = ${tjs}")
            println("Tjs = ${testStatistics}")
        }

        val status = when {
            (sampleNumber == maxSample) -> {
                TestH0Status.LimitReached
            }
            (mj < 0.0) -> {
                TestH0Status.SampleSum
            }
            (mj > upperBound) -> {
                TestH0Status.AcceptNull
            }
            else -> {
                TestH0Status.StatRejectNull
            }
        }

        val sampleMean = sampleSum / sampleNumber
        return TestH0Result(status, sampleNumber, sampleMean, pvalues, etajs, mjs)
    }
}
