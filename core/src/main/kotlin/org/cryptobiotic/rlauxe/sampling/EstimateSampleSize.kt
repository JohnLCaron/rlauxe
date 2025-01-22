package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.concur.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.OneAuditComparisonAssorter
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.workflow.AuditConfig
import org.cryptobiotic.rlauxe.workflow.AuditType
import org.cryptobiotic.rlauxe.workflow.ClcaErrorRates
import org.cryptobiotic.rlauxe.workflow.ClcaSimulationType
import kotlin.math.min
import kotlin.math.max


////////////////////////////////////////////////////////////////////////////////////////////
//// Comparison, Polling, OneAudit.

fun estimateSampleSizes(
    auditConfig: AuditConfig,
    contestsUA: List<ContestUnderAudit>,
    cvrs: List<Cvr>,        // Comparison only
    prevMvrs: List<Cvr>,
    roundIdx: Int,
    show: Boolean = false,
    nthreads: Int = 14,
): Int? {
    val tasks = mutableListOf<ConcurrentTaskG<EstimationResult>>()
    contestsUA.filter { !it.done }.forEach { contestUA ->
        tasks.addAll(makeEstimationTasks(auditConfig, contestUA, cvrs, prevMvrs, roundIdx))
    }
    // run tasks concurrently
    val estResults: List<EstimationResult> = ConcurrentTaskRunnerG<EstimationResult>(show).run(tasks, nthreads)

    // cant modify contestUA until out of the concurrent tasks
    estResults.forEach { estResult ->
        val task = estResult.task
        val result = estResult.repeatedResult
        if (estResult.failed) { // TODO 80% ?? settable ??
            task.assertion.estSampleSize = task.prevSampleSize + result.findQuantile(auditConfig.quantile)
            println("***FailPct ${task.contestUA} ${result.failPct()} > 80% size=${task.assertion.estSampleSize}")
            task.contestUA.done = true
            task.contestUA.status = TestH0Status.FailPct
        } else {
            var size = task.prevSampleSize + result.findQuantile(auditConfig.quantile)
            // val size = task.prevSampleSize + ceil(result.totalSamplesNeeded / result.ntrials.toDouble()).toInt()
            if (roundIdx > 1) {
                // make sure we grow at least 25% from previous estimate (TODO might need special code for nostyle)
                size = max(1.25 * task.contestUA.estSampleSize, size.toDouble()).toInt()
            }
            task.assertion.estSampleSize = min(size, task.contestUA.Nc)
            // if (show) println("  ${task.contestUA.name} ${task.assertion}")
        }
    }

    // pull out the sampleSizes for all successful assertions in the contest
    // probably dont need to check failed, if it did fail, contest.done is true
    contestsUA.filter { !it.done }.forEach { contestUA ->
        val sampleSizes = estResults.filter { it.task.contestUA.id == contestUA.id && !it.failed }
            .map { it.task.assertion.estSampleSize }
        contestUA.estSampleSize = if (sampleSizes.isEmpty()) 0 else sampleSizes.max()
        if (show) println("  ${contestUA}")
    }
    if (show) println()
    val maxContestSize = contestsUA.filter { !it.done }.maxOfOrNull { it.estSampleSize }
    return maxContestSize
}

// tries to start from where the last left off. Otherwise, wouldnt you just get the previous estimate?
fun makeEstimationTasks(
    auditConfig: AuditConfig,
    contestUA: ContestUnderAudit,
    cvrs: List<Cvr>,        // only needed when Comparison
    prevMvrs: List<Cvr>,  // TODO should be used for subsequent round estimation
    roundIdx: Int,
    moreParameters: Map<String, Double> = emptyMap(),
): List<ConcurrentTaskG<EstimationResult>> {
    val tasks = mutableListOf<ConcurrentTaskG<EstimationResult>>()

    contestUA.assertions().map { assert -> // pollingAssertions vs comparisonAssertions
        if (!assert.proved) {
            var prevSampleSize = 0
            var startingTestStatistic = 1.0
            if (roundIdx > 1) {
                val rr = assert.roundResults.last()
                if (rr.samplesUsed == contestUA.Nc) {
                    println("***LimitReached $contestUA")
                    contestUA.done = true
                    contestUA.status = TestH0Status.LimitReached
                }
                // start where the audit left off
                prevSampleSize = rr.samplesUsed
                startingTestStatistic = 1.0 / rr.pvalue
            }

            if (!contestUA.done) {
                tasks.add(
                    SimulateSampleSizeTask(
                        auditConfig,
                        contestUA,
                        assert,
                        cvrs,
                        startingTestStatistic,
                        prevSampleSize,
                        moreParameters
                    )
                )
            }
        }
    }
    return tasks
}

