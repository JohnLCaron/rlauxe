package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.AuditType
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.*
import java.security.SecureRandom
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

//////////////////////////////////////////////////////////////////////
//// Originally from SHANGRLA Audit.py, taken from shangrla-kotlin start package.
// A giant glob of code, to be removed
//
class Audit(
    val quantile: Double = 0.8,
    val error_rate_1: Double = 0.001, // rate of 1-vote overstatement errors, for comparison simulation
    val error_rate_2: Double = 0.0, // rate of 2-vote overstatement errors, for comparison simulation
    val reps: Int = 10,
    val auditType: AuditType = AuditType.CARD_COMPARISON,
    val use_styles: Boolean = true,
) {

    init {
        require(quantile >= 0.0 && quantile <= 1.0) { "quantile must be between 0 and 1" }
        require(error_rate_1 >= 0) { "expected rate of 1-vote errors must be nonnegative" }
        require(error_rate_2 >= 0) { "expected rate of 2-vote errors must be nonnegative" }
    }

    // TODO this assumes use_styles == true
    //     """
    //        Estimate sample size for each contest and overall to allow the audit to complete.
    //
    //        Parameters
    //        ----------
    //        contests:
    //        cvrs: the full set of CVRs, including phantoms
    //        mvr_sample: manually ascertained votes
    //        cvr_sample: CVRs corresponding to the cards that were selected to be sampled? (LOOK was "manually inspected")
    //
    //        Returns
    //        -------
    //        new_size: int
    //            new sample size
    //
    //        Side effects
    //        ------------
    //        sets c.sample_size for each Contest in contests
    //        if use_style, sets cvr.p for each CVR
    //        """
    fun find_sample_size(
        contests: List<Contest>,
        cvrs: List<Cvr>,
        mvr_sample: List<Cvr> = emptyList(), // must be empty the first time
        cvr_sample: List<Cvr> = emptyList(), // wtf?
    ): Int {
        // unless style information is being used, the sample size is the same for every contest.
        val old_sizes: MutableMap<String, Int> =
            contests.associate { it.id to 0 }.toMutableMap()

        for (contest in contests) {
            val contestId = contest.id
            old_sizes[contestId] = cvrs.filter { it.has_contest(contestId) }.map { if (it.sampled) 1 else 0 }.sum()

            // max sample size over all assertions in this contest
            var max_size = 0
            for ((_, asn) in contest.assertions) {
                if (!asn.proved) {
                    if (mvr_sample.isNotEmpty()) { // use MVRs to estimate the next sample size. Set `prefix=True` to use data
                        val (data, _) = asn.mvrs_to_data(mvr_sample, cvr_sample)
                        max_size = max(
                            max_size,
                            asn.find_sample_size_with_data(
                                data = data, prefix = true,
                                reps = this.reps, quantile = this.quantile,
                            )
                        )
                    } else {
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
            }
            contest.sample_size = max_size
        }

        // setting p
        for (cvr in cvrs) {
            if (cvr.sampled) {
                cvr.p = 1.0
            } else {
                cvr.p = 0.0
                for (con in contests) {
                    if (cvr.has_contest(con.id) && !cvr.sampled) {
                        val p1 = con.sample_size.toDouble() / (con.ncards - old_sizes[con.id]!!)
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

    // just pick sampleSize unique samples. do fancy stuff later
    fun assign_sample_nums(cvrs: List<Cvr>, sampleSize: Int): Set<Cvr> {
        val result = mutableSetOf<Cvr>()
        while (result.size < sampleSize) {
            val sample = random.nextInt(cvrs.size)
            result.add(cvrs[sample])
        }
        return result
    }

    companion object {
        val random = SecureRandom.getInstanceStrong()
    }

}

class Assertion(
    val contest: Contest,
    val winner: String,
    val loser: String,
    val assorter: Assorter,
    initialTest: NonnegMean,
) {

    var p_value: Double? = null
    var p_history: List<Double>? = null
    var proved: Boolean = false
    var margin: Double = Double.POSITIVE_INFINITY   // "reported assorter margin"
    var sample_size: Int? = null
    private var test: NonnegMean = initialTest

    fun name() = "$winner v $loser"

    override fun toString(): String {
        return "Assertion(contest=${contest.id}, upperBound=${assorter.upperBound}, winner='$winner', loser='$loser', " +
                "margin=$margin, p_value=$p_value, p_history=${p_history?.size}, proved=$proved, sample_size=$sample_size)"
    }

    fun testFn(x: DoubleArray) = test.testFn.test(x)

    // set margin, test.mean
    fun set_margin_from_cvrs(cvrs: List<Cvr>): Double {
        val amean = this.assorter.mean(cvrs)
        if (amean < .5) {
            println("assertion $this not satisfied by CVRs: mean value is ${amean}")
        }

        // Define v ≡ 2Āc − 1, the reported assorter margin. In a two-candidate plurality contest, v
        // is the fraction of ballot cards with valid votes for the reported winner, minus the fraction
        // with valid votes for the reported loser. This is the diluted margin of [22,12]. (Margins are
        // traditionally calculated as the difference in votes divided by the number of valid votes.
        // Diluted refers to the fact that the denominator is the number of ballot cards, which is
        // greater than or equal to the number of valid votes.)
        this.margin = 2 * amean - 1 // eq 3

        val u = when (this.contest.audit_type) {
            AuditType.POLLING -> this.assorter.upperBound
            AuditType.CARD_COMPARISON, AuditType.ONEAUDIT -> 2 / (2 - this.margin / this.assorter.upperBound)
            else -> throw NotImplementedError("audit type ${this.contest.audit_type} not supported")
        }

        // Tests of the hypothesis that the mean of a population of values in [0, u] is less than or equal to t
        // so u must be the max value of the population.
        // the math seems to be on page 10, if you take tau = 2.
        this.test = this.test.changeMean(newu=u)
        return this.margin
    }

    fun set_margin_from_tally(tallyInput: Map<String, Int>? = null): Double { // cand -> count.

        val tally = tallyInput ?: this.contest.tally
        if (this.contest.choice_function in listOf(SocialChoiceFunction.PLURALITY, SocialChoiceFunction.APPROVAL)) {
            this.margin = (tally[this.winner]!! - tally[this.loser]!!).toDouble() / this.contest.ncards // // TODO check nullable
        } else if (this.contest.choice_function == SocialChoiceFunction.SUPERMAJORITY) {
            if (this.winner == "Candidates.NO_CANDIDATE.name" || this.loser != "Candidates.ALL_OTHERS.name") {
                throw NotImplementedError("TO DO: currently only support super-majority with a winner")
            } else {
                // val q = np.sum([tally[c] for c in this.contest.candidates])/this.contest.cards
                val q = this.contest.candidates.map { tally[it]!! }.sum() / this.contest.ncards // LOOK check nullable
                val p = tally[this.winner]!! / this.contest.ncards // LOOK check nullable
                this.margin = q * (p / this.contest.share_to_win - 1)
            }
        } else {
            throw NotImplementedError("social choice function ${this.contest.choice_function} not supported")
        }
        return this.margin
    }

    fun mvrs_to_data(mvr_sample: List<Cvr>, cvr_sample: List<Cvr>): Pair<DoubleArray, Double> {
        require(this.margin != Double.POSITIVE_INFINITY) { "Margin is not set"  }
        require(this.margin > 0.0) { "Margin ${this.margin} is nonpositive" }
        val upper_bound = this.assorter.upperBound
        val con = this.contest

        var u: Double
        require(mvr_sample.size == cvr_sample.size)
        val cvr2: List<Pair<Cvr, Cvr>> = mvr_sample.zip(cvr_sample)
        val d = cvr2.filter { (_, cvr) -> (cvr.has_contest(con.id) && cvr.sample_num <= con.sample_threshold!!) }
            .map { (mvr, cvr) -> this.overstatement_assorter(mvr, cvr) }
        u = 2 / (2 - this.margin / upper_bound)

        // convert to double array
        val fa = DoubleArray(d.size) { d[it] }
        return Pair(fa, u)
    }

    /*
        Estimate sample size needed to reject the null hypothesis that the assorter mean is <=1/2,
        for the specified risk function, given:
        - for comparison audits, the assorter margin and assumptions about the rate of overstatement errors
        - for polling audits, either a set of assorter values, or the assumption that the reported tallies are correct

        If `data is not None`, uses data to make the estimate. There are three strategies:
            1. if `reps is None`, tile the data to make a list of length N
            2. if `reps is not None and not prefix`, sample from the data with replacement to make `reps` lists of length N
            3. if `reps is not None and prefix`, start with `data`, then draw N-len(data) times from data with
               replacement to make `reps` lists of length N

        If `data is None`, constructs values from scratch.
            - For polling audits, values are inferred from the reported tallies. Since contest.tally only reports
                actual candidate totals, not IRV/RAIRE pseudo-candidates, this is not implemented for IRV.
            - For comparison audits, there are two strategies to construct the values:
                1. Systematically interleave small and large values, starting with a small value (`reps is None`)
                2. Sample randomly from a set of such values
                The rate of small values is `rate_1` if `rate_1 is not None`. If `rate is None`, for POLLING audits, gets
                the rate of small values from the margin.
                For Audit.AUDIT_TYPE.POLLING audits, the small values are 0 and the large values are `u`; the rest are 1/2.
                For Audit.AUDIT_TYPE.CARD_COMPARISON audits, the small values are the overstatement assorter for an
                overstatement of `u/2` and the large values are the overstatement assorter for an overstatement of 0.

        **Assumes that this.test.u has been set appropriately for the audit type (polling or comparison).**
        **Thus, for comparison audits, the assorter margin should be be set before calling this function.**

        Parameters
        ----------
        data: DoubleArray; observations on which to base the calculation. If `data is not None`, uses them in a bootstrap
            approach, rather than simulating errors.
            If `this.contest.audit_type==Audit.POLLING`, the data should be (simulated or actual) values of
            the raw assorter.
            If `this.contest.audit_type==Audit.CARD_COMPARISON`, the data should be (simulated or actual)
            values of the overstatement assorter.
        prefix: bool; prefix the data, then sample or tile to produce the remaining values
        rate_1: float; assumed rate of "small" values for simulations (1-vote overstatements). Ignored if `data is not None`
            If `rate_1 is None and this.contest.audit_type==Audit.POLLING` the rate of small values is inferred
            from the margin
        rate_2: float; assumed rate of 0s for simulations (2-vote overstatements).
        reps: int; if `reps is None`, builds the data systematically
            if `reps is not None`, performs `reps` simulations to estimate the `quantile` quantile of sample size.
        quantile: float; if `reps is not None`, quantile of the distribution of sample sizes to return
            if `reps is None`, ignored
        seed: int; if `reps is not None`, use `seed` as the seed in numpy.random to estimate the quantile

        Returns
        -------
        sample_size: estimated to be sufficient to confirm the outcome if data are generated according to the assumptions

        Side effects
        ------------
        sets the sample_size attribute of the assertion
        */

    // break these two cases apart `data is not None` vs `data is None`
    fun find_sample_size_with_data(
        data: DoubleArray,
        prefix: Boolean = false,
        reps: Int,
        quantile: Double = 0.5, // quantile of the distribution of sample sizes to return
        seed: Int = 1234567890 // seed in numpy.random to estimate the quantile
    ): Int {
        return this.test.estimateSampleSize(
            x = data, alpha = this.contest.risk_limit, reps = reps,
            prefix = prefix, quantile = quantile, // seed = seed
        )
    }

    fun estimateSampleSize(
        prefix: Boolean = false,
        rate1: Double? = null,
        rate2: Double? = null,
        reps: Int,
        quantile: Double, // quantile of the distribution of sample sizes to return
    ): Int {
        require(this.margin != Double.POSITIVE_INFINITY) { "Margin is not set"  }
        require(this.margin > 0.0) { "Margin ${this.margin} is nonpositive" }

        return if (this.contest.audit_type == AuditType.POLLING) estimateSampleSizePolling(prefix, reps, quantile)
        else estimateSampleSizeComparision(prefix, rate1, rate2, reps, quantile)
    }

    fun estimateSampleSizePolling(
        prefix: Boolean = false,
        reps: Int,
        quantile: Double = 0.5, // quantile of the distribution of sample sizes to return
    ): Int {

        val big = this.assorter.upperBound

        val n_0 = this.contest.tally[this.loser]!! // LOOK nullability
        val n_big = this.contest.tally[this.winner]!! // LOOK nullability
        val n_half = this.test.N - n_0 - n_big
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

    fun estimateSampleSizeComparision(
        prefix: Boolean = false,
        rate1: Double? = null,
        rate2: Double? = null,
        reps: Int,
        quantile: Double = 0.5, // quantile of the distribution of sample sizes to return
    ): Int {
        val big = this.make_overstatement(overs = 0.0)
        val small = this.make_overstatement(overs = 0.5)
        println("  Assertion ${name()} big = ${big} small = ${small}")

        val rate_1 = rate1 ?: ((1 - this.margin) / 2)   // rate of small values
        val x = DoubleArray(this.test.N) { big } // array N floats, all equal to big

        val rate_1_i = numpy_arange(0, this.test.N, step = (1.0 / rate_1).toInt())
        val rate_2_i = if (rate2 != null  && rate2 != 0.0) numpy_arange(0, this.test.N, step = (1.0 / rate2).toInt()) else IntArray(0)
        rate_1_i.forEach { x[it] = small }
        rate_2_i.forEach { x[it] = 0.0 }

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


    fun make_overstatement(overs: Double): Double {
        // TODO reference in paper
        val result =  (1 - overs / this.assorter.upperBound) / (2 - this.margin / this.assorter.upperBound)
        return result
    }

    //  assorter that corresponds to normalized overstatement error for an assertion
    fun overstatement_assorter(mvr: Cvr, cvr: Cvr, use_style: Boolean = true): Double {
        return (1 - this.assorter.overstatement(mvr, cvr, use_style) / this.assorter.upperBound) /
                (2 - this.margin / this.assorter.upperBound)
    }

}

data class Contest(
    val id: String,
    val n_winners: Int = 1,
    val candidates: List<String>,
    val choice_function: SocialChoiceFunction = SocialChoiceFunction.PLURALITY,
    val audit_type: AuditType = AuditType.CARD_COMPARISON,
    val risk_limit: Double = 0.05,
    val share_to_win: Double = .5, // for supermajority

    val ncards: Int,
    val winners: List<String>,
    val tally: Map<String, Int>,
) {
    val assertions = mutableMapOf<String, Assertion>() // does it really want to be a map ??
    var sample_size: Int = 0
    var sample_threshold: Int = 0

    init {
        require (n_winners > 0 && n_winners < candidates.size)
        require (share_to_win >= .5) // could allow this
    }

    fun addAssertion(a: Assertion) = assertions.put(a.name(), a)
}

abstract class Assorter(
    val contest: Contest,
    val upperBound: Double, // a priori upper bound on the value the assorter can take
    val winner: String,
    val loser: String
) {
    abstract fun assort(cvr: Cvr): Double

    // Compute the arithmetic mean of the assort value over the cvrs that have this contest, // eq 2
    fun mean(cvrs: List<Cvr>, use_style: Boolean = true): Double {
        //           val result = cvr_list.filter { cvr -> if (use_style) cvr.has_contest(this.contest.id) else true }
        return cvrs.filter { cvr ->  if (use_style) cvr.has_contest(this.contest.id) else true }
            .map { this.assort(it) }
            .average()
    }

    // TODO paper reference
    fun overstatement(mvr: Cvr, cvr: Cvr, use_style: Boolean = true): Double {
        // sanity check
        if (use_style && !cvr.has_contest(contest.id)) {
            throw Exception("use_style==True but Cvr '${cvr.id}' does not contain contest '${this.contest.id}'")
        }
        // assort the MVR
        val mvr_assort = if (mvr.phantom || (use_style && !mvr.has_contest(this.contest.id))) 0.0
        else this.assort(mvr)

        // assort the CVR
        //        cvr_assort = (
        //            self.tally_pool_means[cvr.tally_pool]
        //            if cvr.pool and self.tally_pool_means is not None
        //            else int(cvr.phantom) / 2 + (1 - int(cvr.phantom)) * self.assort(cvr)
        //        )
        // val cvr_assort: Double = if (cvr.pool && this.tally_pool_means != null) this.tally_pool_means!![cvr.tally_pool]!!
        // else phantomValue / 2 + (1 - phantomValue) * this.assort(cvr)
        val phantomValue = if (cvr.phantom) 1.0 else 0.0 // TODO really ? int(cvr.phantom)
        val temp = phantomValue / 2 + (1 - phantomValue) * this.assort(cvr)
        val cvr_assort = if (cvr.phantom) .5 else this.assort(cvr)
        require(temp == cvr_assort)

        return cvr_assort - mvr_assort
    }
}

class PluralityAssorter(contest: Contest, winner: String, loser: String): Assorter(contest, 1.0, winner, loser) {

    override fun assort(cvr: Cvr): Double {
        val w = cvr.get_vote_for(contest.id, winner)
        val l = cvr.get_vote_for(contest.id, loser)
        return (w - l + 1) * 0.5 // eq 1.
    }
}

class SupermajorityAssorter(contest: Contest, upperBound: Double, winner: String, loser: String)
    : Assorter(contest, upperBound, winner, loser) {

    override fun assort(cvr: Cvr): Double {
        val w = cvr.get_vote_for(contest.id, winner)
        // TODO is there something weird going on about which candidates to check ?
        return if (cvr.has_one_vote(contest.id, contest.candidates)) (w / (2 * contest.share_to_win)) else .5
    }
}

data class Cvr(
    val id: String,
    val votes: Map<String, Map<String, Int>>, // contest : candidate : vote
    val phantom: Boolean = false
) {
    var sample_num: Int = 0
    var sampled: Boolean = false
    var p: Double = Double.POSITIVE_INFINITY

    fun has_contest(contest_id: String): Boolean = votes[contest_id] != null

    fun get_vote_for(contestId: String, candidate: String): Int {
        return if (votes[contestId] == null) 0 else votes[contestId]!![candidate] ?: 0
    }

    // Is there exactly one vote among the candidates in the contest `contest_id`?
    fun has_one_vote(contest_id: String, candidates: List<String>): Boolean {
        val contestVotes = this.votes[contest_id] ?: return false
        val totalVotes = candidates.map{ contestVotes[it] ?: 0 }.sum()
        return (totalVotes == 1)
    }

    companion object {
        // also see make_phantoms in CvrBuilder

        fun tabulateVotes(cvrs: List<Cvr>): Map<String, Map<String, Int>> {
            val r = mutableMapOf<String, MutableMap<String, Int>>()
            for (cvr in cvrs) {
                for ((con, conVotes) in cvr.votes) {
                    val accumVotes = r.getOrPut(con) { mutableMapOf() }
                    for ((cand, vote) in conVotes) {
                        val accum = accumVotes.getOrPut(cand) { 0 }
                        accumVotes[cand] = accum + vote
                    }
                }
            }
            return r
        }

        // Number of cards in each contest, return contestId -> ncards
        fun cardsPerContest(cvrs: List<Cvr>): Map<String, Int> {
            val d = mutableMapOf<String, Int>()
            for (cvr in cvrs) {
                for (con in cvr.votes.keys) {
                    val accum = d.getOrPut(con) { 0 }
                    d[con] = accum + 1
                }
            }
            return d
        }
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////
// NonnegMean

interface TestFn {
    // return p, p_history
    fun test(x: DoubleArray): Pair<Double, DoubleArray>
}

interface EstimatorFn {
    // return p, p_history
    fun eta(x: DoubleArray): DoubleArray
}

// Estimator of the true mean (theta)
typealias EstimatorLambda = (x: DoubleArray) -> DoubleArray

// Tests of the hypothesis that the mean of a population of values in [0, u] is less than or equal to t.
// probably only need AlphaMart
enum class TestFnType {
    ALPHA_MART,
    BETTING_MART,
    KAPLAN_KOLMOGOROV,
    KAPLAN_MARKOV,
    KAPLAN_WALD,
    WALD_SPRT,
}

enum class EstimFnType {
    OPTIMAL,
    FIXED,
    SHRINK_TRUNC,
}

private val debugNonnegMean = false

// Tests of the hypothesis that the mean of a population of values in [0, u] is less than or equal to t
data class NonnegMean(
    val N: Int, // If N is np.inf, it means the sampling is with replacement
    val withReplacement: Boolean = false, // TODO
    val t: Double = 0.5,        // the hypothesized mean "under the null".
    val u: Double = 1.0,
    val testFn: TestFn
) {
    val isFinite = !withReplacement

    fun changeMean(newu: Double): NonnegMean {
        return if (testFn is AlphaMart) {
            val testFn = AlphaMart(N, withReplacement, t = t, u = newu, testFn.estimFnType)
            return NonnegMean(N, withReplacement, t = t, u = newu, testFn)
        } else {
            this.copy(u=newu)
        }

    }

    // TODO paper reference
    // estimate the quantile by a bootstrap-like simulation
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

    // If `reps is None`, tiles copies of `x` to produce a list of length `N`.
    fun estimateSampleSizeTiled(x: DoubleArray, alpha: Double): Int {

        //            pop = np.repeat(np.array(x), math.ceil(N/len(x)))[0:N]  # tile data to make the population
        //            p = self.test(pop, **kwargs)[1]
        //            crossed = (p<=alpha)
        //            sam_size = int(N if np.sum(crossed)==0 else (np.argmax(crossed)+1))

        //            crossed = p <= alpha : Array of true or false
        //            sum = np.sum(crossed) : count of true
        //            argmax = np.argmax(crossed): ?? the first index that has true
        //            sam_size = int(N if sum == 0 else (argmax + 1))

        val repeats = ceil(N.toDouble() / x.size).toInt()
        val pop = numpy_repeat(x, repeats) // tile data to make the population
        require(pop.size == N)
        val (_, p_history) = this.testFn.test(pop)

        // int(N if np.sum(crossed) == 0 else (np.argmax(crossed) + 1))
        // i guess you could just find the first index thats true.
        val firstIndex = p_history.indexOfFirst { it <= alpha }
        return if (firstIndex < 0) N else firstIndex + 1
    }

    companion object {
        fun makeAlphaMart(
            N: Int,
            t: Double,
            u: Double,
            withReplacement: Boolean = false,
            estimFnType: EstimFnType = EstimFnType.OPTIMAL
        ): NonnegMean {
            val testFn = AlphaMart(N, withReplacement, t = t, u = u, estimFnType)
            return NonnegMean(N, withReplacement, t = t, u = u, testFn)
        }
    }
}

// TODO paper reference
// Finds the ALPHA martingale for the hypothesis that the population mean is less than or equal to t using a martingale
// method, for a population of size N, based on a series of draws x.
//     u > 0 (default 1); upper bound on the population
// estimFn: Estimation of the mean (aka eta_j)
class AlphaMart(val N: Int, val withReplacement: Boolean, val t: Double, val u: Double, val estimFnType: EstimFnType? = null,
                val estimFnOverride: EstimatorFn? = null,
) : TestFn {
    val estimFn: EstimatorLambda
    val isFinite = !withReplacement

    init {
        val eta = (t + (u - t) / 2) // initial estimate of the population mean
        val estimFixed: EstimatorLambda = { x -> this.fixed_alternative_mean(x, eta) }
        val estimOptimal: EstimatorLambda = { x -> this.optimal_comparison() } // TODO pass error_2_rate here ?
        val estimTrunc: EstimatorLambda = { x -> estimFnOverride!!.eta(x) }
        estimFn = when (estimFnType) {
            EstimFnType.OPTIMAL -> estimOptimal
            EstimFnType.SHRINK_TRUNC -> estimTrunc
            else -> estimFixed
        }
    }

    // x are the samples of the population
    // Returns
    //   p: sequentially valid p-value of the hypothesis that the population mean is less than or equal to t
    //   p_history: sample by sample history of p-values. Not meaningful unless the sample is in random order.
    override fun test(x: DoubleArray): Pair<Double, DoubleArray> {
        // val atol = kwargs.get("atol", 2 * np.finfo(float).eps)
        // val rtol = kwargs.get("rtol", 10e-6)

        if (debugNonnegMean) println("   x = ${x.contentToString()}")

        // TODO This is eq 4 of ALPHA, p.5 :
        //      T_j = T_j-1 * (X_j * eta_j / mu_j + (u - X_j) * (u - eta_j) / ( u - mu_j)) / u
        //    where mu = m, T0 = 1.
        //
        val etaj = this.estimFn(x)
        if (debugNonnegMean) println("   etaj = ${etaj.contentToString()}")

        val (_, Stot, _, m) = this.sjm(N, t, x)
        if (debugNonnegMean) println("   m = ${m.contentToString()}")

        //         with np.errstate(divide="ignore", invalid="ignore", over="ignore"):
        //            etaj = self.estim(x)
        //            terms = np.cumprod((x * etaj / m + (u - x) * (u - etaj) / (u - m)) / u)

        // testStatistic = if (populationMean < 0.0) Double.POSITIVE_INFINITY else {
        //    (testStatistic / upperBound) * (xj * etaj / populationMean + (upperBound - xj) * (upperBound - etaj) / (upperBound - populationMean))
        // }

        // meed both m and eta
        val tj =
            if (etaj.size == x.size) { DoubleArray(x.size) {
                val tmp1 = x[it] * etaj[it] / m[it]
                val tmp2 = (u - x[it]) * (u - etaj[it])
                val tmp3 =   (u - m[it])
                val tmp4 = (tmp1 + tmp2 / tmp3) / u
                (x[it] * etaj[it] / m[it] + (u - x[it]) * (u - etaj[it]) / (u - m[it])) / u
            }
            }
            else if (etaj.size == 1) DoubleArray(x.size) { (x[it] * etaj[0] / m[it] + (u - x[it]) * (u - etaj[0]) / (u - m[it])) / u }
            else throw RuntimeException("NonnegMean alpha_mart")
        // these are the "terms" = T_j = T_j-1 * (tj)
        val terms = numpy_cumprod(tj)
        if (debugNonnegMean) println("   tj = ${tj.contentToString()}")
        if (debugNonnegMean) println("   T = ${terms.contentToString()}")

        // terms[m > u] = 0  # true mean is certainly less than hypothesized
        repeat(terms.size) { if (m[it] > u) terms[it] = 0.0 } // true mean is certainly less than hypothesized

        // terms[np.isclose(0, m, atol=atol)] = 1  # ignore
        repeat(terms.size) { if (doubleIsClose(0.0, m[it])) terms[it] = 1.0 } // ignore

        // terms[np.isclose(0, terms, atol=atol)] = ( 1 } # martingale effectively vanishes; p-value 1
        repeat(terms.size) { if (doubleIsClose(0.0, terms[it])) terms[it] = 1.0 } // martingale effectively vanishes; p-value 1

        // terms[m < 0] = np.inf  # true mean certainly greater than hypothesized
        repeat(terms.size) { if (m[it] < 0.0) terms[it] = Double.POSITIVE_INFINITY } // true mean is certainly less than hypothesized

        // terms[-1] = ( np.inf if Stot > N * t else terms[-1] )  # final sample makes the total greater than the null
        if (Stot > N * t) terms[terms.size - 1] = Double.POSITIVE_INFINITY // final sample makes the total greater than the null

        // return min(1, 1 / np.max(terms)), np.minimum(1, 1 / terms)
        // np.minimum = element-wise minumum, presumably the smaller of 1 and 1/term
        // np.max = maximum of an array
        // min = min of an iterable

        // TODO This is eq 9 of ALPHA, p.5 :
        //  if θ ≤ µ, then P{ ∃j : Tj ≥ α−1 } ≤ α.
        //  That is, min(1, 1/Tj ) is an “anytime P -value” for the composite null hypothesis θ ≤ µ

        // return min(1, 1 / np.max(terms)), np.minimum(1, 1 / terms)
        val npmin = terms.map { min(1.0, 1.0 / it) }.toDoubleArray()
        val p = min(1.0, 1.0 / terms.max()) // seems wrong
        if (debugNonnegMean) println("   phistory = ${npmin.contentToString()}")

        return Pair(p, npmin)
    }

    fun sjm(N: Int, t: Double, x: DoubleArray): CumulativeSum {
        val cum_sum = numpy_cumsum(x)
        val S = DoubleArray(x.size + 1) { if (it == 0) 0.0 else cum_sum[it - 1] }   // 0, x_1, x_1+x_2, ...,
        val Stot = S.last()  // sample total ""array[-1] means the last element"
        val Sp = DoubleArray(x.size) { S[it] } // same length as the data.

//        j = np.arange(1, len(x) + 1)  # 1, 2, 3, ..., len(x)
//        assert j[-1] <= N, "Sample size is larger than the population!"
        val j = IntArray(x.size) { it + 1 } // 1, 2, 3, ..., len(x)
        require(!isFinite || x.size <= N) { "Sample size is larger than the population!" }
//        m = ( (N * t - S) / (N - j + 1) if np.isfinite(N) else t )  # mean of population after (j-1)st draw, if null is true (t=eta is the mean)
        // val m = if (withReplacement) doubleArrayOf(t) else DoubleArray(x.size) { (N * t - Sp[it]) / (N - j[it] + 1)  }
        val m = DoubleArray(x.size) {
            val m1 = (N * t - Sp[it])
            val m2 = (N - j[it] + 1)
            val m3 = m1 / m2
            if (isFinite) (N * t - Sp[it]) / (N - j[it] + 1) else t
        }
        return CumulativeSum(Sp, Stot, j, m)
    }

    // S : The cumulative sum of the input array `x`, excluding the last element.
    // Stot : he total sum of the input array `x`.
    // j : An array of indices from 1 to the length of `x`.
    // m : The mean of the population after each draw if the null hypothesis is true.
    data class CumulativeSum(val S: DoubleArray, val Stot: Double, val indices: IntArray, val mean: DoubleArray)

    // estimated value of the mean
    fun fixed_alternative_mean(x: DoubleArray, eta: Double): DoubleArray {
            val (_, _, _, m) = this.sjm(N, eta, x)

        // must be in [0,u]
        val negs = m.filter { it < 0.0 }
        if (negs.count() > 0) {
            println("Implied population mean is negative in ${negs.size} of ${x.size} terms")
        }
        val pos = m.filter { it > u }
        if (pos.count() > 0) {
            println("Implied population mean is greater than ${u} in ${pos.size} of ${x.size} terms")
        }
        return m
    }

    // estimated value of the mean
    fun optimal_comparison(rate_error_2: Double = 1e-4): DoubleArray {
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
        if (this.u == 1.0)
            throw RuntimeException("optimal_comparison: u ${this.u} must != 1")

        val p2 = rate_error_2 // getattr(self, "rate_error_2", 1e-4)  // rate of 2-vote overstatement errors
        val result = (1 - this.u * (1 - p2)) / (2 - 2 * this.u) + this.u * (1 - p2) - .5
        return doubleArrayOf(result)
    }
}

class ShrinkTrunc(val N: Int, // If N is np.inf, it means the sampling is with replacement
                  val withReplacement: Boolean = false,
                  val t: Double = 0.5,        // the hypothesized mean "under the null".
                  val u: Double = 1.0,
                  val minsd : Double,
                  val d: Int,
                  val eta: Double,
                  val f: Double,
                  val c: Double,
                  val eps: Double) : EstimatorFn {

    // section 2.5.2 of ALPHA, p 9.
    override fun eta(x: DoubleArray ): DoubleArray {
        val (S, _Stot, j, m) = this.sjm(N, t, x)
        // Welford's algorithm for running mean and running sd
        val mj = mutableListOf<Double>()
        mj.add(x[0])
        var sdj = mutableListOf<Double>()
        sdj.add(0.0)

        //        for i, xj in enumerate(x[1:]):
        //            mj.append(mj[-1] + (xj - mj[-1]) / (i + 1))
        //            sdj.append(sdj[-1] + (xj - mj[-2]) * (xj - mj[-1]))
        // enumerate returns Pair(index, element)
        for (idx in 0 until x.size-1) {
            val xj = x[idx+1]
            // mj.append(mj[-1] + (xj - mj[-1]) / (i + 1))
            mj.add(mj.last() + (xj - mj.last()) / (idx + 2)) // fixed in PR # 89
            // sdj.append(sdj[-1] + (xj - mj[-2]) * (xj - mj[-1]))
            sdj.add(sdj.last() + (xj - mj[mj.size - 2]) * (xj - mj.last()))
        }
        // sdj = np.sqrt(sdj / j)
        val sdj2 = sdj.mapIndexed { idx, it -> sqrt(it / j[idx]) } // j is the count
        val sdj3 = DoubleArray(sdj2.size) { if (it < 2) 1.0 else max(sdj2[it-1], minsd) }
        val weighted = sdj3.mapIndexed { idx, it -> ((d * eta + S[idx]) / (d + j[idx] - 1) + u * f / it) / (1 + f / it) }
        val npmax = weighted.mapIndexed { idx, it ->  max( it, m[idx] + c / sqrt((d + j[idx] - 1).toDouble())) }
        val etaj = npmax.map { min(u * (1 - eps), it) }
        return etaj.toDoubleArray()
    }
    //         This method calculates the cumulative sum of the input array `x`, the total sum of `x`,
//        an array of indices, and the mean of the population after each draw if the null hypothesis is true.
    fun sjm(N: Int, t: Double, x: DoubleArray): AlphaMart.CumulativeSum {
        val cum_sum = numpy_cumsum(x)
        val S = DoubleArray(x.size + 1) { if (it == 0) 0.0 else cum_sum[it - 1] }   // 0, x_1, x_1+x_2, ...,
        val Stot = S.last()  // sample total ""array[-1] means the last element"
        val Sp = DoubleArray(x.size) { S[it] } // same length as the data.

//        j = np.arange(1, len(x) + 1)  # 1, 2, 3, ..., len(x)
//        assert j[-1] <= N, "Sample size is larger than the population!"
        val j = IntArray(x.size) { it + 1 } // 1, 2, 3, ..., len(x)
        require(!withReplacement || x.size <= N) { "Sample size is larger than the population!" }
//        m = ( (N * t - S) / (N - j + 1) if np.isfinite(N) else t )  # mean of population after (j-1)st draw, if null is true (t=eta is the mean)
        // val m = if (withReplacement) doubleArrayOf(t) else DoubleArray(x.size) { (N * t - Sp[it]) / (N - j[it] + 1)  }
        val m = DoubleArray(x.size) {
            val m1 = (N * t - Sp[it])
            val m2 = (N - j[it] + 1)
            val m3 = m1 / m2
            if (withReplacement) t else (N * t - Sp[it]) / (N - j[it] + 1)
        }
        return AlphaMart.CumulativeSum(Sp, Stot, j, m)
    }
}

// Return the cumulative product of elements
fun numpy_cumprod(a: DoubleArray) : DoubleArray {
    val result = DoubleArray(a.size)
    result[0] = a[0]
    for (i in 1 until a.size) {
        result[i] = result[i-1] * a[i]
    }
    return result
}

// Return the cumulative product of elements
fun numpy_cumsum(a: DoubleArray) : DoubleArray {
    val result = DoubleArray(a.size)
    result[0] = a[0]
    for (i in 1 until a.size) {
        result[i] = result[i-1] + a[i]
    }
    return result
}

fun interleave_values(n_small: Int, n_med: Int, n_big: Int,
                      small: Double = 0.0, med: Double = 0.5, big: Double = 1.0): DoubleArray {
    val N = n_small + n_med + n_big
    val x = DoubleArray(N) // np.zeros(N)
    var i_small = 0
    var i_med = 0
    var i_big = 0
    var r_small = if (n_small != 0) 1.0 else 0.0
    var r_med = if (n_med != 0) 1.0 else 0.0
    var r_big = 1.0
    if (r_small != 0.0) {   //start with small
        x[0] = small
        i_small = 1
        r_small = (n_small - i_small).toDouble() / n_small
    } else if (r_med != 0.0) { // start with 1/2
        x[0] = med
        i_med = 1
        r_med = (n_med - i_med).toDouble() / n_med
    } else {
        x[0] = big
        i_big = 1
        r_big = (n_big - i_big).toDouble() / n_big
    }
    for (i in (1 until N)) {
        if (r_small > r_big) {
            if (r_med > r_small) {
                x[i] = med
                i_med += 1
                r_med = (n_med - i_med).toDouble() / n_med
            } else {
                x[i] = small
                i_small += 1
                r_small = (n_small - i_small).toDouble() / n_small
            }
        } else if (r_med > r_big) {
            x[i] = med
            i_med += 1
            r_med = (n_med - i_med).toDouble() / n_med
        } else {
            x[i] = big
            i_big += 1
            r_big = (n_big - i_big).toDouble() / n_big
        }
    }
    return x
}

fun python_choice(from: DoubleArray, size: Int): DoubleArray {
    val n = from.size
    if (n <= 0)
        println("HEY")
    return DoubleArray(size) { from[Random.nextInt(n)] }
}