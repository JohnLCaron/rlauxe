package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.lang.Math.pow
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max

// for the moment assume use_style = true, mvrs = null, so initial estimate only
class FindSampleSize(
    val alpha: Double,
    val p1: Double,
    val p2: Double,
    val p3: Double = 0.0,
    val p4: Double = 0.0,
    val ntrials: Int,
    val quantile: Double,
) {

    // given the contest.sampleSize, we can calculate the total number of ballots.
    // however, we get this from consistent sampling, which actually picks which ballots to sample.
    fun computeSampleSize(
        rcontests: List<ContestUnderAudit>,
        cvrs: List<CvrUnderAudit>,
    ): Int {
        // unless style information is being used, the sample size is the same for every contest.
        val old_sizes: MutableMap<Int, Int> =
            rcontests.associate { it.id to 0 }.toMutableMap()

        // setting p TODO whats this doing here? shouldnt it be in consistent sampling ?? MoreStyle section 3 ??
        for (cvr in cvrs) {
            if (cvr.sampled) {
                cvr.p = 1.0
            } else {
                cvr.p = 0.0
                for (con in rcontests) {
                    if (cvr.hasContest(con.id) && !cvr.sampled) {
                        val p1 = con.sampleSize.toDouble() / (con.upperBound!! - old_sizes[con.id]!!)
                        cvr.p = max(p1, cvr.p) // TODO nullability
                    }
                }
            }
        }

        // when old_sizes == 0, total_size should be con.sample_size (61); python has roundoff to get 62
        // total_size = ceil(np.sum([x.p for x in cvrs if !x.phantom))
        // TODO total size is the sum of the p's over the cvrs (!wtf)
        val summ: Double = cvrs.filter { !it.phantom }.map { it.p }.sum()
        val total_size = ceil(summ).toInt()
        return total_size // TODO what is this? doesnt consistent sampling decide this ??
    }

    fun simulateSampleSize(
        contest: ContestUnderAudit,
        assorter: ComparisonAssorter,
        cvrs: List<CvrUnderAudit>,
    ): Int {
        val sampler: GenSampleFn = if (contest.contest.choiceFunction == SocialChoiceFunction.IRV)
            ComparisonSamplerForRaire(cvrs, contest, assorter, p1 = p1, p2 = p2, p3 = p3, p4 = p4)
        else
            ComparisonSamplerForEstimation(cvrs, contest, assorter, p1 = p1, p2 = p2, p3 = p3, p4 = p4)

        val N = cvrs.size
        val optimal = AdaptiveComparison(
            N = contest.ncvrs,
            withoutReplacement = true,
            a = assorter.noerror,
            d1 = 100,
            d2 = 100,
            p1 = p1,
            p2 = p2,
            p3 = p3,
            p4 = p4,
        )
        val betta = BettingMart(bettingFn = optimal, N = N, noerror = assorter.noerror, upperBound = assorter.upperBound, withoutReplacement = false)

        // TODO use coroutines
        val result: RunTestRepeatedResult = runTestRepeated(
            drawSample = sampler,
            maxSamples = contest.ncvrs,
            ntrials = ntrials,
            testFn = betta,
            testParameters = mapOf("p1" to optimal.p1, "p2" to optimal.p2, "p3" to optimal.p3, "p4" to optimal.p4, "margin" to assorter.margin),
            showDetails = false,
        )

        return result.findQuantile(quantile)
    }
}

// this is optimal_comparison_noP1, a bet, not a sample estimate.
// see cobra p 5
fun optimal_comparison(alpha: Double, u: Double, rate_error_2: Double = 1e-4): Double {
    /*
    The value of eta corresponding to the "bet" that is optimal for ballot-level comparison audits,
    for which overstatement assorters take a small number of possible values and are concentrated
    on a single value when the CVRs have no errors.

    Let p0 be the rate of error-free CVRs, p1=0 the rate of 1-vote overstatements,
    and p2= 1-p0-p1 = 1-p0 the rate of 2-vote overstatements. Then

    eta = (1-u*p0)/(2-2*u) + u*p0 - 1/2, where p0 is the rate of error-free CVRs.

    Translating to p2=1-p0 gives:

    eta = (1-u*(1-p2))/(2-2*u) + u*(1-p2) - 1/2.

    Parameters
    ----------
    x: input data
    rate_error_2: hypothesized rate of two-vote overstatements

    Returns
    -------
    eta: estimated alternative mean to use in alpha
    */

    // TODO python doesnt check (2 - 2 * self.u) != 0; self.u = 1
    if (u == 1.0)
        throw RuntimeException("optimal_comparison: u ${u} must != 1")

    val p2 = rate_error_2 // getattr(self, "rate_error_2", 1e-4)  // rate of 2-vote overstatement errors
    val bet = (1 - u * (1 - p2)) / (2 - 2 * u) + u * (1 - p2) - .5
    // 1 / alpha = bet ^ size
    val term1 = -ln(alpha)
    val term2 = ln(bet)
    val size = -ln(alpha) / ln(bet)
    return size
}

