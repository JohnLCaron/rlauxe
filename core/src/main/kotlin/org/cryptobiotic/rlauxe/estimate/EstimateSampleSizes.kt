package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPool
import org.cryptobiotic.rlauxe.oneaudit.OneAuditVunderFuzzer
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.makeDeciles
import org.cryptobiotic.rlauxe.workflow.CardManifest

// TODO obsolete

private val logger = KotlinLogging.logger("EstimateSampleSizes")

////////////////////////////////////////////////////////////////////////////////////////////
//// Comparison, Polling, OneAudit.

// 1. _Estimation_: for each contest, estimate how many samples are needed for this AuditRound
fun estimateSampleSizes(
    config: AuditConfig,
    auditRound: AuditRoundIF,
    sortedManifest: CardManifest,
    cardPools: List<OneAuditPool>?,               // only OneAudit
    populations: List<PopulationIF>?,               // only OneAudit
    previousSamples: Set<Long>,
    showTasks: Boolean = false,
    nthreads: Int = 32,
    onlyTask: OnlyTask? = null,
): List<RunRepeatedResult> {

    if (config.simulationStrategy == SimulationStrategy.optimistic) {
        val optimistic = EstimateAudit(config,  auditRound.roundIdx, auditRound.contestRounds, cardPools, populations, sortedManifest)
        return optimistic.run()
    }

    // choose a subset of the cards for the estimation for speed
    val cardSamples: CardSamples? = if (config.isPolling) null else
        getSubsetForEstimation(
            config,
            auditRound.contestRounds,
            sortedManifest,
            previousSamples,
        )

    // simulate the card pools for all OneAudit contests; do it here one time for all contests
    // uses config.simFuzzPct to fuzz the non-pooled cvrs; the pooled cvrs are simulated using Vunder
    val vunderFuzz = if (!config.isOA) null else {
        val infos = auditRound.contestRounds.map { it.contestUA.contest.info() }.associateBy { it.id }
        // TODO need cardPools always?
        OneAuditVunderFuzzer(cardPools!!, infos, config.simFuzzPct ?: 0.0, cardSamples!!.cards)
    }

    // create the estimation tasks for each contest and assertion
    val tasks = mutableListOf<EstimateSampleSizeTask>()
    auditRound.contestRounds.filter { !it.done }.filter{ onlyTask == null || it.id == onlyTask.contestId }.forEach { contestRound ->
        tasks.addAll(makeEstimationTasks(config, contestRound, auditRound.roundIdx, cardSamples,  vunderFuzz, onlyTask=onlyTask))
    }

    // run estimation tasks concurrently
    val estResults: List<EstimationResult> = ConcurrentTaskRunnerG<EstimationResult>(showTasks).run(tasks, nthreads=nthreads)

    // put results into assertionRounds
    estResults.forEach { estResult ->
        val task = estResult.task
        val estNewSamples = task.assertionRound.estimationResult!!.simNewMvrsNeeded
        task.assertionRound.estNewMvrs = estNewSamples
        task.assertionRound.estMvrs = estNewSamples + task.prevSampleSize
    }

    // put results into contestRounds
    auditRound.contestRounds.filter { !it.done }.forEach { contestRound ->
        val sampleSizes = estResults.filter { it.task.contestRound.id == contestRound.id }.map { it.task.assertionRound.estMvrs }
        contestRound.estMvrs = sampleSizes.max() // TODO cant be empty?
        val newSampleSizes = estResults.filter { it.task.contestRound.id == contestRound.id }.map { it.task.assertionRound.estNewMvrs }
        contestRound.estNewMvrs = newSampleSizes.max() // TODO cant be empty?
        if (contestRound.estNewMvrs <= 0 || contestRound.estMvrs > contestRound.contestUA.Npop) { // estimation failed
            contestRound.done = true
            contestRound.status = TestH0Status.FailMaxSamplesAllowed
            logger.warn{" *** remove contest ${contestRound.id} with status FailMaxSamplesAllowed: estimation failed"}
        }
        if (!quiet) logger.info{" ** contest ${contestRound.id} avgSamplesNeeded ${contestRound.estMvrs} task=${contestRound.estNewMvrs}"}
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
    onlyTask: OnlyTask?,
): List<EstimateSampleSizeTask> {
    val stopwatch = Stopwatch()
    val tasks = mutableListOf<EstimateSampleSizeTask>()
    var estStrategy = "not set"

    // simulate the polling mvrs once for all the assertions for this contest
    val mvrsForPolling = if (config.isPolling) {
            estStrategy = "simulateCvrsWithDilutedMargin"
            simulateCvrsForContest(contestRound.contestUA, config)
        } else null

    contestRound.assertionRounds.filter { !it.status.complete }.map { assertionRound ->
        val taskName = "${contestRound.contestUA.id}-${assertionRound.assertion.assorter.shortName()}"
        if (onlyTask == null || onlyTask.taskName == taskName) {
            var prevSampleSize = 0
            var startingTestStatistic = 1.0
            if (roundIdx > 1) {
                // eliminate contests whose prevAuditResult hit the limit TODO HEY LOOK
                val prevAuditResult = assertionRound.prevAuditResult!!
                if (prevAuditResult.samplesUsed == contestRound.Npop) {
                    logger.warn{" *** remove contest ${contestRound.id} with status LimitReached: no more samples"}
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
                        estStrategy,
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
    val estStrategy: String,
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
                    startingTestStatistic,
                )
            AuditType.POLLING ->
                estimatePollingAssertionRound(
                    roundIdx,
                    config,
                    contestRound.contestUA,
                    mvrsForPolling!!,
                    assertionRound,
                    startingTestStatistic,
                    estStrategy,
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


    val apriori = clcaConfig.apriori.makeErrorRates(noerror, upper)

    val bettingFn =
        GeneralAdaptiveBetting(
            contestUA.Npop,
            aprioriErrorRates = apriori,
            nphantoms = contest.Nphantoms(),
            maxLoss = clcaConfig.maxLoss,
            oaAssortRates = null,
            d = clcaConfig.d,
        )

    // start from where we left off
    val previousErrorTracker = assertionRound.previousErrorTracker()
    // for one contest, this takes a list of cards and optionally fuzzes them to use as the mvrs.
    val samplerTracker =
        ClcaFuzzSamplerTracker(config.simFuzzPct ?: 0.0, cardSamples, contestUA, cassorter, previousErrorTracker)

    val name = "${contestUA.id}/${assertionRound.assertion.assorter.shortName()}"
    logger.debug{ "estimateClcaAssertionRound for $name with ${config.nsimEst} trials"}
    val stopwatch = Stopwatch()
    val ntrials = config.nsimEst

    // run the simulation ntrials (=config.nsimEst) times
    val result: RunRepeatedResult = runRepeatedBettingMart(
        name,
        ntrials,
        config,
        samplerTracker=samplerTracker,
        bettingFn,
        clcaUpper=cassorter.upperBound(),
        contestUA.Npop,
        startingTestStatistic,
        moreParameters
    )

    // informational
    val startingErrorRates = if (roundIdx == 1) {
        val aprioriRates = config.clcaConfig.apriori.makeErrorRates(noerror, upper)
        makeAprioriErrorRates(aprioriRates, contestUA.Nphantoms/contestUA.Npop.toDouble())
    } else {
        previousErrorTracker.errorRates()
    }

    val calcMvrsNeeded = assertionRound.calcNewMvrsNeeded(contestRound.contestUA, config)
    assertionRound.estimationResult = EstimationRoundResult(
        roundIdx,
        "${config.simFuzzPct} ClcaFuzzSamplerTracker ",
        calcNewMvrsNeeded = calcMvrsNeeded,
        startingTestStatistic = startingTestStatistic,
        startingErrorRates = startingErrorRates,
        estimatedDistribution = makeDeciles(result.sampleCount),
        lastIndex=0,
        quantile=config.quantile.toInt(),
        ntrials = result.sampleCount.size,
        simNewMvrsNeeded = when {
            (result.sampleCount.size < ntrials/2) -> calcMvrsNeeded // more than half the simulations fail
            (roundIdx == 1) -> result.findQuantile(.50) // TODO set quantile value in AuditConfig ??
            else -> result.findQuantile(config.quantile)
        }
    )

    logger.debug{"estimateClcaAssertionRound $roundIdx ${name} ${makeDeciles(result.sampleCount)} took=$stopwatch"}

    return result
}

fun runRepeatedBettingMart(
    name: String,
    ntrials: Int,
    config: AuditConfig,
    samplerTracker: SamplerTracker,
    bettingFn: BettingFn,
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
    if (debug) testFn.setDebuggingSequences()

    // run the simulation ntrials times
    val result: RunRepeatedResult = runRepeated(
        name = name,
        ntrials = ntrials,
        testFn = testFn,
        testParameters = moreParameters,
        startingTestStatistic = startingTestStatistic,
        samplerTracker = samplerTracker,
        N = N,
    )
    return result
}

private val debug = true

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
    val clcaConfig = config.clcaConfig
    val noerror=oaCassorter.noerror()
    val upper=oaCassorter.assorter.upperBound()

    // one set of fuzzed pairs for all contests and assertions.
    val apriori = clcaConfig.apriori.makeErrorRates(noerror, upper)

    val bettingFn =
        GeneralAdaptiveBetting(
            contestUA.Npop,
            aprioriErrorRates = apriori,
            nphantoms=contestUA.contest.Nphantoms(),
            maxLoss = clcaConfig.maxLoss,
            oaAssortRates=oaCassorter.oaAssortRates, // OMG
            d = clcaConfig.d,
        )

    // uses the vunderFuzz.mvrCvrPairs as is; each trial is a new permutation
    val wantIndices = cardSamples.usedByContests[contestUA.contest.id]
    if (wantIndices == null) {
        throw RuntimeException("failed on cardSamples.usedByContests[${contestUA.contest.id}]")
    }

    val oaFuzzedPairs: List<Pair<AuditableCard, AuditableCard>> = vunderFuzz.mvrCvrPairs
    val previousErrorTracker = assertionRound.previousErrorTracker()
    val sampler =
        // start from where we left off
        ClcaSamplerErrorTracker.fromIndexList(contestUA.contest.id, oaCassorter, oaFuzzedPairs, wantIndices, previousErrorTracker)

    val name = "${contestUA.id}-${assertionRound.assertion.assorter.shortName()}"
    logger.debug{ "estimateOneAuditAssertionRound for $name with ${config.nsimEst} trials"}
    val stopwatch = Stopwatch()
    val ntrials = config.nsimEst

    val result = runRepeatedBettingMart(
        name,
        ntrials,
        config,
        sampler,
        bettingFn,
        clcaUpper=oaCassorter.upperBound(),
        contestUA.Npop,
        startingTestStatistic,
        moreParameters
    )

    // informational
    val startingErrorRates = if (roundIdx == 1) {
        val aprioriRates = config.clcaConfig.apriori.makeErrorRates(noerror, upper)
        makeAprioriErrorRates(aprioriRates, contestUA.Nphantoms/contestUA.Npop.toDouble())
    } else {
        previousErrorTracker.errorRates()
    }

    val calcMvrsNeeded = assertionRound.calcNewMvrsNeeded(contestRound.contestUA, config)
    assertionRound.estimationResult = EstimationRoundResult(
        roundIdx,
        "${vunderFuzz.fuzzPct} OneAuditVunderFuzzer",
        calcNewMvrsNeeded = calcMvrsNeeded,
        startingErrorRates = startingErrorRates,
        startingTestStatistic = startingTestStatistic,
        estimatedDistribution = makeDeciles(result.sampleCount),
        quantile=config.quantile.toInt(),
        lastIndex=0,
        ntrials = result.sampleCount.size,
        simNewMvrsNeeded = when {
            (result.sampleCount.size < ntrials/2) -> calcMvrsNeeded // more than half the simulations fail
            (roundIdx == 1) -> result.findQuantile(.50) // TODO value put in AuditConfig ??
            else -> result.findQuantile(config.quantile)
        }
    )

    logger.info{ "($stopwatch) estimateOneAuditAssertion round $roundIdx ${name} ${makeDeciles(result.sampleCount)}" }
    return result
}


///////////////////////////////////////////////////////////////////////////////////////////////////
//// Polling

fun estimatePollingAssertionRound(
    roundIdx: Int,
    config: AuditConfig,
    contestUA: ContestWithAssertions,
    mvrs: List<Cvr>,
    assertionRound: AssertionRound,
    startingTestStatistic: Double = 1.0,
    estStrategy: String,
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
        estStrategy,
        calcNewMvrsNeeded = 0, // TODO
        startingTestStatistic = startingTestStatistic,
        estimatedDistribution = makeDeciles(result.sampleCount),
        quantile=config.quantile.toInt(),
        ntrials = result.sampleCount.size,
        lastIndex=0,
        simNewMvrsNeeded = when {
            (result.sampleCount.size == 0) -> 0
            (roundIdx == 1) -> result.findQuantile(.50) // TODO put in AuditConfig ??
            else -> result.findQuantile(config.quantile)
        }
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

data class OnlyTask(val contestId: Int, val taskName: String) {
    companion object {
        fun parse(taskName: String?): OnlyTask? {
            if (taskName == null) return null
            val tokens = taskName.split("-")
            return OnlyTask(tokens[0].toInt(), taskName)
        }
    }
}