class SimulateSampleSizeTask(
        val auditConfig: AuditConfig,
        val contestUA: ContestUnderAudit,
        val assertion: Assertion,
        val cvrs: List<Cvr>,
        val startingTestStatistic: Double,
        val prevSampleSize: Int,
        val moreParameters: Map<String, Double> = emptyMap(),
    ) : ConcurrentTaskG<EstimationResult> {

    val contest = contestUA.contest

    override fun name() = "task ${contest.info.name} ${assertion.assorter.desc()} ${df(assertion.assorter.reportedMargin())}"
    override fun run(): EstimationResult {
        val result: RunTestRepeatedResult = when (auditConfig.auditType) {
            AuditType.CARD_COMPARISON ->
                simulateSampleSizeClcaAssorter(
                    auditConfig,
                    contest,
                    (assertion as ComparisonAssertion).cassorter,
                    cvrs,
                    startingTestStatistic
                )
            AuditType.POLLING ->
                simulateSampleSizePollingAssorter(
                    auditConfig,
                    contest as Contest, // TODO cant use Raire
                    assertion.assorter,
                    startingTestStatistic,
                    moreParameters=moreParameters,
                )
            AuditType.ONEAUDIT ->
                simulateSampleSizeOneAuditAssorter(
                    auditConfig,
                    contest,
                    (assertion as ComparisonAssertion).cassorter,
                    cvrs,
                    startingTestStatistic,
                    moreParameters=moreParameters,
                )
        }
        return EstimationResult(this, result, result.failPct() > 80.0) // TODO 80% ??
    }
}

/////////////////////////////////////////////////////////////////////////////////////////////////
//// Comparison

fun simulateSampleSizeClcaAssorter(
    auditConfig: AuditConfig,
    contest: ContestIF,
    cassorter: ComparisonAssorterIF,
    cvrs: List<Cvr>,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val clcaConfig = auditConfig.clcaConfig!!

    val sampler = // if (auditConfig.errorRates != null) {
    //    ComparisonSimulation(cvrs, contest, cassorter, auditConfig.errorRates)
    //} else
    if (clcaConfig.fuzzPct == null || clcaConfig.fuzzPct == 0.0) {
        val cvrPairs = cvrs.zip( cvrs)
        ComparisonWithoutReplacement(contest, cvrPairs, cassorter, allowReset=true, trackStratum=false)
    // } else if (auditConfig.useGeneratedErrorRates) {
      //  val errorRates = ComparisonErrorRates.getErrorRates(contest.ncandidates, auditConfig.fuzzPct)
      //  ComparisonSimulation(cvrs, contest, cassorter, errorRates)
    } else {
        ComparisonFuzzSampler(clcaConfig.fuzzPct, cvrs, contest as Contest, cassorter) // TODO cant use Raire here
    }

    // we need a permutation to get uniform distribution of errors, since the ComparisonSamplerSimulation puts all the errros
    // at the beginning
    sampler.reset()

    val bettingFn = when (clcaConfig.simType) {
        ClcaSimulationType.noerror -> {
            // optimistic, no errors as apriori, but adapts to actual mvrs
            AdaptiveComparison(
                Nc = contest.Nc,
                withoutReplacement = true,
                a = cassorter.noerror(),
                d1 = clcaConfig.d1,
                d2 = clcaConfig.d2,
                listOf(0.0, 0.0, 0.0, 0.0)
            )
        }
        ClcaSimulationType.oracle, ClcaSimulationType.fuzzPct -> {
            // adaptive, use known fuzz as apriori, but adapts to actual mvrs
            val errorRates = ClcaErrorRates.getErrorRates(contest.ncandidates, clcaConfig.fuzzPct)
            AdaptiveComparison(
                Nc = contest.Nc,
                withoutReplacement = true,
                a = cassorter.noerror(),
                d1 = clcaConfig.d1,
                d2 = clcaConfig.d2,
                errorRates
            )
        }
        ClcaSimulationType.apriori ->
            // adaptive, use previous round errors as apriori, but adapts to actual mvrs
            AdaptiveComparison(
                Nc = contest.Nc,
                withoutReplacement = true,
                a = cassorter.noerror(),
                d1 = clcaConfig.d1,
                d2 = clcaConfig.d2,
                clcaConfig.errorRates!!
            )
    }

    return simulateSampleSizeBetaMart(
        auditConfig,
        sampler,
        bettingFn,
        cassorter.assorter().reportedMargin(),
        cassorter.noerror(),
        cassorter.upperBound(),
        contest.Nc,
        startingTestStatistic,
        moreParameters
    )
}

