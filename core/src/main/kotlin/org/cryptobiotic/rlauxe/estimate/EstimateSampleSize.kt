package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.BettingFn
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.makeDeciles
import kotlin.collections.mutableListOf
import kotlin.math.min

private val debug = false
private val debugErrorRates = false
private val debugSampleDist = false
private val debugSampleSmall = false

private val logger = KotlinLogging.logger("EstimateSampleSizes")

////////////////////////////////////////////////////////////////////////////////////////////
//// Comparison, Polling, OneAudit.

// for CLCA and OA, take the first L=config.contestSampleCutoff values in the cardManifest (i.e. the actual cards)
// For polling, use ContestSimulation.simulateCvrsDilutedMargin(contest as Contest, config).makeCvrs()
//    using Nb and diluted margin.

// 1. _Estimation_: for each contest, estimate how many samples are needed for this AuditRound
fun estimateSampleSizes(
    config: AuditConfig,
    auditRound: AuditRound,
    cardManifest: CloseableIterable<AuditableCard>, // Clca, OneAudit, ifnored for Polling
    showTasks: Boolean = false,
    nthreads: Int = 32,
): List<RunTestRepeatedResult> {

    // create the estimation tasks
    val tasks = mutableListOf<EstimateSampleSizeTask>()
    auditRound.contestRounds.filter { !it.done }.forEach { contestRound ->
        tasks.addAll(makeEstimationTasks(config, contestRound, auditRound.roundIdx, cardManifest))
    }

    // run tasks concurrently
    logger.info{ "ConcurrentTaskRunnerG run ${tasks.size} tasks"}
    val estResults: List<EstimationResult> = ConcurrentTaskRunnerG<EstimationResult>(showTasks).run(tasks, nthreads=nthreads)

    // put results into assertionRounds
    estResults.forEach { estResult ->
        val task = estResult.task
        val result = estResult.repeatedResult

        val useFirst = config.auditType == AuditType.ONEAUDIT && config.oaConfig.useFirst // experimental
        val estNewSamples = if (useFirst) result.sampleCount[0] else result.findQuantile(config.quantile)
        task.assertionRound.estNewSampleSize = estNewSamples
        task.assertionRound.estSampleSize = min(estNewSamples + task.prevSampleSize, task.contest.Nc)

        if (debug) println(result.showSampleDist(estResult.task.contest.id))
        if (debugSampleSmall && result.avgSamplesNeeded() < 10) {
            println(" ** avgSamplesNeeded ${result.avgSamplesNeeded()} < 10; task=${task.name()}")
        }
        if (debugSampleDist) {
            println(
                "---debugSampleDist for '${task.name()}' ${auditRound.roundIdx} ntrials=${config.nsimEst} pctSamplesNeeded=" +
                        "${df(result.pctSamplesNeeded())} estSampleSize=${task.assertionRound.estSampleSize}" +
                        " totalSamplesNeeded=${result.totalSamplesNeeded} nsuccess=${result.nsuccess}" +
                        "\n  sampleDist = ${result.showSampleDist(estResult.task.contest.id)}"
            )
        }
    }

    // put results into contestRounds
    auditRound.contestRounds.filter { !it.done }.forEach { contest ->
        val sampleSizes = estResults.filter { it.task.contest.id == contest.id }
            .map { it.task.assertionRound.estSampleSize }
        contest.estSampleSize = if (sampleSizes.isEmpty()) 0 else sampleSizes.max()
        val newSampleSizes = estResults.filter { it.task.contest.id == contest.id }
            .map { it.task.assertionRound.estNewSampleSize }
        contest.estNewSamples = if (newSampleSizes.isEmpty()) 0 else newSampleSizes.max()
        if (!quiet) logger.info{" ** contest ${contest.id} avgSamplesNeeded ${contest.estSampleSize} task=${contest.estNewSamples}"}
    }

    // return repeatedResult for debugging and diagnostics
    return estResults.map { it.repeatedResult }
}

