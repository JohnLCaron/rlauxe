package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.SimContest
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.workflow.AuditConfig
import org.cryptobiotic.rlauxe.workflow.ComparisonErrorRates
import org.cryptobiotic.rlauxe.workflow.RunTestRepeatedResult
import org.cryptobiotic.rlauxe.workflow.runTestRepeated
import kotlin.math.min

// for the moment assume use_style = true, mvrs = null, so initial estimate only
class EstimateSampleSize(val auditConfig: AuditConfig) {

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    //// Polling

    // called from PollingWorkflow
    fun simulateSampleSizePollingContest(
        contestUA: ContestUnderAudit,
        prevMvrs: List<CvrIF>, // TODO should be used for subsequent round estimation
        maxSamples: Int,
        roundIdx: Int,
        show: Boolean = false
    ): Int {
        val sampleSizes = mutableListOf<Int>()
        contestUA.pollingAssertions.map { assert ->
            if (!assert.proved) {
                if (roundIdx > 1) {
                    if (assert.samplesUsed == contestUA.Nc) {
                        println("***LimitReached $contestUA")
                        contestUA.done = true
                        contestUA.status = TestH0Status.LimitReached
                    }
                    // TODO can we use the pvalue from last round to get better estimate? why 100? should be percent ??
                    assert.estSampleSize = min(assert.samplesUsed + (roundIdx-1) * 100, contestUA.Nc)
                    sampleSizes.add(assert.estSampleSize)
                } else {
                    val result = simulateSampleSizePollingAssorter(contestUA, assert.assorter, maxSamples)
                    if (result.failPct() > 80.0) {
                        assert.estSampleSize = result.findQuantile(auditConfig.quantile)
                        println("***FailPct $contestUA ${result.failPct()} > 80% size=${assert.estSampleSize}")
                        contestUA.done = true
                        contestUA.status = TestH0Status.FailPct
                    } else {
                        val size = result.findQuantile(auditConfig.quantile)
                        assert.estSampleSize = min(size, contestUA.Nc)
                        sampleSizes.add(assert.estSampleSize)
                    }
                }
                if (show) println("  ${contestUA.name} ${assert}")
            }

 /*               val result = simulateSampleSizePollingAssorter(contestUA, assert.assorter, maxSamples)
                val size = result.findQuantile(auditConfig.quantile)
                assert.estSampleSize = size + (roundIdx - 1) * 100  // TODO how to increase sample size ??
                sampleSizes.add(assert.estSampleSize)
                if (show) println(" simulateSampleSizes ${assert} est=$size failed=${df(result.failPct())}")
            } */
        }
        contestUA.estSampleSize = if (sampleSizes.isEmpty()) 0 else sampleSizes.max()
        if (show) println(" ${contestUA}")
        return contestUA.estSampleSize
    }

    // also called from plotter testFindSampleSize
    fun simulateSampleSizePollingAssorter(
        contestUA: ContestUnderAudit,
        assorter: AssorterFunction,
        maxSamples: Int,
    ): RunTestRepeatedResult {
        val margin = assorter.reportedMargin()
        val simContest = SimContest(contestUA.contest, assorter)
        val cvrs = simContest.makeCvrs()
        require(cvrs.size == contestUA.ncvrs)

        val sampler = if (auditConfig.fuzzPct == null) {
            PollWithoutReplacement(contestUA, cvrs, assorter)
        } else {
            PollingFuzzSampler(auditConfig.fuzzPct, cvrs, contestUA, assorter)
        }

        return simulateSampleSizeAlphaMart(sampler, margin, assorter.upperBound(), maxSamples, contestUA.Nc)
    }