fun simulateSampleSizeBetaMart(
    auditConfig: AuditConfig,
    sampleFn: Sampler,
    bettingFn: BettingFn,
    margin: Double,
    noerror: Double,
    upperBound: Double,
    Nc: Int,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {

    val testFn = BettingMart(
        bettingFn = bettingFn,
        Nc = Nc,
        noerror = noerror,
        upperBound = upperBound,
        withoutReplacement = false
    )

    val result: RunTestRepeatedResult = runTestRepeated(
        drawSample = sampleFn,
        ntrials = auditConfig.ntrials,
        testFn = testFn,
        testParameters = moreParameters,
        showDetails = false,
        startingTestStatistic = startingTestStatistic,
        margin = margin,
        Nc = Nc,
    )
    return result
}

///////////////////////////////////////////////////////////////////////////////////////////////////
//// Polling

// also called from GenSampleSizeEstimates
fun simulateSampleSizePollingAssorter(
    auditConfig: AuditConfig,
    contest: Contest,  // TODO cant use Raire
    assorter: AssorterFunction,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val margin = assorter.reportedMargin()
    val simContest = ContestSimulation(contest)
    val cvrs = simContest.makeCvrs()

    val sampler = if (auditConfig.fuzzPct == null || auditConfig.fuzzPct == 0.0) {
        PollWithoutReplacement(contest, cvrs, assorter, allowReset=true)
    } else {
        PollingFuzzSampler(auditConfig.fuzzPct, cvrs, contest, assorter) // TODO cant use Raire
    }

    return simulateSampleSizeAlphaMart(
        auditConfig,
        sampler,
        margin,
        assorter.upperBound(),
        Nc = contest.Nc,
        startingTestStatistic,
        moreParameters = moreParameters,
    )
}

fun simulateSampleSizeAlphaMart(
    auditConfig: AuditConfig,
    sampleFn: Sampler,
    margin: Double,
    upperBound: Double,
    Nc: Int,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val eta0 = margin2mean(margin)
    val minsd = 1.0e-6
    val t = 0.5
    val c = (eta0 - t) / 2 // TODO

    val estimFn = TruncShrinkage(
        N = Nc,
        withoutReplacement = true,
        upperBound = upperBound,
        d = auditConfig.pollingConfig!!.d,
        eta0 = eta0,
        minsd = minsd,
        c = c,
    )
    val testFn = AlphaMart(
        estimFn = estimFn,
        N = Nc,
        upperBound = upperBound,
        riskLimit = auditConfig.riskLimit,
        withoutReplacement = true
    )

    val result: RunTestRepeatedResult = runTestRepeated(
        drawSample = sampleFn,
        ntrials = auditConfig.ntrials,
        testFn = testFn,
        testParameters = mapOf("ntrials" to auditConfig.ntrials.toDouble(), "polling" to 1.0) + moreParameters,
        showDetails = false,
        startingTestStatistic = startingTestStatistic,
        margin = margin,
        Nc = Nc,
    )
    return result
}

///////////////////////////////////////////////////////////////////////////////////////////////////
//// OneAudit

fun simulateSampleSizeOneAuditAssorter(
    auditConfig: AuditConfig,
    contest: ContestIF,
    cassorter: ComparisonAssorterIF,
    cvrs: List<Cvr>,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val cassorterOA = cassorter as OneAuditComparisonAssorter

    val sampler = //if (auditConfig.errorRates != null) {
    //    ComparisonSimulation(cvrs, contest, cassorter, auditConfig.errorRates)
        //} else
        if (auditConfig.fuzzPct == null) {
            val cvrPairs = cvrs.zip( cvrs)
            ComparisonWithoutReplacement(contest, cvrPairs, cassorter, allowReset=true, trackStratum=false)
            // } else if (auditConfig.useGeneratedErrorRates) {
            //  val errorRates = ComparisonErrorRates.getErrorRates(contest.ncandidates, auditConfig.fuzzPct)
            //  ComparisonSimulation(cvrs, contest, cassorter, errorRates)
        } else {
            ComparisonFuzzSampler(auditConfig.fuzzPct, cvrs, contest as Contest, cassorter) // TODO cant use Raire here
        }
    sampler.reset()

    return simulateSampleSizeOneAudit(
        auditConfig,
        sampler,
        cassorterOA.clcaMargin,
        cassorter.upperBound(),
        contest.Nc,
        startingTestStatistic,
        moreParameters
    )
}

fun simulateSampleSizeOneAudit(
    auditConfig: AuditConfig,
    sampleFn: Sampler,
    margin: Double,
    upperBound: Double,
    Nc: Int,
    startingTestStatistic: Double = 1.0,
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
        d = auditConfig.pollingConfig!!.d,
        eta0 = eta0,
        minsd = minsd,
        c = c,
    )
    val testFn = AlphaMart(
        estimFn = estimFn,
        N = Nc,
        withoutReplacement = true,
        riskLimit = auditConfig.riskLimit,
        upperBound = upperBound,
    )

    val result: RunTestRepeatedResult = runTestRepeated(
        drawSample = sampleFn,
        ntrials = auditConfig.ntrials,
        testFn = testFn,
        testParameters = mapOf("ntrials" to auditConfig.ntrials.toDouble(), "polling" to 1.0) + moreParameters,
        showDetails = false,
        startingTestStatistic = startingTestStatistic,
        margin = margin,
        Nc = Nc,
    )
    return result
}

/////////////////////////////////////////////////////////////////////////////////
// SHANGRLA computeSampleSize not needed, I think

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

