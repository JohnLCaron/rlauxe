package org.cryptobiotic.rlauxe

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private val showDetail = false

// estimate the population mean for the jth sample from the previous j-1 samples
interface EstimFn {
    fun eta(prevSamples: List<Double>): Double
}

enum class TestH0Status {
    RejectNull,
    SampleSum, // SampleSum > N * t
    LimitReached,
}

data class TestH0Result(val status: TestH0Status, val sampleCount: Int, val sampleMean: Double, val pvalues: List<Double>)

private val eps = 2.220446049250313e-16

class AlphaMart(
    val estimFn : EstimFn,
    val N: Int, // number of ballot cards in the population of cards from which the sample is drawn
    val withoutReplacement: Boolean = true,
    risk_limit: Double = 0.05, // α ∈ (0, 1)
    val upperBound: Double = 1.0,  // aka u
) {
    val Tthreshold = 1.0 / risk_limit

    fun testH0(maxSample: Int, drawSample : () -> Double) : TestH0Result {
        var sampleNumber = 0        // – j ← 0: sample number
        var testStatistic = 1.0     // – T ← 1: test statistic
        var sampleSum = 0.0        // – S ← 0: sample sum
        var populationMeanIfH0 = 0.5 // – m = µ_j = 1/2: population mean under the null hypothesis = H0

        val sampleAssortValues = mutableListOf<Double>()

        val m = mutableListOf<Double>()
        val pvalues = mutableListOf<Double>()
        val etajs = mutableListOf<Double>()
        val testStatistics = mutableListOf<Double>()
        val sampleSums = mutableListOf<Double>()

        while (sampleNumber < maxSample) {
            val xj: Double = drawSample()
            sampleNumber++ // j <- j + 1

            val etaj = estimFn.eta(sampleAssortValues)
            etajs.add(etaj
            )
            sampleAssortValues.add(xj)

            populationMeanIfH0 = this.populationMeanIfH0(sampleNumber, sampleSum)
            m.add(populationMeanIfH0)

            val tj =
                (xj * etaj / populationMeanIfH0 + (upperBound - xj) * (upperBound - etaj) / (upperBound - populationMeanIfH0)) / upperBound
            testStatistic *= tj // Tj ← Tj-1 & tj
            testStatistics.add(testStatistic)
            if (showDetail) println("    $sampleNumber = $xj etaj = $etaj tj=$tj, Tj = $testStatistic")

            // TODO why ??
            //        terms[m > u] = 0  # true mean is certainly less than hypothesized
            //        terms[np.isclose(0, m, atol=atol)] = 1  # ignore
            //        terms[np.isclose(u, m, atol=atol, rtol=rtol)] = 1  # ignore
            //        terms[np.isclose(0, terms, atol=atol)] = (
            //            1  # martingale effectively vanishes; p-value 1
            //        )
            //        terms[m < 0] = np.inf  # true mean certainly greater than hypothesized
            //        terms[-1] = (
            //            np.inf if Stot > N * t else terms[-1]
            //        )  # final sample makes the total greater than the null
            //        iterms = 1 / terms
            //        miterms = np.minimum(1, iterms)
            //        return np.minimum(1, 1 / terms) // WHY?
            pvalues.add(1.0 / testStatistic)

            // – S ← S + Xj
            sampleSum += xj
            sampleSums.add(sampleSum)
        }

        val sampleMean = sampleSum / sampleNumber
        val status = when {
            (sampleSum > N * 0.5) -> TestH0Status.SampleSum
            (sampleNumber == maxSample) -> TestH0Status.LimitReached
            else -> TestH0Status.RejectNull
        }

        return TestH0Result(status, sampleNumber, sampleMean, pvalues)
    }

    fun populationMeanIfH0(sampleNum: Int, sampleSumMinusOne: Double): Double {
        // LOOK detect if it goes negetive. sampleNum < N. Neg if (N * t < sampleSum)
        return if (withoutReplacement) (N * 0.5 - sampleSumMinusOne) / (N - sampleNum + 1) else 0.5
    }
}

