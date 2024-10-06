package org.cryptobiotic.rlauxe.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Interface for defining a betting function.
 *
 * Choose the amount to bet (aka lam)
 * for a given sample number and associated sample value.
 */
interface BettingFn {
    fun bet(prevSamples: Samples): Double
}

class FixedBet(val lam: Double): BettingFn {
    override fun bet(prevSamples: Samples) = lam
}

/*
Alpha eq 12. Choosing λi is equivalent to choosing ηi :
       λi = (ηi /µi − 1) / (u − µi )
    ⇐⇒ ηi = µi (1 + λi (u − µi ))
As ηi ranges from µi to u, λi ranges continuously from 0 to 1/µi , the same range of values of λi permitted in
Waudby-Smith and Ramdas (2021): selecting λi is equivalent to selecting a method for estimating θi.
The difference is only in how λi is chosen. However, see section 4 for a generalization to allow sampling weights
and to allow u to vary by draw.
 */

/* original python
    def lam_to_eta(self, lam: np.array, mu: np.array) -> np.array:
        """
        Convert bets (lam) for betting martingale to their implied estimates of the mean, eta, for ALPHA

        Parameters
        ----------
        lam: float or numpy array
            the value(s) of lam (the fraction of the current fortune to bet on the next draw)
        mu: float or numpy array
            sequence of population mean(s) if the null is true, adjusted for values already seen

        Returns
        -------
        eta: float or numpy array
            the corresponding value(s) of the mean
        """
        return mu * (1 + lam * (self.u - mu))
 */

fun lam_to_eta(lam: Double, mu: Double, upper: Double): Double {
    return mu * (1 + lam * (upper - mu))
}

/* original python
    def eta_to_lam(self, eta: np.array, mu: np.array) -> np.array:
        """
        Convert eta for ALPHA to corresponding bet lam for the betting martingale parametrization

        Parameters
        ----------
        eta: float or numpy array
            the value(s) of lam (the fraction of the current fortune to bet on the next draw)
        mu: float or numpy array
            sequence of population mean(s) if the null is true, adjusted for values already seen

        Returns
        -------
        lam: float or numpy array
            the corresponding betting fractions
        """
        return (eta / mu - 1) / (self.u - mu)
 */
fun eta_to_lam(eta: Double, mu: Double, upper: Double): Double {
    return (eta / mu - 1) / (upper - mu)
}

// // SHANGRLA Nonneg_mean.agrapa()
//  lamj = (mj - t_adj) / (sdj2 + (t_adj - mj) ** 2)  # agrapa bet



/**
 * Approximate Growth rate adaptive to the particular alternative (AGRAPA)
 * @parameter t hypothesized population mean
 * @parameter lam0 initial bet
 * @parameter c_grapa_0: float in (0, 1) initial scale factor c_j in WSR's agrapa bet
 */
