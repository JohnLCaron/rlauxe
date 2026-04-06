package org.cryptobiotic.rlauxe.estimateOld

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.util.ConcurrentTask
import org.cryptobiotic.rlauxe.util.ConcurrentTaskRunner
import org.cryptobiotic.rlauxe.estimate.VunderPoolsFuzzer
import org.cryptobiotic.rlauxe.estimate.simulateCvrsForContest
import org.cryptobiotic.rlauxe.util.OnlyTask
import org.cryptobiotic.rlauxe.util.Quantiles.percentiles
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.persist.CardManifest


private val logger = KotlinLogging.logger("EstimateSampleSizes")

////////////////////////////////////////////////////////////////////////////////////////////
//// Comparison, Polling, OneAudit.

// TODO obsolete. keeping around for reference for now.
fun estimateSampleSizes(
    config: Config,
    auditRound: AuditRoundIF,
    sortedManifest: CardManifest,
    cardPools: List<CardPool>?,
    batches: List<CardStyleIF>?, // why dont you need batches ?
    previousSamples: Set<Long>,
    showTasks: Boolean = false,
    nthreads: Int = 32,
    onlyTask: OnlyTask? = null,
): List<RunRepeatedResult> {

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
        VunderPoolsFuzzer(cardPools!!, infos, config.round.simulation.simFuzzPct ?: 0.0, cardSamples!!.cards)
    }

    // create the estimation tasks for each contest and assertion
    val tasks = mutableListOf<EstimateSampleSizeTask>()
    auditRound.contestRounds.filter { !it.done }.filter{ onlyTask == null || it.id == onlyTask.contestId }.forEach { contestRound ->
        tasks.addAll(makeEstimationTasks(config, contestRound, auditRound.roundIdx, cardSamples,  vunderFuzz, onlyTask=onlyTask))
    }

    // run estimation tasks concurrently
    val estResults: List<EstimationResult> = ConcurrentTaskRunner<EstimationResult>(showTasks).run(tasks, nthreads=nthreads)

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
private fun makeEstimationTasks(
    config: Config,
    contestRound: ContestRound,
    roundIdx: Int,
    cardSamples: CardSamples?,
    vunderFuzz: VunderPoolsFuzzer?,
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
    val config: Config,
    val cardSamples: CardSamples?,
    val mvrsForPolling: List<Cvr>?,
    val vunderFuzz: VunderPoolsFuzzer?,
    val contestRound: ContestRound,
    val assertionRound: AssertionRound,
    val startingTestStatistic: Double, // T, must grow to 1/riskLimit
    val estStrategy: String,
    val prevSampleSize: Int,
    val moreParameters: Map<String, Double> = emptyMap(),
) : ConcurrentTask<EstimationResult> {

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
    config: Config,
    cardSamples: CardSamples,
    contestRound: ContestRound,
    assertionRound: AssertionRound,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunRepeatedResult {
    val contestUA = contestRound.contestUA
    val contest = contestUA.contest

    val cassertion = assertionRound.assertion as ClcaAssertion
    val cassorter = cassertion.cassorter
    val noerror=cassorter.noerror()
    val upper=cassorter.assorter.upperBound()

    val simulation = config.round.simulation
    val clcaConfig = config.round.clcaConfig!!
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
        ClcaFuzzSamplerTracker(simulation.simFuzzPct ?: 0.0, cardSamples, contestUA, cassorter, previousErrorTracker)

    val name = "${contestUA.id}/${assertionRound.assertion.assorter.shortName()}"
    logger.debug{ "estimateClcaAssertionRound for $name with ${simulation.nsimTrials} trials"}
    val stopwatch = Stopwatch()
    val ntrials = simulation.nsimTrials

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
        val aprioriRates = clcaConfig.apriori.makeErrorRates(noerror, upper)
        makeAprioriErrorRates(aprioriRates, contestUA.Nphantoms/contestUA.Npop.toDouble())
    } else {
        previousErrorTracker.errorRates()
    }

    // create the distribution and find the percentileWanted number of mvrs
    val percentileWanted = simulation.percentile(roundIdx)
    val distribution = result.sampleCount.toIntArray()
    val newMvrsWanted = roundUp(percentiles().index(percentileWanted).compute(*distribution))

    // calculate from "first principles"
    val calcMvrsNeeded = assertionRound.calcNewMvrsNeeded(contestRound.contestUA, config)

    assertionRound.estimationResult = EstimationRoundResult(
        roundIdx,
        "${simulation.simFuzzPct} ClcaFuzzSamplerTracker ",
        calcNewMvrsNeeded = calcMvrsNeeded,
        startingTestStatistic = startingTestStatistic,
        startingErrorRates = startingErrorRates,
        estimatedDistribution = makeDeciles(result.sampleCount),
        lastIndex=0,
        percentile=percentileWanted,
        ntrials = result.sampleCount.size,
        simNewMvrsNeeded = when {
            (result.sampleCount.size < ntrials/2) -> calcMvrsNeeded // more than half the simulations fail TODO set in config
            else -> newMvrsWanted
        }
    )

    logger.debug{"estimateClcaAssertionRound $roundIdx ${name} ${makeDeciles(result.sampleCount)} took=$stopwatch"}

    return result
}

