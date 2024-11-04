package org.cryptobiotic.rlauxe.core.sampling

import org.cryptobiotic.rlauxe.core.AuditPolling
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.indexFirstTrue
import org.cryptobiotic.rlauxe.util.numpy_append
import org.cryptobiotic.rlauxe.util.numpy_quantile
import kotlin.math.ceil
import kotlin.math.max

// assume use_style = true
class FindSampleSize(val N: Int, val error_rate_1: Double, val error_rate_2: Double, val reps: Int, val quantile: Double) {
/*
    fun find_sample_size(
        audit: AuditPolling,
        contests: List<ContestUnderAudit>,
        cvrs: List<CvrUnderAudit>,
    ): Int {
        // unless style information is being used, the sample size is the same for every contest.
        val old_sizes: MutableMap<Int, Int> =
            contests.associate { it.idx to 0 }.toMutableMap()

        for (contest in contests) {
            val contestId = contest.idx
            old_sizes[contestId] = cvrs.filter { it.hasContest(contestId) }.map { if (it.sampled) 1 else 0 }.sum()

            // max sample size over all assertions in this contest
            var max_size = 0
            val assertions = audit.assertions[contestId]!!
            for ((_, asn) in assertions) {
                if (!asn.proved) {
                    max_size = max(
                        max_size,
                        asn.estimateSampleSize(
                            rate1 = this.error_rate_1,
                            rate2 = this.error_rate_2,
                            reps = this.reps, quantile = this.quantile,
                        )
                    )
                }
            }
            contest.sampleSize = max_size
        }

        // setting p
        for (cvr in cvrs) {
            if (cvr.sampled) {
                cvr.p = 1.0
            } else {
                cvr.p = 0.0
                for (con in contests) {
                    if (cvr.hasContest(con.idx) && !cvr.sampled) {
                        val p1 = con.sampleSize.toDouble() / (con.ncards!! - old_sizes[con.idx]!!)
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

    fun Assertion.estimateSampleSizePolling(
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
}