package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.concur.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.OneAuditComparisonAssorter
import org.cryptobiotic.rlauxe.oneaudit.OneAuditContestUnderAudit
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.makeDeciles
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.math.max
import kotlin.math.min

private val debug = false
private val debugErrorRates = false
private val debugSampleDist = true
private val debugSizeNudge = true

////////////////////////////////////////////////////////////////////////////////////////////
//// Comparison, Polling, OneAudit.

fun estimateSampleSizes(
    auditConfig: AuditConfig,
    contestsUA: List<ContestUnderAudit>,
    cvrs: List<Cvr>,        // Clca only
    roundIdx: Int,
    show: Boolean = false,
    nthreads: Int = 30,
): List<RunTestRepeatedResult> {
    val tasks = mutableListOf<SimulateSampleSizeTask>()
    contestsUA.filter { !it.done }.forEach { contestUA ->
        tasks.addAll(makeEstimationTasks(auditConfig, contestUA, cvrs, roundIdx))
    }
    // run tasks concurrently
    val estResults: List<EstimationResult> = ConcurrentTaskRunnerG<EstimationResult>(show).run(tasks, nthreads)

    // cant modify contestUA until out of the concurrent tasks
    estResults.forEach { estResult ->
        val task = estResult.task
        val result = estResult.repeatedResult

        if (auditConfig.version == 1.0) {
            var estNew = result.findQuantile(auditConfig.quantile)
            if (roundIdx > 2) {
                val prevNudged = (0.25 * task.prevSampleSize).toInt()
                if (prevNudged > estNew) {
                    if (debugSizeNudge) println(" ** prevNudged $prevNudged > $estNew; round=$roundIdx task=${task.name()}")
                }
                // make sure we grow at least 25% from previous estimate (TODO might need special code for nostyle?)
                // TODO do we really need this? seems too crude
                estNew = max(prevNudged, estNew)
            }
            task.assertion.estNewSamples = estNew;
            task.assertion.estSampleSize = min(estNew + task.prevSampleSize, task.contestUA.Nc)
        }

        if (debug) println(result.showSampleDist())
        if (result.avgSamplesNeeded() < 10) {
            println(" ** avgSamplesNeeded ${result.avgSamplesNeeded()} task=${task.name()}")
        }
        if (debugSampleDist) {
            println(
                "---debugSampleDist for '${task.name()}' $roundIdx ntrials=${auditConfig.nsimEst} pctSamplesNeeded=" +
                        "${df(result.pctSamplesNeeded())} estSampleSize=${task.assertion.estSampleSize} estNew=${task.assertion.estNewSamples}" +
                        " totalSamplesNeeded=${result.totalSamplesNeeded} nsuccess=${result.nsuccess}" +
                        "\n  sampleDist = ${result.showSampleDist()}"
            )
        }
    }

    // pull out the sampleSizes for all successful assertions in the contest
    contestsUA.filter { !it.done }.forEach { contestUA ->
        val sampleSizes = estResults.filter { it.task.contestUA.id == contestUA.id }
            .map { it.task.assertion.estSampleSize }
        contestUA.estMvrs = if (sampleSizes.isEmpty()) 0 else sampleSizes.max()
        // TODO how do we know these came from the same assertion?
        val estNew = estResults.filter { it.task.contestUA.id == contestUA.id }
            .map { it.task.assertion.estNewSamples }
        contestUA.estNewMvrs = if (estNew.isEmpty()) 0 else estNew.max()

        val results = contestUA.minAssertion()?.roundResults
        val pvalue = if (results.isNullOrEmpty()) 1.0 else results.last().pvalue
        if (show) println("  ${contestUA} pvalue=$pvalue")
    }
    if (show) println()
    return estResults.map { it.repeatedResult }
}

