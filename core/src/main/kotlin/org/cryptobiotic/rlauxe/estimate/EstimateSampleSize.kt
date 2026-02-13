package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.betting.AlphaMart
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.betting.BettingFn
import org.cryptobiotic.rlauxe.betting.BettingMart
import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.betting.ClcaSamplerErrorTracker
import org.cryptobiotic.rlauxe.betting.EstimFn
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting2
import org.cryptobiotic.rlauxe.betting.SamplerTracker
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.betting.TruncShrinkage
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.oneaudit.OneAuditVunderFuzzer
import org.cryptobiotic.rlauxe.raire.RaireContest
import org.cryptobiotic.rlauxe.raire.SimulateIrvTestData
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.makeDeciles
import kotlin.collections.List
import kotlin.collections.mutableListOf
import kotlin.math.min

private val debug = false
private val debugSampleDist = false
private val debugSampleSmall = false

private val logger = KotlinLogging.logger("EstimateSampleSizes")

////////////////////////////////////////////////////////////////////////////////////////////
//// Comparison, Polling, OneAudit.

// 1. _Estimation_: for each contest, estimate how many samples are needed for this AuditRound
fun estimateSampleSizes(
    config: AuditConfig,
    auditRound: AuditRoundIF,
    cardManifest: CloseableIterable<AuditableCard>, // Clca, OneAudit, ignored for Polling
    cardPools: List<OneAuditPoolFromCvrs>?,               // only OneAudit
    previousSamples: Set<Long>,
    showTasks: Boolean = false,
    nthreads: Int = 32,
    onlyTask: String? = null
): List<RunRepeatedResult> {

    if ((config.isClca || config.isOA ) && auditRound.roundIdx == 1 && config.simulationStrategy == SimulationStrategy.optimistic) {
        calculateSampleSizes(config, auditRound)
        return emptyList()
    }

    // choose a subset of the cards for the estimation for speed
    val cardSamples: CardSamples? = if (config.isPolling) null else
        getSubsetForEstimation(
            config,
            auditRound.contestRounds,
            cardManifest,
            previousSamples,
        )

    // simulate the card pools for all OneAudit contests; do it here one time for all contests
    // uses config.simFuzzPct to fuzz the non-pooled cvrs; the pooled cvrs are simulated using Vunder
    val vunderFuzz = if (!config.isOA) null else {
        val infos = auditRound.contestRounds.map { it.contestUA.contest.info() }.associateBy { it.id }
        OneAuditVunderFuzzer(cardPools!!, infos, config.simFuzzPct ?: 0.0, cardSamples!!.cards)
    }

    // create the estimation tasks for each contest and assertion
    val tasks = mutableListOf<EstimateSampleSizeTask>()
    auditRound.contestRounds.filter { !it.done }.forEach { contestRound ->
        tasks.addAll(makeEstimationTasks(config, contestRound, auditRound.roundIdx, cardSamples,  vunderFuzz, onlyTask=onlyTask))
    }

    // run estimation tasks concurrently
    val estResults: List<EstimationResult> = ConcurrentTaskRunnerG<EstimationResult>(showTasks).run(tasks, nthreads=nthreads)

    // put results into assertionRounds
    estResults.forEach { estResult ->
        val task = estResult.task
        val result = estResult.repeatedResult

        /* val estNewSamples = if (result.sampleCount.size == 0) 0 else result.findQuantile(config.quantile)
        if (task.assertionRound.estimationResult != null) {
            task.assertionRound.estimationResult!!.simNewMvrs = estNewSamples
        } */
        val estNewSamples = task.assertionRound.estimationResult!!.simNewMvrs
        task.assertionRound.estNewMvrs = estNewSamples
        task.assertionRound.estMvrs = min(estNewSamples + task.prevSampleSize, task.contestRound.Npop)

        if (debug) println(result.showSampleDist(estResult.task.contestRound.id))
        if (debugSampleSmall && result.avgSamplesNeeded() < 10) {
            println(" ** avgSamplesNeeded ${result.avgSamplesNeeded()} < 10; task=${task.name()}")
        }
        if (debugSampleDist) {
            println(
                "---debugSampleDist for '${task.name()}' ${auditRound.roundIdx} ntrials=${config.nsimEst} pctSamplesNeeded=" +
                        "${df(result.pctSamplesNeeded())} estSampleSize=${task.assertionRound.estMvrs}" +
                        " totalSamplesNeeded=${result.totalSamplesNeeded} nsuccess=${result.nsuccess}" +
                        "\n  sampleDist = ${result.showSampleDist(estResult.task.contestRound.id)}"
            )
        }
    }

    // put results into contestRounds
    auditRound.contestRounds.filter { !it.done }.forEach { contest ->
        val sampleSizes = estResults.filter { it.task.contestRound.id == contest.id }.map { it.task.assertionRound.estMvrs }
        contest.estMvrs = if (sampleSizes.isEmpty()) 0 else sampleSizes.max()
        val newSampleSizes = estResults.filter { it.task.contestRound.id == contest.id }.map { it.task.assertionRound.estNewMvrs }
        contest.estNewMvrs = if (newSampleSizes.isEmpty()) 0 else newSampleSizes.max()
        if (!quiet) logger.info{" ** contest ${contest.id} avgSamplesNeeded ${contest.estMvrs} task=${contest.estNewMvrs}"}
    }

    // return repeatedResults for debugging and diagnostics
    return estResults.map { it.repeatedResult }
}

