package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.raire.makeRaireComparisonAudit
import org.cryptobiotic.rlauxe.util.*
import kotlin.math.ceil
import kotlin.math.max

class AssertionUnderAudit(val assertion: org.cryptobiotic.rlauxe.core.ComparisonAssertion, var ncards: Int? = null) {
    var sample_size = 0
    var margin = 0.0

    fun make_overstatement(overs: Double): Double {
        // TODO reference in paper
        val upper = assertion.assorter.upperBound()
        val result = (1 - overs / upper) / (2 - this.margin / upper)
        return result
    }
}

// for the moment assume use_style = true, mvrs = null, so initial estimate only
class FindSampleSize(
    val N: Int,
    val alpha: Double,
    val error_rate_1: Double,
    val error_rate_2: Double,
    val reps: Int,
    val quantile: Double,
) {

    // SHANGRLA Audit.find_sample_size
    // StartingSampleSize.Audit.find_sample_size

    // expect these parameters
    // SHANGRLA Nonneg_mean.sample_size
    //                    'test':             NonnegMean.alpha_mart,
    //                   'estim':            NonnegMean.optimal_comparison
    //          'quantile':       0.8,
    //         'error_rate_1':   0.001,
    //         'error_rate_2':   0.0,
    //         'reps':           100,

    fun find_sample_size(
        audit: AuditComparison,
        rcontests: List<RaireContestUnderAudit>,
        cvrs: List<CvrUnderAudit>,
    ): Int {
        // unless style information is being used, the sample size is the same for every contest.
        val old_sizes: MutableMap<Int, Int> =
            rcontests.associate { it.id to 0 }.toMutableMap()

        for (contest in rcontests) {
            val contestId = contest.id
            old_sizes[contestId] = cvrs.filter { it.hasContest(contestId) }.map { if (it.sampled) 1 else 0 }.sum()

            // max sample size over all assertions in this contest
            var max_size = 0
            val assertions = audit.assertions[contestId]!!
            assertions.forEach { asn ->
                // if (!asn.proved) {
                max_size = max(
                    max_size,
                    estimateSampleSize(
                        rcontests,
                        cvrs.map { it.cvr },
                        reps = this.reps,
                        quantile = this.quantile,
                    )
                )
                // }
            }
            contest.sampleSize = max_size
        }

        // setting p TODO whats this doing here? shouldnt it be in consistent sampling ??
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
        return total_size
    }

    fun estimateSampleSize(
        contests: List<RaireContestUnderAudit>,
        cvrs: List<Cvr>,
        reps: Int,
        quantile: Double
    ): Int {

        // expect these parameters
        // SHANGRLA Nonneg_mean.sample_size
        //                    'test':             NonnegMean.alpha_mart,
        //                   'estim':            NonnegMean.optimal_comparison
        //          'quantile':       0.8,
        //         'error_rate_1':   0.001,
        //         'error_rate_2':   0.0,
        //         'reps':           100,

        val auditComparison = makeRaireComparisonAudit(contests, cvrs)
        val comparisonAssertions = auditComparison.assertions.values.first()
        val minAssorter = comparisonAssertions[1].assorter // the one with the smallest margin

        val sampler: GenSampleFn = ComparisonNoErrors(cvrs, minAssorter) // assume no errors

        val optimal = OptimalComparisonNoP1(
            N = N,
            withoutReplacement = true,
            upperBound = minAssorter.upperBound,
            p2 = error_rate_2, // 0.000! really ??
        )

        val betting = BettingMart(bettingFn = optimal, N = N, noerror = 0.0, withoutReplacement = false)
        val result = betting.testH0(N, true, showDetails = false) { sampler.sample() }
        println(result)
        println("pvalues = ${result.pvalues}")
        return result.sampleCount
    }

}
////////////////////////////////////////////////////////////////////////////////////////
// from python

/* fun estimateSampleSizePolling(
    asn: Assertion,
    N: Int,
    prefix: Boolean = false,
    reps: Int,
    quantile: Double = 0.5, // quantile of the distribution of sample sizes to return
): Int {

    val big = this.assorter.upperBound

    val n_0 = this.contest.tally[this.loser]!! // LOOK nullability
    val n_big = this.contest.tally[this.winner]!! // LOOK nullability
    val n_half = N - n_0 - n_big
    //         fun interleave_values(n_small: Int, n_med: Int, n_big: Int, small: Double, med: Double, big: Double): DoubleArray {
    val x = interleave_values(n_0, n_half, n_big, big = big)

    // this is the simulation
    val sample_size = this.test.estimateSampleSize(
        x = x,
        alpha = this.contest.risk_limit,
        reps = reps,
        prefix = prefix,
        quantile = quantile, // seed = seed
    )
    this.sample_size = sample_size
    return sample_size
}

 */

// SHANGRLA Assertion.find_sample_size
// StartingSampleSize.Assertion.estimateSampleSizeComparision

/*
    fun estimateSampleSizeComparision(
        asn: AssertionUnderAudit,
        prefix: Boolean = false,
        rate1: Double? = null,
        rate2: Double? = null,
        reps: Int,
        quantile: Double = 0.5, // quantile of the distribution of sample sizes to return
    ): Int {
        val big = asn.make_overstatement(overs = 0.0)
        val small = asn.make_overstatement(overs = 0.5)
        println("  Assertion ${asn} big = ${big} small = ${small}")

        val rate_1 = rate1 ?: ((1.0 - asn.margin) / 2.0)   // rate of small values
        val x = DoubleArray(this.N) { big } // array N floats, all equal to big

        val rate_1_i = numpy_arange(0, this.N, step = (1.0 / rate_1).toInt())
        val rate_2_i =
            if (rate2 != null && rate2 != 0.0) numpy_arange(0, this.N, step = (1.0 / rate2).toInt()) else IntArray(0)
        rate_1_i.forEach { x[it] = small }
        rate_2_i.forEach { x[it] = 0.0 }

        // this is the simulation
        val sample_size = estimateSampleSize(
            x = x,
            alpha = this.alpha,
            reps = reps,
            prefix = prefix,
            quantile = quantile, // seed = seed
        )
        asn.sample_size = sample_size
        return sample_size
    }

    fun estimateSampleSize(x: DoubleArray, alpha: Double, reps: Int, prefix: Boolean, quantile: Double): Int {

        val sams = IntArray(reps)
        val pfx = if (prefix) x else DoubleArray(0)
        val ran_len = if (prefix) (N - x.size) else N
        repeat(reps) {
            val choices = python_choice(x, size = ran_len)
            val pop = numpy_append(pfx, choices) // tile data to make the population
            val (_, p_history) = this.testFn.test(pop)
            val crossed = p_history.map { it <= alpha }
            val crossedCount = crossed.filter { it }.count()
            sams[it] = if (crossedCount == 0) N else (indexFirstTrue(crossed) + 1)
        }
        sams.sort() // sort in place
        val sam_size = numpy_quantile(sams, quantile)
        return sam_size
    }

 */


// this is optimal_comparison_noP1
fun optimal_comparison(u: Double, rate_error_2: Double = 1e-4): Double {
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
    val result = (1 - u * (1 - p2)) / (2 - 2 * u) + u * (1 - p2) - .5
    return result
}