    fun simulateSampleSizeAlphaMart(
        sampleFn: SampleGenerator,
        margin: Double,
        upperBound: Double,
        maxSamples: Int,
        Nc: Int,
        moreParameters: Map<String, Double> = emptyMap(),
    ): RunTestRepeatedResult {
        val eta0 = margin2mean(margin)
        val minsd = 1.0e-6
        val t = 0.5
        val c = (eta0 - t) / 2

        val estimFn = TruncShrinkage(
            N = Nc,
            withoutReplacement = true,
            upperBound = upperBound,
            d = auditConfig.d1,
            eta0 = eta0,
            minsd = minsd,
            c = c,
        )
        val testFn = AlphaMart(
            estimFn = estimFn,
            N = Nc,
            upperBound = upperBound,
            withoutReplacement = true
        )

        // TODO use coroutines
        val result: RunTestRepeatedResult = runTestRepeated(
            drawSample = sampleFn,
            maxSamples = maxSamples,
            ntrials = auditConfig.ntrials,
            testFn = testFn,
            testParameters = mapOf("ntrials" to auditConfig.ntrials.toDouble(), "polling" to 1.0) + moreParameters,
            showDetails = false,
            margin = margin,
        )
        return result
    }

    ////////////////////////////////////////////////////////////////////////////////////////////
    //// Comparison

    fun simulateSampleSizeComparisonContest(
        contestUA: ContestUnderAudit,
        cvrs: List<Cvr>,
        mvrs: List<CvrIF>, // TODO use previous samples
        roundIdx: Int,
        show: Boolean = false
    ): Int {

        val sampleSizes = mutableListOf<Int>()
        contestUA.comparisonAssertions.map { assert ->
            if (!assert.proved) {
                if (roundIdx > 1) {
                    if (assert.samplesUsed == contestUA.Nc) {
                        println("***LimitReached $contestUA")
                        contestUA.done = true
                        contestUA.status = TestH0Status.LimitReached
                    }
                    // TODO can we use the pvalue from last round to get better estimate?
                    assert.estSampleSize = min(assert.samplesUsed + (roundIdx-1) * 100, contestUA.Nc) // TODO why 100? should be percent ??
                    sampleSizes.add(assert.estSampleSize)
                } else {
                    val result = simulateSampleSizeAssorter(contestUA, assert.assorter, cvrs,)
                    if (result.failPct() > 80.0) { // TODO what should this be? allow to proceed to round1 with max? TODO port to polling.
                        assert.estSampleSize = result.findQuantile(auditConfig.quantile)
                        println("***FailPct $contestUA ${result.failPct()} > 80% size=${assert.estSampleSize}")
                        contestUA.done = true
                        contestUA.status = TestH0Status.FailPct
                    } else {
                        val size = result.findQuantile(auditConfig.quantile)
                        assert.estSampleSize = min(size, contestUA.Nc)
                        sampleSizes.add(assert.estSampleSize)
                    }
                }
                if (show) println("  ${contestUA.name} ${assert}")
            }
        }
        contestUA.estSampleSize = if (sampleSizes.isEmpty()) 0 else sampleSizes.max()
        if (show) println(" ${contestUA}")
        return contestUA.estSampleSize
    }

    fun simulateSampleSizeAssorter(
        contestUA: ContestUnderAudit,
        cassorter: ComparisonAssorter,
        cvrs: List<Cvr>,
    ): RunTestRepeatedResult {
        val errorRates = ComparisonErrorRates.getErrorRates(contestUA.ncandidates, auditConfig.fuzzPct)
        val sampler = if (auditConfig.fuzzPct == null) {
            // ComparisonSamplerSimulation carefully adds that number of errors. So simulation has that error in it.
            ComparisonSamplerSimulation(cvrs, contestUA, cassorter, errorRates)
        } else {
            ComparisonFuzzSampler(auditConfig.fuzzPct, cvrs, contestUA, cassorter)
        }

        // we need a permutation to get uniform distribution of errors, since the ComparisonSamplerSimulation puts all the errros
        // at the beginning
        sampler.reset()

        return simulateSampleSizeBetaMart(
            auditConfig,
            sampler,
            cassorter.margin,
            cassorter.noerror,
            cassorter.upperBound(),
            contestUA.ncvrs,
            contestUA.Nc,
            ComparisonErrorRates.getErrorRates(contestUA.ncandidates, auditConfig.fuzzPct),
        )
    }