// For one contest, generate a task for each non-complete assertion
// starts from where the last audit left off (prevAuditResult.pvalue)
fun makeEstimationTasks(
    config: AuditConfig,
    contestRound: ContestRound,
    roundIdx: Int,
    cardSamples: CardSamples?,
    vunderFuzz: OneAuditVunderFuzzer?,
    moreParameters: Map<String, Double> = emptyMap(),
    onlyTask: String?
): List<EstimateSampleSizeTask> {
    val stopwatch = Stopwatch()
    val tasks = mutableListOf<EstimateSampleSizeTask>()

    // simulate the polling mvrs once for all the assertions for this contest
    val mvrsForPolling = if (config.isPolling) {
            val contest = contestRound.contestUA.contest
            if (!contest.isIrv()) {
                simulateCvrsWithDilutedMargin(contestRound.contestUA, config)
            } else {
                // TODO this just makes sure the winner is chosen first (ncards * minMargin) more than any other candidate.
                val minMargin = contestRound.contestUA.minDilutedMargin()!! // not sure about this...
                val sim = SimulateIrvTestData(
                    contest as RaireContest,
                    minMargin,
                    sampleLimits = config.contestSampleCutoff
                )
                sim.makeCvrs()
            }
        } else null

    contestRound.assertionRounds.filter { !it.status.complete }.map { assertionRound ->
        val taskName = "${contestRound.contestUA.id}-${assertionRound.assertion.assorter.shortName()}"
        if (onlyTask == null || onlyTask == taskName) {
            var prevSampleSize = 0
            var startingTestStatistic = 1.0
            if (roundIdx > 1) {
                // eliminate contests which have no more samples
                val prevAuditResult = assertionRound.prevAuditResult!!
                if (prevAuditResult.samplesUsed == contestRound.Npop) {
                    logger.info { "***LimitReached $contestRound" }
                    contestRound.done = true
                    contestRound.status = TestH0Status.LimitReached
                }
                // start where the audit left off
                prevSampleSize = prevAuditResult.samplesUsed
                startingTestStatistic = 1.0 / prevAuditResult.plast
            }

            if (!contestRound.done) {
                tasks.add(
                    EstimateSampleSizeTask(
                        roundIdx,
                        config,
                        cardSamples = cardSamples,
                        mvrsForPolling = mvrsForPolling,
                        vunderFuzz,
                        contestRound,
                        assertionRound,
                        startingTestStatistic,
                        prevSampleSize,
                        moreParameters
                    )
                )
            }
        }
    }

    logger.debug{ "make ${tasks.size} tasks for contest ${contestRound.contestUA.id} took $stopwatch"}
    return tasks
}