// ALPHA paper, "ALPHA: AUDIT THAT LEARNS FROM PREVIOUSLY HAND-AUDITED BALLOTS" 12 Aug 2022
class AlphaAlgorithm(
    val estimFn : EstimFn,
    val N: Int, // number of ballot cards in the population of cards from which the sample is drawn
    val withoutReplacement: Boolean = true,
    risk_limit: Double = 0.05, // α ∈ (0, 1)
    val upperBound: Double = 1.0,  // aka u
) {
    val Tthreshold = 1.0 / risk_limit

    init {
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
    }

    // drawSample() returns the assorter value
    // return the number of ballots sampled. if equal to maxSample, then the rla failed and must do a hand recount.
    // for debugging, we want to know the sample mean.
    fun testH0(maxSample: Int, drawSample : () -> Double) : TestH0Result {

        // • Initialize variables
        var sampleNumber = 0        // – j ← 0: sample number
        var testStatistic = 1.0     // – T ← 1: test statistic
        var sampleSum = 0.0        // – S ← 0: sample sum
        var populationMeanIfH0 = 0.5 // – m = µ_j = 1/2: population mean under the null hypothesis = H0

        var status = TestH0Status.RejectNull
        val sampleAssortValues = mutableListOf<Double>()
        val pvalues = mutableListOf<Double>()

        // • While sampleNumber < maxSample and T < 1/α:
        while (sampleNumber < maxSample && testStatistic < Tthreshold ) {
            // – Draw a ballot at random
            // – Determine Xj by applying the assorter to the selected ballot card (and the CVR, for comparison audits)
            val xj: Double = drawSample()
            sampleNumber++ // j <- j + 1

            // note using sample list not including current sample
            val etaj = estimFn.eta(sampleAssortValues)
            sampleAssortValues.add(xj)

            // – If the sample is drawn without replacement, m ← (N/2 − S)/(N − j + 1)
            // LOOK moved from the paper, where it was below S ← S + Xj.
            populationMeanIfH0 = this.populationMeanIfH0(sampleNumber, sampleSum)

            if (sampleSum > N * 0.5) { // aka populationMeanIfNull < 0
                status = TestH0Status.SampleSum // true mean certainly greater than null
                break
            }

            // This is eq 4 of ALPHA, p.5 :
            //      T_j = T_j-1 / u * ((X_j * eta_j / µ_j) + (u - X_j) * (u - eta_j) / ( u - µ_j))
            //  T is the "ALPHA supermartingale"
            //  u = upperBound
            //  X_j = jth sample
            //  eta_j = ηj = estimate of population mean for H1 = estimFn.eta(X^j-1)
            //  µ_j = m = The mean of the population after each draw for H0.

            // – If m < 0, T ← ∞. Otherwise, T ← T / u * ( Xj * η(j,S)/m + (u - Xj) * (u−η(j,S))/(u-m))
            //    already tested for m < 0
            //    tj = ( Xj * η(j,S)/m + (u - Xj) * (u−η(j,S))/(u-m)) / u
            //    Tj ← Tj-1 * tj
            val tj = (xj * etaj / populationMeanIfH0 + (upperBound - xj) * (upperBound - etaj) / (upperBound - populationMeanIfH0)) / upperBound
            testStatistic *= tj // Tj ← Tj-1 & tj
            if (showDetail) println("    $sampleNumber = $xj etaj = $etaj tj=$tj, Tj = $testStatistic")
            pvalues.add(min(1.0, 1.0 / tj))

            // – S ← S + Xj
            sampleSum += xj
        }

        if (sampleNumber == maxSample) status = TestH0Status.LimitReached
        val sampleMean = sampleSum / sampleNumber
        return TestH0Result(status, sampleNumber, sampleMean, pvalues)
    }

    // population mean under the null hypothesis that “the average of this list is not greater than 1/2”
    fun populationMeanIfH0(sampleNum: Int, sampleSumMinusOne: Double): Double {
        // LOOK detect if it goes negetive. sampleNum < N. Neg if (N * t < sampleSum)
        return if (withoutReplacement) (N * 0.5 - sampleSumMinusOne) / (N - sampleNum + 1) else 0.5
    }
}