fun runRepeatedBettingMart(
    name: String,
    ntrials: Int,
    config: Config,
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
    config: Config,
    vunderFuzz: VunderPoolsFuzzer,
    cardSamples: CardSamples,
    contestRound: ContestRound,
    assertionRound: AssertionRound,
    startingTestStatistic: Double = 1.0, // T, must grow to 1/riskLimit
    moreParameters: Map<String, Double> = emptyMap(),
): RunRepeatedResult {
    val contestUA = contestRound.contestUA
    val cassertion = assertionRound.assertion as ClcaAssertion
    val oaCassorter = cassertion.cassorter as OneAuditClcaAssorter

    val noerror=oaCassorter.noerror()
    val upper=oaCassorter.assorter.upperBound()

    val simulation = config.round.simulation
    val clcaConfig = config.round.clcaConfig!!


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
    logger.debug{ "estimateOneAuditAssertionRound for $name with ${simulation.nsimTrials} trials"}
    val stopwatch = Stopwatch()
    val ntrials = simulation.nsimTrials

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
        val aprioriRates = clcaConfig.apriori.makeErrorRates(noerror, upper)
        makeAprioriErrorRates(aprioriRates, contestUA.Nphantoms/contestUA.Npop.toDouble())
    } else {
        previousErrorTracker.errorRates()
    }

    // create the distribution and find the percentileWanted number of mvrs
    val percentileWanted = simulation.percentile(roundIdx)
    val distribution = result.sampleCount.toIntArray()
    val newMvrsWanted = roundUp(percentiles().index(percentileWanted).compute(*distribution))

    // calculate from "first principles"
    val calcMvrsNeeded = assertionRound.calcNewMvrsNeeded(contestRound.contestUA, config)

    assertionRound.estimationResult = EstimationRoundResult(
        roundIdx,
        "${vunderFuzz.fuzzPct} OneAuditVunderFuzzer",
        calcNewMvrsNeeded = calcMvrsNeeded,
        startingErrorRates = startingErrorRates,
        startingTestStatistic = startingTestStatistic,
        estimatedDistribution = makeDeciles(result.sampleCount),
        percentile=percentileWanted,
        lastIndex=0,
        ntrials = result.sampleCount.size,
        simNewMvrsNeeded = when {
            (result.sampleCount.size < ntrials/2) -> calcMvrsNeeded // more than half the simulations fail
            else -> newMvrsWanted
        }
    )

    logger.info{ "($stopwatch) estimateOneAuditAssertion round $roundIdx ${name} ${makeDeciles(result.sampleCount)}" }
    return result
}


///////////////////////////////////////////////////////////////////////////////////////////////////
//// Polling

fun estimatePollingAssertionRound(
    roundIdx: Int,
    config: Config,
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
    val simulation = config.round.simulation
    val useFuzz = simulation.simFuzzPct ?: 0.0
    val samplerTracker = PollingFuzzSamplerTracker(useFuzz, mvrs, contestUA.contest as Contest, assorter)

    val name = "${contestUA.id}/${assertionRound.assertion.assorter.shortName()}"
    logger.debug{ "estimatePollingAssertionRound for $name with ${simulation.nsimTrials} trials"}
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

    // create the distribution and find the percentileWanted number of mvrs
    val percentileWanted = simulation.percentile(roundIdx)
    val distribution = result.sampleCount.toIntArray()
    val newMvrsWanted = roundUp(percentiles().index(percentileWanted).compute(*distribution))

    // calculate from "first principles" only for CLCA; TODO ??
    // val calcMvrsNeeded = assertionRound.calcNewMvrsNeeded(contestRound.contestUA, config)

    assertionRound.estimationResult = EstimationRoundResult(
        roundIdx,
        estStrategy,
        calcNewMvrsNeeded = 0, // TODO
        startingTestStatistic = startingTestStatistic,
        estimatedDistribution = makeDeciles(result.sampleCount),
        percentile=percentileWanted,
        ntrials = result.sampleCount.size,
        lastIndex=0,
        simNewMvrsNeeded = newMvrsWanted
    )

    logger.debug{"estimatePollingAssertionRound $roundIdx ${name} ${makeDeciles(result.sampleCount)} took=$stopwatch"}

    return result
}

fun runRepeatedAlphaMart(
    name: String,
    config: Config,
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
        d = config.round.pollingConfig!!.d,
        eta0 = eta0,
    )

    val testFn = AlphaMart(
        estimFn = useEstimFn,
        N = N,
        tracker = samplerTracker,
        upperBound = upperBound,
        riskLimit = config.creation.riskLimit,
    )

    val result: RunRepeatedResult = runRepeated(
        name,
        ntrials = config.simulation.nsimTrials,
        testFn = testFn,
        testParameters = mapOf("ntrials" to config.simulation.nsimTrials.toDouble(), "polling" to 1.0) + moreParameters,
        startingTestStatistic = startingTestStatistic,
        samplerTracker = samplerTracker,
        N = N,
    )
    return result
}