// For one contest, for one assertion, a concurrent task
class EstimateSampleSizeTask(
    val roundIdx: Int,
    val config: AuditConfig,
    val cardSamples: CardSamples?,
    val mvrsForPolling: List<Cvr>?,
    val vunderFuzz: OneAuditVunderFuzzer?,
    val contestRound: ContestRound,
    val assertionRound: AssertionRound,
    val startingTestStatistic: Double, // T, must grow to 1/riskLimit
    val prevSampleSize: Int,
    val moreParameters: Map<String, Double> = emptyMap(),
) : ConcurrentTaskG<EstimationResult> {

    override fun name() = "${contestRound.contestUA.id}-${assertionRound.assertion.assorter.shortName()}"

    // all assertions share the same cvrs. run ntrials (=config.nsimEst times).
    // each time the trial is run, the cvrs are randomly permuted. The result is a distribution of ntrials sampleSizes.
    override fun run(): EstimationResult {
        val result: RunRepeatedResult = when (config.auditType) {
            AuditType.CLCA ->
                estimateClcaAssertionRound(
                    roundIdx,
                    config,
                    cardSamples!!,
                    contestRound,
                    assertionRound,
                    startingTestStatistic
                )
            AuditType.POLLING ->
                estimatePollingAssertionRound(
                    roundIdx,
                    config,
                    contestRound.contestUA,
                    mvrsForPolling!!,
                    assertionRound,
                    startingTestStatistic,
                    moreParameters=moreParameters,
                )
            AuditType.ONEAUDIT ->
                estimateOneAuditAssertionRound(
                    roundIdx,
                    config,
                    vunderFuzz!!,
                    cardSamples!!,
                    contestRound,
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
    val repeatedResult: RunRepeatedResult,
)

/////////////////////////////////////////////////////////////////////////////////////////////////
//// Clca, including IRV

private const val quiet = true

fun estimateClcaAssertionRound(
    roundIdx: Int,
    config: AuditConfig,
    cardSamples: CardSamples,
    contestRound: ContestRound,
    assertionRound: AssertionRound,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunRepeatedResult {
    val contestUA = contestRound.contestUA
    val contest = contestUA.contest

    val clcaConfig = config.clcaConfig
    val cassertion = assertionRound.assertion as ClcaAssertion
    val cassorter = cassertion.cassorter
    val noerror=cassorter.noerror()
    val upper=cassorter.assorter.upperBound()

    val measuredErrors = if (clcaConfig.strategy == ClcaStrategyType.generalAdaptive)
        assertionRound.accumulatedErrorCounts(contestRound) // TODO switch to previousErrorCounts ?
    else
        assertionRound.previousErrorCounts()

    val apriori = clcaConfig.apriori.makeErrorCounts(contestUA.Npop, noerror, upper)

    val bettingFn = if (clcaConfig.strategy == ClcaStrategyType.generalAdaptive) {
        GeneralAdaptiveBetting(
            contestUA.Npop,
            startingErrors = measuredErrors,
            contest.Nphantoms(),
            oaAssortRates = null,
            d = clcaConfig.d,
            maxLoss = clcaConfig.maxLoss,
        )
    } else {
        GeneralAdaptiveBetting2(
            contestUA.Npop,
            aprioriCounts = apriori,
            nphantoms = contest.Nphantoms(),
            maxLoss = clcaConfig.maxLoss,
            d = clcaConfig.d,
        )
    }

    // TODO can we use previousErrorCounts to generate fuzz ?
    // for one contest, this takes a list of cards and fuzzes them to use as the mvrs.
    val samplerTracker = if (clcaConfig.strategy == ClcaStrategyType.generalAdaptive) {
        ClcaFuzzSamplerTracker(config.simFuzzPct ?: 0.0, cardSamples, contestUA, cassorter, ClcaErrorCounts.empty(noerror, upper))
    } else {
        // start from where we left off
        ClcaFuzzSamplerTracker(config.simFuzzPct ?: 0.0, cardSamples, contestUA, cassorter, measuredErrors)
    }

    val name = "${contestUA.id}/${assertionRound.assertion.assorter.shortName()}"
    logger.debug{ "estimateClcaAssertionRound for $name with ${config.nsimEst} trials"}
    val stopwatch = Stopwatch()

    // run the simulation ntrials (=config.nsimEst) times
    val result: RunRepeatedResult = runRepeatedBettingMart(
        name,
        config,
        samplerTracker=samplerTracker,
        bettingFn,
        noerror=cassorter.noerror(),
        upper=cassorter.assorter.upperBound(),
        clcaUpper=cassorter.upperBound(),
        contestUA.Npop,
        startingTestStatistic,
        moreParameters
    )

    // The result is a distribution of ntrials sampleSizes
    // what makes this simNewMvrs? because of startingTestStatistic?
    assertionRound. estimationResult = EstimationRoundResult(
        roundIdx,
        clcaConfig.strategy.name,
        fuzzPct = config.simFuzzPct,
        startingTestStatistic = startingTestStatistic,
        startingErrorRates = measuredErrors.errorRates(), // TODO
        estimatedDistribution = makeDeciles(result.sampleCount),
        ntrials = result.sampleCount.size,
        simNewMvrs = if (result.sampleCount.size == 0) 0 else result.findQuantile(config.quantile)
    )

    logger.debug{"estimateClcaAssertionRound $roundIdx ${name} ${makeDeciles(result.sampleCount)} took=$stopwatch"}

    return result
}

fun runRepeatedBettingMart(
    name: String,
    config: AuditConfig,
    samplerTracker: SamplerTracker,
    bettingFn: BettingFn,
    noerror: Double,
    upper: Double, // cassorter.assorter.upperBound(),
    clcaUpper: Double, // clca assorter
    N: Int,
    startingTestStatistic: Double = 1.0, // T, must grow to 1/riskLimit
    moreParameters: Map<String, Double> = emptyMap(),
): RunRepeatedResult {

    val testFn = BettingMart(
        bettingFn = bettingFn,
        N = N,
        tracker = samplerTracker,
        riskLimit = config.riskLimit,
        sampleUpperBound = clcaUpper,
    )

    // run the simulation ntrials (config.nsimEst) times
    val result: RunRepeatedResult = runRepeated(
        name = name,
        ntrials = config.nsimEst,
        testFn = testFn,
        testParameters = moreParameters,
        startingTestStatistic = startingTestStatistic,
        samplerTracker = samplerTracker,
        N = N,
    )
    return result
}

///////////////////////////////////////////////////////////////////////////////////////////////////
//// OneAudit, including IRV

fun estimateOneAuditAssertionRound(
    roundIdx: Int,
    config: AuditConfig,
    vunderFuzz: OneAuditVunderFuzzer,
    cardSamples: CardSamples,
    contestRound: ContestRound,
    assertionRound: AssertionRound,
    startingTestStatistic: Double = 1.0, // T, must grow to 1/riskLimit
    moreParameters: Map<String, Double> = emptyMap(),
): RunRepeatedResult {
    val contestUA = contestRound.contestUA
    val cassertion = assertionRound.assertion as ClcaAssertion
    val oaCassorter = cassertion.cassorter as OneAuditClcaAssorter
    val oaConfig = config.oaConfig
    val clcaConfig = config.clcaConfig
    val noerror=oaCassorter.noerror()
    val upper=oaCassorter.assorter.upperBound()

    // one set of fuzzed pairs for all contests and assertions.
    val oaFuzzedPairs: List<Pair<AuditableCard, AuditableCard>> = vunderFuzz.mvrCvrPairs

    val measuredErrors = if (clcaConfig.strategy == ClcaStrategyType.generalAdaptive)
        assertionRound.accumulatedErrorCounts(contestRound) // TODO switch to previousErrorCounts ?
    else
        assertionRound.previousErrorCounts()

    val apriori = clcaConfig.apriori.makeErrorCounts(contestUA.Npop, noerror, upper)

    val bettingFn = if (clcaConfig.strategy == ClcaStrategyType.generalAdaptive) {
        GeneralAdaptiveBetting(
            contestUA.Npop,
            startingErrors = measuredErrors,
            nphantoms=contestUA.contest.Nphantoms(),
            oaAssortRates = oaCassorter.oaAssortRates,
            d = clcaConfig.d,
            maxLoss = clcaConfig.maxLoss,
        )
    } else {
        GeneralAdaptiveBetting2(
            contestUA.Npop,
            aprioriCounts = apriori,
            nphantoms=contestUA.contest.Nphantoms(),
            maxLoss = clcaConfig.maxLoss,
            d = clcaConfig.d,
        )
    }

    // uses the vunderFuzz.mvrCvrPairs as is; each trial is a new permutation
    val wantIndices = cardSamples.usedByContests[contestUA.contest.id]
    if (wantIndices == null) {
        throw RuntimeException("failed on cardSamples.usedByContests[${contestUA.contest.id}]")
    }

    val sampler = if (clcaConfig.strategy == ClcaStrategyType.generalAdaptive) {
        ClcaSamplerErrorTracker.fromIndexList(contestUA.contest.id, oaCassorter, oaFuzzedPairs, wantIndices)
    } else {
        // start from where we left off
        ClcaSamplerErrorTracker.fromIndexList(contestUA.contest.id, oaCassorter, oaFuzzedPairs, wantIndices, measuredErrors)
    }

    val name = "${contestUA.id}/${assertionRound.assertion.assorter.shortName()}"
    logger.debug{ "estimateOneAuditAssertionRound for $name with ${config.nsimEst} trials"}
    val stopwatch = Stopwatch()

    val result = runRepeatedBettingMart(
        name,
        config,
        sampler,
        bettingFn,
        oaCassorter.noerror(),
        oaCassorter.assorter.upperBound(),
        clcaUpper=oaCassorter.upperBound(),
        contestUA.Npop,
        startingTestStatistic,
        moreParameters
    )

    assertionRound.estimationResult = EstimationRoundResult(
        roundIdx,
        oaConfig.strategy.name,
        fuzzPct = config.simFuzzPct,
        startingErrorRates = measuredErrors.errorRates(), // TODO
        startingTestStatistic = startingTestStatistic,
        estimatedDistribution = makeDeciles(result.sampleCount),
        ntrials = result.sampleCount.size,
        simNewMvrs = if (result.sampleCount.size == 0) 0 else result.findQuantile(config.quantile)
    )

    logger.info{ "($stopwatch) estimateOneAuditAssertion round $roundIdx ${name} ${makeDeciles(result.sampleCount)}" }
    return result
}

/* for OneAudit IRV contests
fun estSamplesSimple(config: AuditConfig, assertionRound: AssertionRound, fac: Double, startingTestStatistic: Double): Int {
    val lastPvalue = assertionRound.auditResult?.plast ?: config.riskLimit
    val cassertion = assertionRound.assertion as ClcaAssertion

    val cassorter = cassertion.cassorter
    val est = roundUp(fac * cassorter.sampleSizeNoErrors(2 * config.clcaConfig.maxLoss, lastPvalue))

    assertionRound.estimationResult = EstimationRoundResult(
        assertionRound.roundIdx,
        "estSamplesSimple",
        fuzzPct = config.simFuzzPct, // TODO used ??
        startingErrorRates = null,
        startingTestStatistic = startingTestStatistic,
        estimatedDistribution = makeDeciles(listOf(est)),
        ntrials = est.size,
        firstSample = est,
    )
    return est
} */


///////////////////////////////////////////////////////////////////////////////////////////////////
//// Polling

fun estimatePollingAssertionRound(
    roundIdx: Int,
    config: AuditConfig,
    contestUA: ContestWithAssertions,
    mvrs: List<Cvr>,
    assertionRound: AssertionRound,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunRepeatedResult {
    val assorter = assertionRound.assertion.assorter
    val eta0 = assorter.dilutedMean()

    // optional fuzzing of the mvrs
    val useFuzz = config.simFuzzPct ?: 0.0
    val samplerTracker = PollingFuzzSamplerTracker(useFuzz, mvrs, contestUA.contest as Contest, assorter)

    val name = "${contestUA.id}/${assertionRound.assertion.assorter.shortName()}"
    logger.debug{ "estimatePollingAssertionRound for $name with ${config.nsimEst} trials"}
    val stopwatch = Stopwatch()

    val result = runRepeatedAlphaMart(
        name,
        config,
        samplerTracker,
        null,
        eta0 = eta0,
        upperBound = assorter.upperBound(),
        N = contestUA.Npop,
        startingTestStatistic = startingTestStatistic,
        moreParameters = moreParameters,
    )

    assertionRound.estimationResult = EstimationRoundResult(
        roundIdx,
        "default",
        fuzzPct = useFuzz,
        startingTestStatistic = startingTestStatistic,
        estimatedDistribution = makeDeciles(result.sampleCount),
        ntrials = result.sampleCount.size,
        simNewMvrs = if (result.sampleCount.size == 0) 0 else result.findQuantile(config.quantile)
    )

    logger.debug{"estimatePollingAssertionRound $roundIdx ${name} ${makeDeciles(result.sampleCount)} took=$stopwatch"}

    return result
}

fun runRepeatedAlphaMart(
    name: String,
    config: AuditConfig,
    samplerTracker: SamplerTracker,
    estimFn: EstimFn?, // if null use default TruncShrinkage
    eta0: Double,  // initial estimate of mean
    upperBound: Double,
    N: Int,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunRepeatedResult {

    val useEstimFn = estimFn ?: TruncShrinkage(
        N = N,
        upperBound = upperBound,
        d = config.pollingConfig.d,
        eta0 = eta0,
    )

    val testFn = AlphaMart(
        estimFn = useEstimFn,
        N = N,
        tracker = samplerTracker,
        upperBound = upperBound,
        riskLimit = config.riskLimit,
    )

    val result: RunRepeatedResult = runRepeated(
        name,
        ntrials = config.nsimEst,
        testFn = testFn,
        testParameters = mapOf("ntrials" to config.nsimEst.toDouble(), "polling" to 1.0) + moreParameters,
        startingTestStatistic = startingTestStatistic,
        samplerTracker=samplerTracker,
        N = N,
    )
    return result
}