class AgrapaBet(
    val N: Int,
    val withoutReplacement: Boolean = true,
    val upperBound: Double,
    val t: Double,
    val lam0: Double, // initaal guess
    val c_grapa_0: Double,
    val c_grapa_max: Double,
    val c_grapa_grow: Double
): BettingFn {
    //val welford = Welford()
    //var sampleSum = 0.0

/*
def agrapa(self, x: np.array, **kwargs) -> np.array:
        """
        maximize approximate growth rate adapted to the particular alternative (aGRAPA) bet of Waudby-Smith & Ramdas (WSR)

        This implementation alters the method from support \mu \in [0, 1] to \mu \in [0, u], and to constrain
        the bets to be positive (for one-sided tests against the alternative that the true mean is larger than
        hypothesized)

        lam_j := 0 \vee (\hat{\mu}_{j-1}-t)/(\hat{\sigma}_{j-1}^2 + (t-\hat{\mu})^2) \wedge c_grapa/t

        \hat{\sigma} is the standard deviation of the sample.

        \hat{\mu} is the mean of the sample

        The value of c_grapa \in (0, 1) is passed as an instance variable of Class NonnegMean

        The running standard deviation is calculated using Welford's method.

        S_0 := 0
        S_j := \sum_{i=0}^{j-1} x_i, j >= 1
        t_adj := (N*t-S_j)/(N-j+1) if np.isfinite(N) else t
        sd_0 := 0
        sd_j := sqrt[(\sum_{i=1}^{j-1} (x_i-S_j/(j-1))^2)/(j-2)] \wedge minsd, j>2
        lam_1 := self.lam
        lam_j :=  0 \vee (\hat{m_{j-1}-t)/(sd_{j-1}^2 + (t-m_{j-1})^2) \wedge c_grapa/t

        Parameters
        ----------
        x: np.array
            input data
        attributes used:
            c_grapa_0: float in (0, 1)
                initial scale factor c_j in WSR's agrapa bet
            c_grapa_max: float in (1, 1-np.finfo(float).eps]
                asymptotic limit of the value of c_j
            c_grapa_grow: float in [0, np.infty)
                rate at which to allow c to grow towards c_grapa_max.
                c_j := c_grapa_0 + (c_grapa_max-c_grapa_0)*(1-1/(1+c_grapa_grow*np.sqrt(j)))
                A value of 0 keeps c_j equal to c_grapa for all j.

        """
        # set the parameters
        u = self.u  # population upper bound
        N = self.N  # population size
        t = self.t  # hypothesized population mean
        lam = getattr(self, "lam", 0.5)  # initial bet
        c_g_0 = getattr(
            self, "c_grapa_0", (1 - np.finfo(float).eps)
        )  # initial truncation value c for agrapa
        c_g_m = getattr(
            self, "c_grapa_max", (1 - np.finfo(float).eps)
        )  # asymptotic limit of c
        c_g_g = getattr(self, "c_grapa_grow", 0)  # rate to let c grow towards c_g_m

        mj, sdj2 = welford_mean_var(x)
        t_adj = (
            (N * t - np.insert(np.cumsum(x), 0, 0)[0:-1]) / (N - np.arange(len(x)))
            if np.isfinite(N)
            else t * np.ones(len(x))
        )
        lamj = (mj - t_adj) / (sdj2 + (t_adj - mj) ** 2)  # agrapa bet
        #  shift and set first bet to self.lam
        lamj = np.insert(lamj, 0, lam)[0:-1]
        c = c_g_0 + (c_g_m - c_g_0) * (1 - 1 / (1 + c_g_g * np.sqrt(np.arange(len(x)))))
        lamj = np.maximum(0, np.minimum(c / t_adj, lamj))
        return lamj


fun agrapa(
    x: List<Double>, c_grapa_0: Double = (1 - Double.MIN_VALUE),
    c_grapa_max: Double = (1 - Double.MIN_VALUE),
    c_grapa_grow: Double = 0.0,
    u: Double?,
    N: Double?,
    t: Double?,
    lam: Double = 0.5,
): List<Double> {

    val mj: List<Double>
    val sdj2: List<Double>
    val mjAcc = 0.0
    val sdj2Acc = 0.0
    val t_adj: List<Double> = mk.emptyArray(x.size)

    val lamj = mk.zeros(x.size + 1)

    lamj[0] = lam

    mjAcc += x[0]
    mj = mk.array(doubleArrayOf(mjAcc))
    sdj2 = when (mj.size) {
        1 -> mk.zeros(1)
        else -> mk.array(doubleArrayOf(sdj2Acc / (mj.size - 1))) // Finishing Welford's method.
    }

    val temp = (N?.let { -mk.cumsum(x).insert(0, 0.0) + it * t!! / (it - (0 until x.size).toDoubleArray()) }
        ?: t?.toDouble()!!)
    t_adj = when {
        t_adj.size > 0 && N != Double.POSITIVE_INFINITY ->
            temp

        else ->
            mk.expandDims(t?.toDouble() ?: 0.0)
    }

    lamj = ((mj - t_adj) / (sdj2 + (t_adj - mj).pow(2.0)))
        .insert(0, lam)
        .sliceArray(0 until lamj.size - 1) // Insert and slide.

    val c =
        c_grapa_0 + (c_grapa_max - c_grapa_0) * (1 - 1 / (1 + c_grapa_grow * kotlin.math.sqrt((0 until x.size).toDoubleArray().indices.toDouble())))

    lamj = kotlin.math.max(0.toDouble(), kotlin.math.min(c / t_adj, lamj))

    return lamj
}
 */

    // "λi can be a predictable function of the data X1 , . . . , Xi−1" COBRA section 4.2
    // The bet must only use the previous samples
    override fun bet(prevSamples: Samples): Double {
        val lastSampleNumber = prevSamples.numberOfSamples()

        val t_adj = populationMeanIfH0(N, withoutReplacement, prevSamples)
        //        lamj = (mj - t_adj) / (sdj2 + (t_adj - mj) ** 2)  # agrapa bet
        //        #  shift and set first bet to self.lam
        //        lamj = np.insert(lamj, 0, lam)[0:-1]
        val lamj = if (lastSampleNumber == 0) lam0 else {
            val mj = prevSamples.mean()
            val variance = prevSamples.variance()
            val tmm = (mj - t_adj)
            tmm / (variance + tmm * tmm)
        }

//      c = c_g_0 + (c_g_m - c_g_0) * (1 - 1 / (1 + c_g_g * np.sqrt(np.arange(len(x)))))
        val c = c_grapa_0 + (c_grapa_max - c_grapa_0) * (1.0 - 1.0 / (1 + c_grapa_grow * sqrt(lastSampleNumber.toDouble())))

//        lamj = np.maximum(0, np.minimum(c / t_adj, lamj))
//        return lamj
        val result =  max(0.0, min(c / t_adj, lamj))
        return result
    }

    /* estimate population mean from previous samples
    fun eta(prevSamples: Samples): Double {
        val lastj = prevSamples.size()
        val dj1 = (d + lastj).toDouble()

        val sampleSum = if (lastj == 0) 0.0 else {
            welford.update(prevSamples.last())
            prevSamples.sum()
        }

        // (2.5.2, eq 14, "truncated shrinkage")
        // weighted = ((d * eta + S) / (d + j - 1) + u * f / sdj) / (1 + f / sdj)
        // val est = ((d * eta0 + sampleSum) / dj1 + upperBound * f / sdj3) / (1 + f / sdj3)
        val est = if (f == 0.0) (d * eta0 + sampleSum) / dj1 else {
            // note stdev not used if f = 0
            val (_, variance, _) = welford.result()
            val stdev = sqrt(variance) // stddev of sample
            val sdj3 = if (lastj < 2) 1.0 else max(stdev, minsd) // LOOK
            ((d * eta0 + sampleSum) / dj1 + upperBound * f / sdj3) / (1 + f / sdj3)
        }

        // Choosing epsi . To allow the estimated winner’s share ηi to approach √ µi as the sample grows
        // (if the sample mean approaches µi or less), we shall take epsi := c/ sqrt(d + i − 1) for a nonnegative constant c,
        // for instance c = (η0 − µ)/2.
        val mean = populationMeanIfH0(N, withoutReplacement, prevSamples)
        val e_j = c / sqrt(dj1)
        val capBelow = mean + e_j

        // println("est = $est sampleSum=$sampleSum d=$d eta0=$eta0 dj1=$dj1 lastj = $lastj, capBelow=${capBelow}(${est < capBelow})")
        // println("  meanOld=$meanUnderNull mean = $mean e_j=$e_j capBelow=${capBelow}(${est < capBelow})")

        // The estimate ηi is thus the sample mean, shrunk towards η0 and truncated to the interval [µi + ǫi , upper),
        //    where ǫi → 0 as the sample size grows.
        //    return min(capAbove, max(est, capBelow)): capAbove > est > capAbove: u*(1-eps) > est > mu_j+e_j(c,j)
        val boundedEst = min(max(capBelow, est), capAbove)
        return boundedEst
    }

     */

}

