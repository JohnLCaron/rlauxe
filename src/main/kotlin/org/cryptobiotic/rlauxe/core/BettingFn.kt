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

// SmithRamdas 2022, section B.3
// Rather than solve (42), we take the Taylor approximation of (1 + y)−1 by (1 − y) for y ≈ 0 to obtain
// λaGRAPA (m) := −c/(1-m) ∨ (µ_t-1 - m) / (variance_t-1 + (µ_t-1 - m)**2) ∧ c/m
// for some truncation level c ≤ 1
//
// SHANGRLA Nonneg_mean.agrapa()
//  This implementation alters the method from support \mu \in [0, 1] to \mu \in [0, u], and to constrain
//  the bets to be positive (for one-sided tests against the alternative that the true mean is larger than hypothesized)
/**
 * Approximate Growth rate adaptive to the particular alternative (AGRAPA)
 * @parameter t hypothesized population mean
 * @parameter lam0 initial bet
 * @parameter c_grapa_0: float in (0, 1) initial scale factor c_j in WSR's agrapa bet
 */
class AgrapaBet(
    val N: Int,
    val withoutReplacement: Boolean = true,
    val upperBound: Double, // TODO why not used ??
    val lam0: Double, // initial guess TODO how to pick this?
    val c_grapa_0: Double,
    val c_grapa_max: Double,
    val c_grapa_grow: Double
): BettingFn {

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
        val result =  max(0.0, min(lamj, c / t_adj))
        return result
    }

}

//     def optimal_comparison(self, x: np.array, **kwargs) -> np.array:
//        """
//        The value of eta corresponding to the "bet" that is optimal for ballot-level comparison audits,
//        for which overstatement assorters take a small number of possible values and are concentrated
//        on a single value when the CVRs have no errors.
//
//        Let p0 be the rate of error-free CVRs, p1=0 the rate of 1-vote overstatements,
//        and p2= 1-p0-p1 = 1-p0 the rate of 2-vote overstatements. Then
//
//        eta = (1-u*p0)/(2-2*u) + u*p0 - 1/2, where p0 is the rate of error-free CVRs.
//
//        Translating to p2=1-p0 gives:
//
//        eta = (1-u*(1-p2))/(2-2*u) + u*(1-p2) - 1/2.
//
//        Parameters
//        ----------
//        x: np.array
//            input data
//        rate_error_2: float
//            hypothesized rate of two-vote overstatements
//
//        Returns
//        -------
//        eta: float
//            estimated alternative mean to use in alpha
//        """
//        # set the parameters
//        # TO DO: double check where rate_error_2 is set
//        p2 = getattr(self, "rate_error_2", 1e-4)  # rate of 2-vote overstatement errors
//        return (1 - self.u * (1 - p2)) / (2 - 2 * self.u) + self.u * (1 - p2) - 1 / 2

/**
 * The value of eta corresponding to the "bet" that is optimal for ballot-level comparison audits,
 * for which overstatement assorters take a small number of possible values and are concentrated
 * on a single value when the CVRs have no errors.
 * Let p0 be the rate of error-free CVRs,
 *     p1 the rate of 1-vote overstatements,
 *     p2 the rate of 2-vote overstatements = 1-p0-p1
 * Ignore the understatements.
 * from SHANGRLA Nonneg_mean.optimal_comparison()
 */
class OptimalComparison(
    val N: Int,
    val withoutReplacement: Boolean = true,
    val upperBound: Double,
    // val p1: Double = 0.0, // the rate of 1-vote overstatements // assume 0
    val p2: Double = 1.0e-4, // the rate of 2-vote overstatements
): BettingFn {
    init {
        require(upperBound > 1.0)
    }

    override fun bet(prevSamples: Samples): Double {
        val mu = populationMeanIfH0(N, withoutReplacement, prevSamples)

        // note eta is a constant
        //        return (1 - self.u * (1 - p2)) / (2 - 2 * self.u) + self.u * (1 - p2) - 1 / 2
        val eta =  (1.0 - upperBound * (1.0 - p2)) / (2.0 - 2.0 * upperBound) + upperBound * (1.0 - p2) - 0.5
        return eta_to_lam(eta, mu, upperBound)
    }

}

class OptimalComparisonFull(
    val N: Int,
    val withoutReplacement: Boolean = true,
    val upperBound: Double,
    val p1: Double = 0.0, // the rate of 1-vote overstatements
    val p2: Double = 1.0e-4, // the rate of 2-vote overstatements
): BettingFn {

    override fun bet(prevSamples: Samples): Double {
        val mu = populationMeanIfH0(N, withoutReplacement, prevSamples)

        //        return (1 - self.u * (1 - p2)) / (2 - 2 * self.u) + self.u * (1 - p2) - 1 / 2
        val eta =  (1.0 - upperBound * (1.0 - p2)) / (2.0 - 2.0 * upperBound) + upperBound * (1.0 - p2) - 0.5
        return eta_to_lam(eta, mu, upperBound)
    }

}