    fun simulateSampleSizeAssorterAlt(
        fuzzPct: Double,
        contestUA: ContestUnderAudit,
        assorter: ComparisonAssorter,
        cvrs: List<Cvr>,
        moreParameters: Map<String, Double> = emptyMap(),
    ): RunTestRepeatedResult {
        val sampler = ComparisonFuzzSampler(fuzzPct, cvrs, contestUA, assorter)
        return simulateSampleSizeBetaMart(
            auditConfig,
            sampler,
            assorter.margin,
            assorter.noerror,
            assorter.upperBound(),
            contestUA.ncvrs,
            contestUA.Nc,
            ComparisonErrorRates.getErrorRates(contestUA.ncandidates, fuzzPct),
            moreParameters
        )
    }
}

fun simulateSampleSizeBetaMart(
    auditConfig: AuditConfig,
    sampleFn: SampleGenerator,
    margin: Double,
    noerror: Double,
    upperBound: Double,
    maxSamples: Int,
    Nc: Int,
    errorRates: List<Double>,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {

    val optimal = AdaptiveComparison(
        Nc = Nc,
        withoutReplacement = true,
        a = noerror,
        d1 = auditConfig.d1,
        d2 = auditConfig.d2,
        p1 = errorRates[0],
        p2 = errorRates[1],
        p3 = errorRates[2],
        p4 = errorRates[3],
    )
    val testFn = BettingMart(
        bettingFn = optimal,
        Nc = Nc,
        noerror = noerror,
        upperBound = upperBound,
        withoutReplacement = false)

    // TODO use coroutines
    val result: RunTestRepeatedResult = runTestRepeated(
        drawSample = sampleFn,
        maxSamples = maxSamples,
        ntrials = auditConfig.ntrials,
        testFn = testFn,
        testParameters = mapOf("p1" to optimal.p1, "p2" to optimal.p2, "p3" to optimal.p3, "p4" to optimal.p4) + moreParameters,
        showDetails = false,
        margin = margin,
    )
    return result
}

/////////////////////////////////////////////////////////////////////////////////

//4.a) Pick the (cumulative) sample sizes {𝑆_𝑐} for 𝑐 ∈ C to attain by the end of this round of sampling.
//	    The software offers several options for picking {𝑆_𝑐}, including some based on simulation.
//      The desired sampling fraction 𝑓_𝑐 := 𝑆_𝑐 /𝑁_𝑐 for contest 𝑐 is the sampling probability
//	      for each card that contains contest 𝑘, treating cards already in the sample as having sampling probability 1.
//	    The probability 𝑝_𝑖 that previously unsampled card 𝑖 is sampled in the next round is the largest of those probabilities:
//	      𝑝_𝑖 := max (𝑓_𝑐), 𝑐 ∈ C ∩ C𝑖, where C_𝑖 denotes the contests on card 𝑖.
//	b) Estimate the total sample size to be Sum(𝑝_𝑖), where the sum is across all cards 𝑖 except phantom cards.

// given the contest.sampleSize, we can calculate the total number of ballots.
// however, we get this from consistent sampling, which actually picks which ballots to sample.
/* dont really need
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
                    val p1 = con.estSampleSize.toDouble() / (con.Nc!! - old_sizes[con.id]!!)
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

// STYLISH 4 a,b. I think maybe only works when you use sampleThreshold ??
fun computeSampleSizePolling(
    rcontests: List<ContestUnderAudit>,
    ballots: List<BallotUnderAudit>,
): Int {
    ballots.forEach { ballot ->
        if (ballot.sampled) {
            ballot.p = 1.0
        } else {
            ballot.p = 0.0
            for (con in rcontests) {
                if (ballot.hasContest(con.id) && !ballot.sampled) {
                    val p1 = con.estSampleSize.toDouble() / con.Nc
                    ballot.p = max(p1, ballot.p)
                }
            }
        }
    }
    val summ: Double = ballots.filter { !it.phantom }.map { it.p }.sum()
    return ceil(summ).toInt()
}

 */

