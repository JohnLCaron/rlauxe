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
) {
    private val showDetail = false

    init {
        require(riskLimit > 0.0 && riskLimit < 1.0 )
        require(upperBound > 0.0)
    }

    // run until sampleNumber == maxSample (batch mode) or terminateOnNullReject (ballot at a time)
    fun testH0(maxSample: Int, terminateOnNullReject: Boolean, showDetails: Boolean = false, drawSample : () -> Double) : TestH0Result {
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
            if (testStatistic == 0.0) {
               //  println("testStatistic == 0.0")
            }

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

// 3. Pseudo-algorithm for ballot-level comparison and ballot-polling audits
// • Set audit parameters:
//  – Select the risk limit α ∈ (0, 1)
//  - decide whether to sample with or without replacement.
//  – Set upper as appropriate for the assertion under audit.
//  – Set N to the number of ballot cards in the population of cards from which the sample is drawn.
// (the rest is all in the estimFn)
//  – Set η0
//    For polling audits, η0 could be the reported mean value of the assorter.
//	    For instance, for the assertion corresponding to checking whether w got more votes than ℓ,
//	      η0 = (Nw + Nc /2)/N , where Nw is the number of votes reported for w , Nℓ is the
//	   number of votes reported for ℓ, and Nc = N − Nw − Nℓ is the number of ballot cards
//	   reported to have a vote for some other candidate or no valid vote in the contest.
//     For comparison audits, η0 can be based on assumed or historical rates of overstatement errors.
//
//  – Define the function to update η based on the sample,
//	  e.g, η(i, X i−1 ) = ((d * η0 + S)/(d + i − 1) ∨ (eps(i) + µi )) ∧ u,    (2.5.2, eq 14, "truncated shrinkage")
//	     where S = Sum i−1 k=1 (Xk) is the sample sum of the first i − 1 draws
//	     and eps(i) = c/ sqrt(d + i − 1)
//	  set any free parameters in the function (e.g., d and c in this example). The only requirement is that
//	     η(i, X i−1 ) ∈ (µi , u), where µi := E(Xi |X i−1 ) is computed under the null.

// simple version of alpha_mart from ONEAUDIT
// def alpha_mart(x: np.array, N: int, mu: float=1/2, eta: float=1-np.finfo(float).eps, u: float=1,
//               estim: callable=shrink_trunc) -> np.array :
//    '''
//    Finds the ALPHA martingale for the hypothesis that the population
//    mean is less than or equal to t using a martingale method,
//    for a population of size N, based on a series of draws x.
//
//    The draws must be in random order, or the sequence is not a martingale under the null
//
//    If N is finite, assumes the sample is drawn without replacement
//    If N is infinite, assumes the sample is with replacement
//
//    Parameters
//    ----------
//    x : list corresponding to the data
//    N : int
//        population size for sampling without replacement, or np.infinity for sampling with replacement
//    mu : float in (0,1)
//        hypothesized fraction of ones in the population
//    eta : float in (t,1)
//        alternative hypothesized population mean
//    estim : callable
//        estim(x, N, mu, eta, u) -> np.array of length len(x), the sequence of values of eta_j for ALPHA
//
//    Returns
//    -------
//    terms : array
//        sequence of terms that would be a nonnegative supermartingale under the null
//    '''
//    S = np.insert(np.cumsum(x),0,0)[0:-1]  # 0, x_1, x_1+x_2, ...,
//    j = np.arange(1,len(x)+1)              # 1, 2, 3, ..., len(x)
//    m = (N*mu-S)/(N-j+1) if np.isfinite(N) else mu   # mean of population after (j-1)st draw, if null is true
//    etaj = estim(x, N, mu, eta, u)
//    with np.errstate(divide='ignore',invalid='ignore'):
//        term = (x*etaj/m + (u-x)*(u-etaj)/(u-m))/u
//        terms = np.cumprod((x*etaj/m + (u-x)*(u-etaj)/(u-m))/u)
//    terms[m<0] = np.inf
//    return terms