// MoreStyle footnote 5
// The number of draws S4 needs to confirm results depends on the diluted margin and
// the number and nature of discrepancies the sample uncovers. The initial sample size can be
// written as a constant (denoted ρ) divided by the “diluted margin.”
// In general, ρ = − log(α)/[ 2γ + λ log(1 − 2γ)], where γ is an error inflation factor and λ is the anticipated rate of
// one-vote overstatements in the initial sample as a percentage of the diluted margin [17]. We define γ and λ as in
// https://www.stat.berkeley.edu/~stark/Vote/auditTools.htm.

/**
 * From colorado-rla Audit.optimistic().
 * Based on SuperSimple paper, generalization of equations in section 2.
 *
 * @param gamma the "error inflator" parameter. error inflation factor γ ≥ 100%.
 *   γ controls a tradeoff between initial sample size and the amount of additional counting required when the
 *   sample finds too many overstatements, especially two-vote overstatements.
 *   The larger γ is, the larger the initial sample needs to be, but the less additional counting will be required
 *   if the sample finds a two-vote overstatement or a large number of one-vote maximum overstatements. (paper has 1.1)
 * @param twoUnder the number of two-vote understatements
 * @param oneUnder the number of one-vote understatements
 * @param oneOver the number of one-vote overstatements
 * @param twoOver the number of two-vote overstatements
 */
fun estimateSampleSizeSimple(
    riskLimit: Double,
    dilutedMargin: Double,
    gamma: Double = 1.03,
    oneOver: Int = 0,   // p1
    twoOver: Int = 0,   // p2
    oneUnder: Int = 0,  // p3
    twoUnder: Int = 0,  // p4
): Int {
    val two_under_term = twoUnder * ln( 1 + 1 / gamma) // log or ln ?
    val one_under_term = oneUnder * ln( 1 + 1 / (2 * gamma)) // log or ln ?
    val one_over_term = oneOver * ln( 1 - 1 / (2 * gamma)) // log or ln ?
    val two_over_term = twoOver * ln( 1 - 1 / gamma) // log or ln ?

    // "sample-size multiplier" rho is independent of margin
    val rho: Double = -(2.0 * gamma) * (ln(riskLimit) + two_under_term + one_under_term + one_over_term + two_over_term)
    val r = ceil(rho / dilutedMargin)  // round up
    val over_under_sum = (twoUnder + oneUnder + oneOver + twoOver).toDouble()
    // println("   rho=$rho r=$r")
    return max(r, over_under_sum).toInt()
}


// COBRA equation 1 is a deterministic lower bound on sample size, dependent on margin and risk limit.
// COBRA equation 2 has the maximum expected value for given over/understatement rates. See OptimalLambda class for implementation.

fun estimateSampleSizeOptimalLambda(
    alpha: Double, // risk
    dilutedMargin: Double, // the difference in votes for the reported winner and reported loser, divided by the total number of ballots cast.
    upperBound: Double, // assort upper value, = 1 for plurality, 1/(2*minFraction) for supermajority
    p1: Double, p2: Double, p3: Double = 0.0, p4: Double = 0.0
): Int {

    //  a := 1 / (2 − v/au)
    //  v := 2Āc − 1 is the diluted margin
    //  au := assort upper value, = 1 for plurality, 1/(2*minFraction) for supermajority

    val a = 1 / (2 - dilutedMargin / upperBound)
    val kelly = OptimalLambda(a, p1=p1, p2=p2, p3=p3, p4=p4)
    val lam = kelly.solve()

    // 1 / alpha = bet ^ size
    val term1 = -ln(alpha)
    val term2 = ln(lam)
    val r = term1 / term2 // round up

    val T = pow(lam, r)
    val size = ceil(r)
    // println("   lam=$lam r=$r T=$T size=$size")

    return size.toInt()
}