class TruncShrinkage(
    val N: Int,
    val u: Double,
    val minsd: Double,
    val eta0: Double,
    val c: Double,
    val d: Int,
    val f: Double,
) : EstimFn {
    val welford = Welford()

    // estimate population mean from previous samples
    override fun eta(prevSamples: List<Double>): Double {
        val lastj = prevSamples.size
        val dj1 = (d + lastj).toDouble()

        val sampleSum = if (prevSamples.size == 0) 0.0 else {
            welford.update(prevSamples.last())
            prevSamples.subList(0, lastj).sum()
        }

        // note stdev not used if f = 0, except as capBelow
        val (_, variance, _) = welford.result()
        val stdev = Math.sqrt(variance) // stddev of sample
        val sdj3 = if (lastj < 2) 1.0 else max(stdev, minsd) // LOOK

        // (2.5.2, eq 14, "truncated shrinkage")
        // weighted = ((d * eta + S) / (d + j - 1) + u * f / sdj) / (1 + f / sdj)
        val est = ((d * eta0 + sampleSum) / dj1 + u * f / sdj3) / (1 + f / sdj3)
        println("est = $est sampleSum=$sampleSum d=$d eta0=$eta0 dj1=$dj1 lastj = $lastj")
        // Choosing epsi . To allow the estimated winner’s share ηi to approach √ µi as the sample grows
        // (if the sample mean approaches µi or less), we shall take epsi := c/ sqrt(d + i − 1) for a nonnegative constant c,
        val e_j = c / sqrt(dj1)

        // for instance c = (η0 − µ)/2.
        // The estimate ηi is thus the sample mean, shrunk towards η0 and truncated to the interval [µi + ǫi , 1), where ǫi → 0 as the sample size grows.

        //    return min(capAbove, max(est, capBelow)): capAbove > est > capAbove: u*(1-eps) > est > mu_j+e_j(c,j)
        val mean = meanUnderNull(N, 0.5, prevSamples)
        val capAbove = u * (1 - eps)
        val capBelow = mean + e_j
        val boundedEst =  min(capAbove, max(est, capBelow))


        //        return np.minimum(
        //            u * (1 - np.finfo(float).eps),
        //            np.maximum(weighted, m + c / np.sqrt(d + j - 1)),
        //        )
        val npmax = max(est, mean + c / sqrt((d + lastj - 1).toDouble()))  // 2.5.2 "choosing ǫi"
        val eta = min(u * (1 - eps), npmax)

        // println("   TruncShrinkage ${welford.count} sampleSum= $sampleSum eta=$eta")
        require(boundedEst == eta)
        return eta
    }

    fun meanUnderNull(N: Int, t: Double, x: List<Double>): Double {
        if (x.size == 0) return t
        val sum = x.subList(0, x.size - 1).sum()
        val m1 = (N * t - sum)
        val m2 = (N - x.size + 1)
        val m3 = m1 / m2
        return m3
    }
}

// ONEAUDIT
// def shrink_trunc(x: np.array, N: int, mu: float=1/2, nu: float=1-np.finfo(float).eps, u: float=1, c: float=1/2,
//                 d: float=100) -> np.array:
//     '''
//    apply the shrinkage and truncation estimator to an array
//
//    sample mean is shrunk towards nu, with relative weight d times the weight of a single observation.
//    estimate is truncated above at u-u*eps and below at mu_j+e_j(c,j)
//
//    S_1 = 0
//    S_j = \sum_{i=1}^{j-1} x_i, j > 1
//    m_j = (N*mu-S_j)/(N-j+1) if np.isfinite(N) else mu
//    e_j = c/sqrt(d+j-1)
//    eta_j =  ( (d*nu + S_j)/(d+j-1) \vee (m_j+e_j) ) \wedge u*(1-eps)
//
//    Parameters
//    ----------
//    x : input data
//    mu : float in (0, 1): hypothesized population mean under the null = 1/2
//    eta : float in (t, 1)
//        initial alternative hypothethesized value for the population mean
//    c : positive float
//        scale factor for allowing the estimated mean to approach t from above
//    d : positive float
//        relative weight of nu compared to an observation, in updating the alternative for each term
//    '''
//
//    S = np.insert(np.cumsum(x),0,0)[0:-1]  # 0, x_1, x_1+x_2, ...,
//    j = np.arange(1,len(x)+1)              # 1, 2, 3, ..., len(x)
//    m = (N*mu-S)/(N-j+1) if np.isfinite(N) else mu   # mean of population after (j-1)st draw, if null is true
//    eps = np.finfo(float).eps

