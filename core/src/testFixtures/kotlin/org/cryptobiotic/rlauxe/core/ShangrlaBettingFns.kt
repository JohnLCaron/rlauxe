package org.cryptobiotic.rlauxe.core

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// betting functions from SHANGRLA code

class FixedBet(val lam: Double): BettingFn {
    override fun bet(prevSamples: SampleTracker) = lam
}

// SmithRamdas 2022, section B.3
// Rather than solve (42), we take the Taylor approximation of (1 + y)−1 by (1 − y) for y ≈ 0 to obtain
//     λ_t(m) := −c/(1-m) ∨ (µ_t-1 - m) / (variance_t-1 + (µ_t-1 - m)**2) ∧ c/m
// for some truncation level c ≤ 1.
//
// In our case:
//   lower bound is zero.
//   m here is populationMeanIfH0() = mu_WOR in section 5 = t_adj in SHANGRLA Nonneg_mean.agrapa()
//   µ_t-1 here is the sample Mean at t-1 = mj in SHANGRLA Nonneg_mean.agrapa()
//   variance_t-1 here is the sample variance at t-1 = sdj2 in SHANGRLA Nonneg_mean.agrapa()
//
// SHANGRLA Nonneg_mean.agrapa()
//  This implementation alters the method from support \mu \in [0, 1] to \mu \in [0, u], and to constrain
//  the bets to be positive (for one-sided tests against the alternative that the true mean is larger than hypothesized)
//
// Note that the initial guess is only used for the first lam, rather than a weighted average as in shrink_trunc.
// Note that upperBound is not used, which seems suspicious, especially because SHANGRLA says \mu \in [0, u].
// Currently not used in production
/**
 * Approximate Growth rate adaptive to the particular alternative (AGRAPA)
 * @parameter t hypothesized population mean
 * @parameter lam0 initial bet
 * @parameter c_grapa_0: float in (0, 1) initial scale factor c_j in WSR's agrapa bet
 */
class AgrapaBet(
    val N: Int,
    val withoutReplacement: Boolean = true,
    val upperBound: Double, // why not used ??
    val lam0: Double, // initial guess; how to pick this? should be < 2 * c_grapa_0?
    val c_grapa_0: Double,
    val c_grapa_max: Double,
    val c_grapa_grow: Double
): BettingFn {

    override fun bet(prevSamples: SampleTracker): Double {
        val lastSampleNumber = prevSamples.numberOfSamples()
        if (lastSampleNumber == 0) return lam0 // initial guess

//        lamj = (mj - t_adj) / (sdj2 + (t_adj - mj) ** 2)  # agrapa bet
//        #  shift and set first bet to self.lam
//        lamj = np.insert(lamj, 0, lam)[0:-1]
        val t_adj = populationMeanIfH0(N, withoutReplacement, prevSamples) // aka mu_WOR
        val meanDiff = (prevSamples.mean() - t_adj)
        val lamj = meanDiff / (prevSamples.variance() + meanDiff * meanDiff)

//        c = c_g_0 + (c_g_m - c_g_0) * (1 - 1 / (1 + c_g_g * np.sqrt(np.arange(len(x)))))
        val c = c_grapa_0 + (c_grapa_max - c_grapa_0) * (1.0 - 1.0 / (1 + c_grapa_grow * sqrt(lastSampleNumber.toDouble())))

//        return np.maximum(0, np.minimum(lamj, c / t_adj))
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
 * From SHANGRLA Nonneg_mean.optimal_comparison(). line 390
 * When p1=0, can use closed form to solve for lambda.
 *
 * The value of eta corresponding to the "bet" that is optimal for ballot-level comparison audits,
 * for which overstatement assorters take a small number of possible values and are concentrated
 * on a single value when the CVRs have no errors.
 *
 * Let p0 be the rate of error-free CVRs, p1=0 the rate of 1-vote overstatements,
 * and p2= 1-p0-p1 = 1-p0 the rate of 2-vote overstatements. Then
 *
 *   eta = (1-u*p0)/(2-2*u) + u*p0 - 1/2, where p0 is the rate of error-free CVRs.
 *
 * Translating to p2=1-p0 gives:
 *
 *   eta = (1-u*(1-p2))/(2-2*u) + u*(1-p2) - 1/2.
 */
// A bug in BettingMart that removed stalls probably made this look better than it is
// CANDIDATE for removal
// TODO just stop using it....
// TODO check to see if this is correct, esp with assorter upper bound
// CORBA has u := 2/(2 − v) = 2a, should be  clcaUpper := 2/(2 − v/assortUpper) = 2 * noerror
// ALPHA has lam = (eta/mui - 1)/(u-mui) = etaToLam
class OptimalComparisonNoP1(
    val N: Int,
    val withoutReplacement: Boolean = true,
    val upperBound: Double,   // bassort u = 2 * noerror, not assorter upper = 1.0
    p2: Double = 1.0e-4, // the rate of 2-vote overstatements
): BettingFn {
    // p2 = getattr(self, "error_rate_2", 1e-5)  # rate of 2-vote overstatement errors
    val p2use = max(p2, 1.0e-5)
    val eta: Double

    init {
        require(upperBound > 1.0)
        if (upperBound * (1.0 - p2use) <= 1.0)
            logger.warn{ "hmmmm ${upperBound * (1.0 - p2use)} should be > 1.0" }

        // note eta is a constant
        //        return (1 - self.u * (1 - p2)) / (2 - 2 * self.u) + self.u * (1 - p2) - 1 / 2
        //         eta = (1-u*(1-p2))/(2-2*u) + u*(1-p2) - 1/2.
        val numer =  (1.0 - upperBound * (1.0 - p2use)) // (1-u*(1-p2))
        val denom =  (2.0 - 2.0 * upperBound) // (2-2*u)
        val eta3 =  upperBound * (1.0 - p2use) - 0.5 // u*(1-p2) - 1/2
        eta =  numer/denom + eta3
    }

    override fun bet(prevSamples: SampleTracker): Double {
        // (N * 0.5 - sampleTracker.sum()) / (N - sampleNum) =~ 0.5
        val mu = populationMeanIfH0(N, withoutReplacement, prevSamples)
        // return (eta / mu - 1) / (upper - mu)
        val lam =  etaToLam(eta, mu, upperBound)

        // however, this is out of bounds in testing for plurality, assortUpper = 1
        if (lam <= 0.0 || lam >= 2.0) {
            val t1 = eta/mu
            val t2 = eta/mu - 1
            val t3 = upperBound - mu
            val t4 = t2 / t3
            logger.warn { "lam=$lam should be in (0..2); eta=$eta" }
        }

        return lam
    }

    companion object {
        private val logger = KotlinLogging.logger("OptimalComparisonNoP1")
    }
}