// For one contest, generate a task for each non-complete assertion
// starts from where the last audit left off (prevAuditResult.pvalue)
fun makeEstimationTasks(
    config: AuditConfig,
    contestRound: ContestRound,
    roundIdx: Int,
    cardManifest: CloseableIterable<AuditableCard>,
    moreParameters: Map<String, Double> = emptyMap(),
): List<EstimateSampleSizeTask> {
    val tasks = mutableListOf<EstimateSampleSizeTask>()

    // TODO could do them for all contests in one pass; could be in one list
    val contestCards = ContestCardsLimited(contestRound.contestUA.id, config.contestSampleCutoff, cardManifest.iterator()).cards()

    contestRound.assertionRounds.map { assertionRound ->
        if (!assertionRound.status.complete) {
            var prevSampleSize = 0
            var startingTestStatistic = 1.0
            if (roundIdx > 1) {
                val prevAuditResult = assertionRound.prevAuditResult!!
                if (prevAuditResult.samplesUsed == contestRound.Nc) {
                    logger.info{"***LimitReached $contestRound"}
                    contestRound.done = true
                    contestRound.status = TestH0Status.LimitReached
                }
                // start where the audit left off
                prevSampleSize = prevAuditResult.samplesUsed
                startingTestStatistic = 1.0 / prevAuditResult.pvalue
            }

            if (!contestRound.done) {
                tasks.add(
                    EstimateSampleSizeTask(
                        roundIdx,
                        config,
                        contestRound,
                        contestCards = contestCards,
                        assertionRound,
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

// For one contest, for one assertion, a concurrent task
class EstimateSampleSizeTask(
    val roundIdx: Int,
    val config: AuditConfig,
    val contest: ContestRound,
    val contestCards: List<AuditableCard>,
    val assertionRound: AssertionRound,
    val startingTestStatistic: Double,
    val prevSampleSize: Int,
    val moreParameters: Map<String, Double> = emptyMap(),
) : ConcurrentTaskG<EstimationResult> {

    override fun name() = "task ${contest.name} ${assertionRound.assertion.assorter.desc()} ${config.strategy()}}"

    // all assertions share the same cvrs. run ntrials (=config.nsimEst times).
    // each time the trial is run, the cvrs are randomly permuted. The result is a distribution of ntrials sampleSizes.
    override fun run(): EstimationResult {
        val result: RunTestRepeatedResult = when (config.auditType) {
            AuditType.CLCA ->
                estimateClcaAssertionRound(
                    roundIdx,
                    config,
                    contest.contestUA,
                    contestCards,
                    assertionRound,
                    startingTestStatistic
                )
            AuditType.POLLING ->
                estimatePollingAssertionRound(
                    roundIdx,
                    config,
                    contest.contestUA,
                    contestCards,
                    assertionRound,
                    startingTestStatistic,
                    moreParameters=moreParameters,
                )
            AuditType.ONEAUDIT ->
                estimateOneAuditAssertionRound(
                    roundIdx,
                    config,
                    contest.contestUA,
                    contestCards,
                    assertionRound,
                    startingTestStatistic,
                    moreParameters=moreParameters,
                )
        }
        return EstimationResult(this, result)
    }
}

data class EstimationResult(
    val task: EstimateSampleSizeTask,
    val repeatedResult: RunTestRepeatedResult,
)

/////////////////////////////////////////////////////////////////////////////////////////////////
//// Clca, including with IRV

private const val quiet = true

fun estimateClcaAssertionRound(
    roundIdx: Int,
    config: AuditConfig,
    contestUA: ContestUnderAudit,
    contestCards: List<AuditableCard>,
    assertionRound: AssertionRound,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val clcaConfig = config.clcaConfig
    val cassertion = assertionRound.assertion as ClcaAssertion
    val cassorter = cassertion.cassorter
    val contest = contestUA.contest

    var errorRates: PluralityErrorRates = when {
        // Subsequent rounds, always use measured rates.
        (assertionRound.prevAuditResult != null) -> {
            // TODO should be average of previous rates?
            PluralityErrorRates.fromCounts(assertionRound.prevAuditResult!!.measuredCounts, cassorter.noerror(), assertionRound.prevAuditResult!!.samplesUsed)
        }
        (clcaConfig.strategy == ClcaStrategyType.fuzzPct)  -> {
            ClcaErrorTable.getErrorRates(contest.ncandidates, config.simFuzzPct)
        }
        (clcaConfig.strategy == ClcaStrategyType.apriori) -> {
            clcaConfig.errorRates!!
        }
        else -> {
            PluralityErrorRates.Zero
            // ClcaErrorTable.getErrorRates(contest.ncandidates, config.clcaConfig.fuzzPct)  // TODO do better
        }
    }

    //  estimation: use real cards, simulate cards with ClcaSimulatedErrorRates; the cards already have phantoms
    val sampler = ClcaCardSimulatedErrorRates(contestCards, contest, cassorter, errorRates) // TODO why cant we use this with IRV?? I think we can

    // Using errorRates in the bettingFn, make sure phantom rate is accounted for
    if (errorRates.p1o < contest.phantomRate())
        errorRates = errorRates.copy( p1o = contest.phantomRate())

    /* val bettingFn: BettingFn = if (clcaConfig.strategy == ClcaStrategyType.oracle) {
        OracleComparison(a = cassorter.noerror(), errorRates = errorRates)
    }  else if (clcaConfig.strategy == ClcaStrategyType.optimalComparison) {
        OptimalComparisonNoP1(N = contestUA.Nb, withoutReplacement = true, upperBound = cassorter.upperBound, p2 = errorRates.p2o)
    } else { */
        //AdaptiveBetting(N = contestUA.Nb, a = cassorter.noerror(), d = clcaConfig.d, errorRates = errorRates)
    // val bettingFn = AdaptiveBetting(N = contestUA.Nb, a = cassorter.noerror(), d = clcaConfig.d, errorRates = errorRates) // diluted N

    val errorCounts = ClcaErrorCounts.fromPluralityErrorRates(errorRates, totalSamples = contestCards.size, noerror = cassorter.noerror(), upper = cassorter.assorter.upperBound())
    val bettingFn = GeneralAdaptiveBetting(N = contestUA.Nb, errorCounts, d = clcaConfig.d, )

    // TODO track down simulations and do initial permutation there; we want first trial to use the actual permutation
    // we need a permutation to get uniform distribution of errors, since some simulations put all the errors at the beginning
    // sampler.reset()

    // run the simulation ntrials (=config.nsimEst) times
    val result: RunTestRepeatedResult = runRepeatedBettingMart(
        config,
        sampler,
        bettingFn,
        // cassorter.assorter().reportedMargin(),
        cassorter.noerror(),
        cassorter.upperBound(),
        contestUA.Nb,
        startingTestStatistic,
        moreParameters
    )

    // The result is a distribution of ntrials sampleSizes
    assertionRound.estimationResult = EstimationRoundResult(roundIdx,
        clcaConfig.strategy.name,
        fuzzPct = config.simFuzzPct,
        startingTestStatistic = startingTestStatistic,
        startingRates = errorRates.errorRates(cassorter.noerror()),
        estimatedDistribution = makeDeciles(result.sampleCount),
        firstSample = if (result.sampleCount.isEmpty()) 0 else result.sampleCount[0],
    )

    return result
}

fun runRepeatedBettingMart(
    config: AuditConfig,
    sampleFn: Sampler,
    bettingFn: BettingFn,
    noerror: Double,
    upperBound: Double,
    N: Int,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {

    val testFn = BettingMart(
        bettingFn = bettingFn,
        N = N,
        tracker = ClcaErrorTracker(noerror),
        riskLimit = config.riskLimit,
        sampleUpperBound = upperBound,
    )

    // run the simulation ntrials (config.nsimEst) times
    val result: RunTestRepeatedResult = runTestRepeated(
        drawSample = sampleFn,
        ntrials = config.nsimEst,
        testFn = testFn,
        testParameters = moreParameters,
        startingTestStatistic = startingTestStatistic,
        N = N,
    )
    return result
}

///////////////////////////////////////////////////////////////////////////////////////////////////
//// Polling

fun estimatePollingAssertionRound(
    roundIdx: Int,
    config: AuditConfig,
    contestUA: ContestUnderAudit,
    contestCards: List<AuditableCard>,
    assertionRound: AssertionRound,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val assorter = assertionRound.assertion.assorter
    val eta0 = assorter.reportedMean()

    // optional fuzzing of the cvrs
    val useFuzz = config.simFuzzPct ?: 0.0
    val sampler = PollingCardFuzzSampler(useFuzz, contestCards, contestUA.contest as Contest, assorter) // cant use Raire

    // was
    // makeFuzzedCardsFrom(contestsUA: List<ContestUnderAudit>, cards: List<AuditableCard>, fuzzPct: Double)
    // val cvrs = ContestSimulation.simulateCvrsDilutedMargin(contestRound.contestUA, config)

    val result = runRepeatedAlphaMart(
        config,
        sampler,
        null,
        eta0 = eta0,
        upperBound = assorter.upperBound(),
        N = contestUA.Nb,
        startingTestStatistic = startingTestStatistic,
        moreParameters = moreParameters,
    )

    assertionRound.estimationResult = EstimationRoundResult(
        roundIdx,
        "default",
        fuzzPct = useFuzz,
        startingTestStatistic = startingTestStatistic,
        estimatedDistribution = makeDeciles(result.sampleCount),
        firstSample = if (result.sampleCount.isEmpty()) 0 else result.sampleCount[0],
    )

    return result
}

fun runRepeatedAlphaMart(
    config: AuditConfig,
    sampleFn: Sampler,
    estimFn: EstimFn?, // if null use default TruncShrinkage
    eta0: Double,  // initial estimate of mean
    upperBound: Double,
    N: Int,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {

    val useEstimFn = estimFn ?: TruncShrinkage(
        N = N,
        upperBound = upperBound,
        d = config.pollingConfig.d,
        eta0 = eta0,
    )

    val testFn = AlphaMart(
        estimFn = useEstimFn,
        N = N,
        upperBound = upperBound,
        riskLimit = config.riskLimit,
    )

    val result: RunTestRepeatedResult = runTestRepeated(
        drawSample = sampleFn,
        ntrials = config.nsimEst,
        testFn = testFn,
        testParameters = mapOf("ntrials" to config.nsimEst.toDouble(), "polling" to 1.0) + moreParameters,
        startingTestStatistic = startingTestStatistic,
        N = N,
    )
    return result
}

///////////////////////////////////////////////////////////////////////////////////////////////////
//// OneAudit

fun estimateOneAuditAssertionRound(
    roundIdx: Int,
    config: AuditConfig,
    contestUA: ContestUnderAudit,
    contestCards: List<AuditableCard>,
    assertionRound: AssertionRound,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val cassertion = assertionRound.assertion as ClcaAssertion
    val oaCassorter = cassertion.cassorter as OneAuditClcaAssorter
    val oaConfig = config.oaConfig
    val clcaConfig = config.clcaConfig

    //// same as estimateClcaAssertionRound
    val errorRates: PluralityErrorRates = when {
        // Subsequent rounds, always use measured rates.
        (assertionRound.prevAuditResult != null) -> {
            // TODO should be average of previous rates?
            PluralityErrorRates.fromCounts(assertionRound.prevAuditResult!!.measuredCounts, oaCassorter.noerror(), assertionRound.prevAuditResult!!.samplesUsed)
        }
        (clcaConfig.strategy == ClcaStrategyType.fuzzPct)  -> {
            ClcaErrorTable.getErrorRates(contestUA.ncandidates, clcaConfig.fuzzPct) // TODO do better
        }
        (clcaConfig.strategy == ClcaStrategyType.apriori) -> {
            clcaConfig.errorRates!!
        }
        else -> {
            PluralityErrorRates.Zero
        }
    }

    //  estimation: use real cards, simulate cards with ClcaSimulatedErrorRates; the cards already have phantoms
    val sampler = ClcaCardSimulatedErrorRates(contestCards, contestUA.contest, oaCassorter, errorRates) // TODO why cant we use this with IRV?? I think we can

    // the minimum p2o is always the phantom rate.
    // if (errorRates.p2o < contestUA.contest.phantomRate())
    //    errorRates = errorRates.copy( p2o = contestUA.contest.phantomRate())

    // TODO track down simulations and do initial permutation there; we want first trial to use the actual permutation

    val strategy = config.oaConfig.strategy
    val result = if (strategy == OneAuditStrategyType.optimalComparison) {

        val bettingFn: BettingFn = OptimalComparisonNoP1(contestUA.Nb, true, oaCassorter.upperBound, p2 = errorRates.p2o) // diluted margin

        runRepeatedBettingMart(
            config,
            sampler,
            bettingFn,
            oaCassorter.noerror(),
            oaCassorter.upperBound(),
            contestUA.Nb,
            startingTestStatistic,
            moreParameters
        )

    } else {
        val eta0 = if (strategy == OneAuditStrategyType.eta0Eps)
            oaCassorter.upperBound() * (1.0 - eps)
        else
            oaCassorter.noerror()

        val estimFn = if (config.oaConfig.strategy == OneAuditStrategyType.bet99) {
            FixedEstimFn(.99 * oaCassorter.upperBound())
        } else {
            TruncShrinkage(
                N = contestUA.Nc,
                withoutReplacement = true,
                upperBound = oaCassorter.upperBound(),
                d = config.pollingConfig.d,
                eta0 = eta0,
            )
        }

        runRepeatedAlphaMart(
            config,
            sampler,
            estimFn = estimFn,
            eta0 = eta0,
            upperBound = oaCassorter.upperBound(),
            N = contestUA.Nb,
            startingTestStatistic = startingTestStatistic,
            moreParameters
        )
    }

    assertionRound.estimationResult = EstimationRoundResult(
        roundIdx,
        oaConfig.strategy.name,
        fuzzPct = config.simFuzzPct, // TODO used ??
        startingTestStatistic = startingTestStatistic,
        estimatedDistribution = makeDeciles(result.sampleCount),
        firstSample = if (result.sampleCount.size > 0) result.sampleCount[0] else 0,
    )

    logger.info{"simulateSampleSizeOneAuditAssorter $roundIdx ${contestUA.id} ${oaCassorter.assorter().desc()} ${makeDeciles(result.sampleCount)} " +
                "firstSample=${assertionRound.estimationResult!!.firstSample}"}
    return result
}

// take the first contestSampleCutoff cards that contain the contest
// TODO this assumes the cards are randomized, so we can just take the first L cvrs; but some of the tests may not do that...track them down
class ContestCardsLimited(
    val contestId: Int,
    val contestSampleCutoff: Int?,
    cardIter: Iterator<AuditableCard>,
) {
    private val cards = mutableListOf<AuditableCard>()

    init {
        while ((contestSampleCutoff == null || cards.size < contestSampleCutoff) && cardIter.hasNext()) {
            val card = cardIter.next()
            if (card.hasContest(contestId)) cards.add(card)
        }
    }

    fun cards() = cards.toList()
}