// tries to start from where the last left off. Otherwise, wouldnt you just get the previous estimate?
fun makeEstimationTasks(
    auditConfig: AuditConfig,
    contestUA: ContestUnderAudit,
    cvrs: List<Cvr>,        // only needed when Comparison
    roundIdx: Int,
    moreParameters: Map<String, Double> = emptyMap(),
): List<SimulateSampleSizeTask> {
    val tasks = mutableListOf<SimulateSampleSizeTask>()

    contestUA.assertions().map { assert -> // pollingAssertions vs comparisonAssertions
        if (!assert.status.complete) {
            var prevSampleSize = 0
            var startingTestStatistic = 1.0
            if (roundIdx > 1) {
                val rr = assert.roundResults.last()
                if (rr.samplesUsed == contestUA.Nc) {   // TODO or pct of ?
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
                        roundIdx,
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
    val roundIdx: Int,
    val auditConfig: AuditConfig,
    val contestUA: ContestUnderAudit,
    val assertion: Assertion,
    val cvrs: List<Cvr>,
    val startingTestStatistic: Double,
    val prevSampleSize: Int,
    val moreParameters: Map<String, Double> = emptyMap(),
) : ConcurrentTaskG<EstimationResult> {

    override fun name() = "task ${contestUA.name} ${assertion.assorter.desc()} ${auditConfig.strategy()}}"
    override fun run(): EstimationResult {
        val result: RunTestRepeatedResult = when (auditConfig.auditType) {
            AuditType.CLCA ->
                simulateSampleSizeClcaAssorter(
                    roundIdx,
                    auditConfig,
                    contestUA.contest,
                    (assertion as ClcaAssertion),
                    cvrs,
                    startingTestStatistic
                )
            AuditType.POLLING ->
                simulateSampleSizePollingAssorter(
                    roundIdx,
                    auditConfig,
                    contestUA.contest as Contest, // TODO cant use Raire
                    assertion,
                    startingTestStatistic,
                    moreParameters=moreParameters,
                )
            AuditType.ONEAUDIT ->
                simulateSampleSizeOneAuditAssorter(
                    roundIdx,
                    auditConfig,
                    contestUA as OneAuditContestUnderAudit,
                    (assertion as ClcaAssertion),
                    cvrs,
                    startingTestStatistic,
                    moreParameters=moreParameters,
                )
        }
        return EstimationResult(this, result)
    }
}

/////////////////////////////////////////////////////////////////////////////////////////////////
//// Clca, including Raire

fun simulateSampleSizeClcaAssorter(
    roundIdx: Int,
    auditConfig: AuditConfig,
    contest: ContestIF,
    cassertion: ClcaAssertion,
    cvrs: List<Cvr>,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val clcaConfig = auditConfig.clcaConfig
    val cassorter = cassertion.cassorter
    var fuzzPct = 0.0

    val errorRates = when {
        (clcaConfig.strategy == ClcaStrategyType.previous) -> {
            var errorRates = ClcaErrorRates(0.0, contest.phantomRate(), 0.0, 0.0)
            if (cassertion.roundResults.size > 0) {
                errorRates = cassertion.roundResults.last().measuredRates!!
            }
            if (debugErrorRates) println("previous simulate round $roundIdx using errorRates=$errorRates")
            errorRates
        }
        (clcaConfig.strategy == ClcaStrategyType.phantoms) -> {
            val errorRates = ClcaErrorRates(0.0, contest.phantomRate(), 0.0, 0.0)
            if (debugErrorRates) println("phantoms simulate round $roundIdx using errorRates=$errorRates")
            errorRates
        }
        (clcaConfig.simFuzzPct != null && clcaConfig.simFuzzPct != 0.0) -> {
            fuzzPct = clcaConfig.simFuzzPct
            if (debugErrorRates) println("simFuzzPct simulate round $roundIdx using simFuzzPct=${clcaConfig.simFuzzPct} errorRate=${ClcaErrorTable.getErrorRates(contest.ncandidates, clcaConfig.simFuzzPct)}")
            ClcaErrorTable.getErrorRates(contest.ncandidates, clcaConfig.simFuzzPct)
        }
        (clcaConfig.errorRates != null) -> {
            if (debugErrorRates) println("simulate apriori round $roundIdx using clcaConfig.errorRates =${clcaConfig.errorRates}")
            clcaConfig.errorRates
        } // hmmmm
        else -> {
            if (debugErrorRates) println("simulate round $roundIdx using no errorRates")
            null
        }
    }

    val (sampler: Sampler, bettingFn: BettingFn) = if (errorRates != null && !errorRates.areZero()) {
        val irvFuzz = (contest.choiceFunction == SocialChoiceFunction.IRV && clcaConfig.simFuzzPct != null)
        if (irvFuzz) fuzzPct = clcaConfig.simFuzzPct!! // TODO
        Pair(
            if (irvFuzz) ClcaFuzzSampler(clcaConfig.simFuzzPct!!, cvrs, contest, cassorter)
            else ClcaSimulation(cvrs, contest, cassorter, errorRates),
            AdaptiveComparison(
                Nc = contest.Nc,
                a = cassorter.noerror(),
                d = clcaConfig.d,
                errorRates = errorRates,
            )
        )
    } else {
        // this is noerrors
        Pair(
            ClcaWithoutReplacement(
                contest,
                cvrs.zip(cvrs),
                cassorter,
                allowReset = true,
                trackStratum = false
            ),
            AdaptiveComparison(
                Nc = contest.Nc,
                a = cassorter.noerror(),
                d = clcaConfig.d,
                errorRates = ClcaErrorRates(0.0, 0.0, 0.0, 0.0)
            )
        )
    }

    // we need a permutation to get uniform distribution of errors, since some simulations puts all the errros at the beginning
    sampler.reset()

    val result: RunTestRepeatedResult =  simulateSampleSizeBetaMart(
        roundIdx,
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

    val estRound = EstimationRoundResult(roundIdx,
        clcaConfig.strategy.name,
        fuzzPct = fuzzPct,
        startingTestStatistic = startingTestStatistic,
        startingRates = errorRates,
        estimatedDistribution = makeDeciles(result.sampleCount),
    )
    cassertion.estRoundResults.add(estRound)

    return result
}

fun simulateSampleSizeBetaMart(
    roundIdx: Int,
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
    )

    val result: RunTestRepeatedResult = runTestRepeated(
        drawSample = sampleFn,
        ntrials = auditConfig.nsimEst,
        testFn = testFn,
        testParameters = moreParameters,
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
    roundIdx: Int,
    auditConfig: AuditConfig,
    contest: Contest,  // TODO cant use Raire
    assertion: Assertion,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val assorter = assertion.assorter
    val margin = assorter.reportedMargin()
    // TODO 2 candidate plurality Contest with given margin
    val simContest = ContestSimulation(contest)
    val cvrs = simContest.makeCvrs() // fake Cvrs with reported margin, what about suprmajority?
    var fuzzPct = 0.0

    val pollingConfig = auditConfig.pollingConfig
    val sampler = if (pollingConfig.simFuzzPct == null || pollingConfig.simFuzzPct == 0.0) {
        PollWithoutReplacement(contest, cvrs, assorter, allowReset=true)
    } else {
        fuzzPct = pollingConfig.simFuzzPct
        PollingFuzzSampler(pollingConfig.simFuzzPct, cvrs, contest, assorter) // TODO cant use Raire
    }

    val result = simulateSampleSizeAlphaMart(
        auditConfig,
        sampler,
        margin,
        assorter.upperBound(),
        Nc = contest.Nc,
        startingTestStatistic,
        moreParameters = moreParameters,
    )

    val estRound = EstimationRoundResult(roundIdx,
        "default",
        fuzzPct = fuzzPct,
        startingTestStatistic = startingTestStatistic,
        estimatedDistribution = makeDeciles(result.sampleCount),
    )
    assertion.estRoundResults.add(estRound)

    return result
}

// polling and oneAudit
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
    val c = (eta0 - 0.5) / 2 // TODO

    val estimFn = TruncShrinkage(
        N = Nc,
        upperBound = upperBound,
        d = auditConfig.pollingConfig.d,
        eta0 = eta0,
        c = c,
    )
    val testFn = AlphaMart(
        estimFn = estimFn,
        N = Nc,
        upperBound = upperBound,
        riskLimit = auditConfig.riskLimit,
    )

    val result: RunTestRepeatedResult = runTestRepeated(
        drawSample = sampleFn,
        ntrials = auditConfig.nsimEst,
        testFn = testFn,
        testParameters = mapOf("ntrials" to auditConfig.nsimEst.toDouble(), "polling" to 1.0) + moreParameters,
        startingTestStatistic = startingTestStatistic,
        margin = margin,
        Nc = Nc,
    )
    return result
}

///////////////////////////////////////////////////////////////////////////////////////////////////
//// OneAudit

fun simulateSampleSizeOneAuditAssorter(
    roundIdx: Int,
    auditConfig: AuditConfig,
    contestUA: OneAuditContestUnderAudit,
    cassertion: ClcaAssertion,
    cvrs: List<Cvr>,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val cassorter = cassertion.cassorter as OneAuditComparisonAssorter
    val oaConfig = auditConfig.oaConfig
    var fuzzPct = 0.0

    // TODO is this right, no special processing for the "hasCvr" strata?
    val sampler = if (oaConfig.simFuzzPct == null) {
        ClcaWithoutReplacement(contestUA.contest, cvrs.zip( cvrs), cassorter, allowReset=true, trackStratum=false)
    } else {
        fuzzPct = oaConfig.simFuzzPct
        OneAuditFuzzSampler(oaConfig.simFuzzPct, cvrs, contestUA, cassorter) // TODO cant use Raire
    }

    sampler.reset()

    val result = simulateSampleSizeAlphaMart(
        auditConfig,
        sampler,
        cassorter.clcaMargin,
        cassorter.upperBound(),
        contestUA.Nc,
        startingTestStatistic,
        moreParameters
    )

    val estRound = EstimationRoundResult(roundIdx,
        oaConfig.strategy.name,
        fuzzPct = fuzzPct,
        startingTestStatistic = startingTestStatistic,
        estimatedDistribution = makeDeciles(result.sampleCount),
    )
    cassertion.estRoundResults.add(estRound)

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