//    est = (d*nu+S)/(d+j-1)

// estimate is truncated above at u-u*eps and below at mu_j+e_j(c,j)
//    e_j(c,j) = c/np.sqrt(d+j-1)
//    capBelow = m+c/np.sqrt(d+j-1)
//    capAbove = u*(1-np.finfo(float).eps)
//    return min(capAbove, max(est, capBelow)): capAbove > est > capAbove: u*(1-eps) > est > mu_j+e_j(c,j)

//    termMax = np.maximum(term2, term3)
//    termMin = np.minimum(term1, termMax)
//    return np.minimum(u*(1-np.finfo(float).eps), np.maximum((d*nu+S)/(d+j-1),m+c/np.sqrt(d+j-1)))

// SHANGRLA NonnegMean
//
// def shrink_trunc(self, x: np.array, **kwargs) -> np.array:
//        """
//        apply shrinkage/truncation estimator to an array to construct a sequence of "alternative" values
//
//        sample mean is shrunk towards eta, with relative weight d compared to a single observation,
//        then that combination is shrunk towards u, with relative weight f/(stdev(x)).
//
//        The result is truncated above at u*(1-eps) and below at m_j+e_j(c,j)
//
//        Shrinking towards eta stabilizes the sample mean as an estimate of the population mean.
//        Shrinking towards u takes advantage of low-variance samples to grow the test statistic more rapidly.
//
//        The running standard deviation is calculated using Welford's method.
//
//        S_1 := 0
//        S_j := \sum_{i=1}^{j-1} x_i, j >= 1
//        m_j := (N*t-S_j)/(N-j+1) if np.isfinite(N) else t
//        e_j := c/sqrt(d+j-1)
//        sd_1 := sd_2 = 1
//        sd_j := sqrt[(\sum_{i=1}^{j-1} (x_i-S_j/(j-1))^2)/(j-2)] \wedge minsd, j>2
//        eta_j :=  ( [(d*eta + S_j)/(d+j-1) + f*u/sd_j]/(1+f/sd_j) \vee (m_j+e_j) ) \wedge u*(1-eps)
//
//        Parameters
//        ----------
//        x: np.array
//            input data
//        attributes used:
//            eta: float in (t, u) (default u*(1-eps))
//                initial alternative hypothethesized value for the population mean
//            c: positive float
//                scale factor for allowing the estimated mean to approach t from above
//            d: positive float
//                relative weight of eta compared to an observation, in updating the alternative for each term
//            f: positive float
//                relative weight of the upper bound u (normalized by the sample standard deviation)
//            minsd: positive float
//                lower threshold for the standard deviation of the sample, to avoid divide-by-zero errors and
//                to limit the weight of u
//        """
//        # set the parameters
//        u = self.u
//        N = self.N
//        t = self.t
//        eta = getattr(self, "eta", u * (1 - np.finfo(float).eps))
//        c = getattr(self, "c", 1 / 2)
//        d = getattr(self, "d", 100)
//        f = getattr(self, "f", 0)
//        minsd = getattr(self, "minsd", 10**-6)
//        S, _, j, m = self.sjm(N, t, x)
//        _, v = welford_mean_var(x)
//        sdj = np.sqrt(v)
//        # threshold the sd, set first two sds to 1
//        sdj = np.insert(np.maximum(sdj, minsd), 0, 1)[0:-1]
//        sdj[1] = 1
//        weighted = ((d * eta + S) / (d + j - 1) + u * f / sdj) / (1 + f / sdj)
//        return np.minimum(
//            u * (1 - np.finfo(float).eps),
//            np.maximum(weighted, m + c / np.sqrt(d + j - 1)),
//        )
