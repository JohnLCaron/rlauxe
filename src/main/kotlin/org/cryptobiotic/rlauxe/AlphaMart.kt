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
    val riskLimit: Double = 0.05, // α ∈ (0, 1)
    val upperBound: Double = 1.0,  // aka u
) {
    init {
        require(riskLimit > 0.0 && riskLimit < 1.0 )
        require(upperBound > 0.0)
    }

    // run until sampleNumber == maxSample or terminateOnNullReject
    fun testH0(maxSample: Int, terminateOnNullReject: Boolean, drawSample : () -> Double) : TestH0Result {
        require(maxSample <= N)

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
            etajs.add(etaj)
            sampleAssortValues.add(xj)

            populationMeanIfH0 = this.populationMeanIfH0(sampleNumber, sampleSum)
            m.add(populationMeanIfH0)

            // This is eq 4 of ALPHA, p.5 :
            //      T_j = T_j-1 / u * ((X_j * eta_j / µ_j) + (u - X_j) * (u - eta_j) / ( u - µ_j))
            //      terms[np.isclose(0, m, atol=atol)] = 1  # ignore
            //      terms[np.isclose(u, m, atol=atol, rtol=rtol)] = 1  # ignore
            val tj = if (doubleIsClose(0.0, populationMeanIfH0) || doubleIsClose(upperBound, populationMeanIfH0)) 1.0 else {
                (xj * etaj / populationMeanIfH0 + (upperBound - xj) * (upperBound - etaj) / (upperBound - populationMeanIfH0)) / upperBound
            }
            testStatistic *= tj // Tj ← Tj-1 & tj
            testStatistics.add(testStatistic)

            // – S ← S + Xj
            sampleSum += xj
            sampleSums.add(sampleSum)
            if (showDetail) println("    $sampleNumber = $xj sum= $sampleSum, etaj = $etaj tj=$tj, Tj = $testStatistic")

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
            //        return np.minimum(1, 1 / terms) // WHY?
            val pvalue = 1.0 / testStatistic
            pvalues.add(pvalue)

            if (terminateOnNullReject && (pvalue < riskLimit || (sampleSum > N * 0.5))) {
                break
            }
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

// I think this agrees with SHANGRLA NonnegMean.shrink_trunc(), but not sure if thats really canonical
class TruncShrinkage(
    val N: Int,
    val withoutReplacement: Boolean = true,
    val upperBound: Double,
    val minsd: Double,
    val eta0: Double,
    val c: Double,
    val d: Int,
    val f: Double,
) : EstimFn {

    init {
        require(c > 0.0)
        require(d > 0)
        require(upperBound > 0.0)
    }

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
        val est = ((d * eta0 + sampleSum) / dj1 + upperBound * f / sdj3) / (1 + f / sdj3)
        // println("est = $est sampleSum=$sampleSum d=$d eta0=$eta0 dj1=$dj1 lastj = $lastj")

        // Choosing epsi . To allow the estimated winner’s share ηi to approach √ µi as the sample grows
        // (if the sample mean approaches µi or less), we shall take epsi := c/ sqrt(d + i − 1) for a nonnegative constant c,
        val e_j = c / sqrt(dj1)

        // for instance c = (η0 − µ)/2.
        // The estimate ηi is thus the sample mean, shrunk towards η0 and truncated to the interval [µi + ǫi , 1), where ǫi → 0 as the sample size grows.
        //    return min(capAbove, max(est, capBelow)): capAbove > est > capAbove: u*(1-eps) > est > mu_j+e_j(c,j)
        val mean = meanUnderNull(N, 0.5, prevSamples) // only used in capBelow
        val capAbove = upperBound * (1 - eps)
        val capBelow = mean + e_j
        val boundedEst =  min(capAbove, max(est, capBelow))
        return boundedEst
    }

    // TODO dont we need withReplacement ?
    fun meanUnderNull(N: Int, t: Double, x: List<Double>): Double {
        if (!withoutReplacement) return t  // with replacement
        if (x.isEmpty()) return t

        val sum = x.sum()
        val m1 = (N * t - sum)
        val m2 = (N - x.size)
        val m3 = m1 / m2
        return m3
    }
}

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

// shrink_trunc from ONEAUDIT
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
