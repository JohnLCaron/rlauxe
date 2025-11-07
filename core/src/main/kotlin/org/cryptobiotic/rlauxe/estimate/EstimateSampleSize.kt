package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.raire.RaireContest
import org.cryptobiotic.rlauxe.raire.SimulateIrvTestData
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.makeDeciles
import kotlin.math.min
import kotlin.random.Random

private val debug = false
private val debugErrorRates = false
private val debugSampleDist = false
private val debugSampleSmall = false

private val logger = KotlinLogging.logger("EstimateSampleSizes")

////////////////////////////////////////////////////////////////////////////////////////////
//// Comparison, Polling, OneAudit.

// for CLCA and OA, take the first L values in the manifest.
// For polling, use ContestSimulation.simulateContestCvrsWithLimits(contest as Contest, config).makeCvrs()
//    but use Nb and diluted margin.

// 1. _Estimation_: for each contest, estimate how many samples are needed for this AuditRound
fun estimateSampleSizes(
    config: AuditConfig,
    auditRound: AuditRound,
    cardManifest: CloseableIterable<AuditableCard>?, // Clca, OneAudit
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

// For one contest, generate a task for each assertion thats not been completed
// starts from where the last audit left off (prevAuditResult.pvalue)
fun makeEstimationTasks(
    config: AuditConfig,
    contestRound: ContestRound,
    roundIdx: Int,
    cardManifest: CloseableIterable<AuditableCard>?,
    moreParameters: Map<String, Double> = emptyMap(),
): List<EstimateSampleSizeTask> {
    val tasks = mutableListOf<EstimateSampleSizeTask>()

    // simulate the cvrs once for all the assertions for this contest
    val contestUA = contestRound.contestUA
    val cvrs: List<Cvr> = when (config.auditType) {
        AuditType.CLCA -> {
            if (contestUA.isIrv) {
                // TODO test this
                val testData = SimulateIrvTestData(contestUA.contest as RaireContest, contestRound.contestUA.minDilutedMargin(), config.contestSampleCutoff)
                testData.makeCvrs()
            } else {
                CvrsLimited(contestUA.id, config.contestSampleCutoff, cardManifest!!.iterator()).cvrs()
            }
        }

        AuditType.ONEAUDIT -> {
            CvrsLimited(contestUA.id, config.contestSampleCutoff, cardManifest!!.iterator()).cvrs()
        }

        AuditType.POLLING -> {
            ContestSimulation.simulateCvrsDilutedMargin(contestRound.contestUA, config)
        }
    }

    // logger.debug{ "add assertionRounds for contest ${contestRound.contestUA.id} round $roundIdx"}

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
                        cvrList = cvrs,
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
    val cvrList: List<Cvr>,
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
                    cvrList,
                    assertionRound,
                    startingTestStatistic
                )
            AuditType.POLLING ->
                estimatePollingAssertionRound(
                    roundIdx,
                    config,
                    contest.contestUA.contest,
                    cvrList,
                    assertionRound,
                    startingTestStatistic,
                    moreParameters=moreParameters,
                )
            AuditType.ONEAUDIT ->
                estimateOneAuditAssertionRound(
                    roundIdx,
                    config,
                    contest.contestUA,
                    cvrList,
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
    cvrList: List<Cvr>,
    assertionRound: AssertionRound,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val clcaConfig = config.clcaConfig
    val cassertion = assertionRound.assertion as ClcaAssertion
    val cassorter = cassertion.cassorter
    val contest = contestUA.contest

    // strategies to choose how much error there is
    var fuzzPct = 0.0
    val errorRates = when {
        (clcaConfig.strategy == ClcaStrategyType.previous) -> {
            var errorRates = ClcaErrorRates(0.0, contest.phantomRate(), 0.0, 0.0)
            if (assertionRound.prevAuditResult != null) {
                errorRates = assertionRound.prevAuditResult!!.measuredRates!!
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

    // TODO subsequent rounds
    // optional fuzzing of the cvrs
    val isIrvFzz = (contest.isIrv() && clcaConfig.simFuzzPct != null)
    val (sampler: Sampler, bettingFn: BettingFn) = if (errorRates != null && !errorRates.areZero()) {
        if (isIrvFzz) fuzzPct = clcaConfig.simFuzzPct
        Pair(
            if (isIrvFzz) ClcaFuzzSampler(clcaConfig.simFuzzPct, cvrList, contest, cassorter)
                else ClcaSimulatedErrorRates(cvrList, contest, cassorter, errorRates), // TODO why cant we use this with IRV??
            AdaptiveBetting(Nc = contest.Nc(), a = cassorter.noerror(), d = clcaConfig.d, errorRates = errorRates)
        )
    } else {
        // this is noerrors
        Pair(
            makeClcaNoErrorSampler(contest.id, cvrList, cassorter),
            AdaptiveBetting(Nc = contest.Nc(), a = cassorter.noerror(), d = clcaConfig.d, errorRates = ClcaErrorRates.Zero)
        )
    }

    // we need a permutation to get uniform distribution of errors, since some simulations put all the errors at the beginning
    sampler.reset()

    // run the simulation ntrials (=config.nsimEst) times
    val result: RunTestRepeatedResult = runRepeatedBettingMart(
        config,
        sampler,
        bettingFn,
        // cassorter.assorter().reportedMargin(),
        cassorter.noerror(),
        cassorter.upperBound(),
        contest.Nc(),
        startingTestStatistic,
        moreParameters
    )

    // The result is a distribution of ntrials sampleSizes
    assertionRound.estimationResult = EstimationRoundResult(roundIdx,
        clcaConfig.strategy.name,
        fuzzPct = fuzzPct,
        startingTestStatistic = startingTestStatistic,
        startingRates = errorRates,
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
    Nc: Int,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {

    val testFn = BettingMart(
        bettingFn = bettingFn,
        Nc = Nc,
        noerror = noerror,
        riskLimit = config.riskLimit,
        upperBound = upperBound,
    )

    // run the simulation ntrials (config.nsimEst) times
    val result: RunTestRepeatedResult = runTestRepeated(
        drawSample = sampleFn,
        ntrials = config.nsimEst,
        testFn = testFn,
        testParameters = moreParameters,
        startingTestStatistic = startingTestStatistic,
        Nc = Nc,
    )
    return result
}

///////////////////////////////////////////////////////////////////////////////////////////////////
//// Polling

fun estimatePollingAssertionRound(
    roundIdx: Int,
    config: AuditConfig,
    contest: ContestIF,
    cvrs: List<Cvr>,
    assertionRound: AssertionRound,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val assorter = assertionRound.assertion.assorter
    val eta0 = assorter.reportedMean()

    // optional fuzzing of the cvrs
    var fuzzPct = 0.0
    val pollingConfig = config.pollingConfig
    val sampler = if (pollingConfig.simFuzzPct == null || pollingConfig.simFuzzPct == 0.0) {
        PollWithoutReplacement(contest.id, cvrs, assorter, allowReset=true)
    } else {
        fuzzPct = pollingConfig.simFuzzPct
        PollingFuzzSampler(pollingConfig.simFuzzPct, cvrs, contest as Contest, assorter) // TODO cant use Raire
    }

    val result = runRepeatedAlphaMart(
        config,
        sampler,
        null,
        eta0 = eta0,
        upperBound = assorter.upperBound(),
        Nc = contest.Nc(),
        startingTestStatistic = startingTestStatistic,
        moreParameters = moreParameters,
    )

    assertionRound.estimationResult = EstimationRoundResult(roundIdx,
        "default",
        fuzzPct = fuzzPct,
        startingTestStatistic = startingTestStatistic,
        estimatedDistribution = makeDeciles(result.sampleCount),
        firstSample = result.sampleCount[0],
        )

    return result
}

fun runRepeatedAlphaMart(
    config: AuditConfig,
    sampleFn: Sampler,
    estimFn: EstimFn?, // if null use default TruncShrinkage
    eta0: Double,  // initial estimate of mean
    upperBound: Double,
    Nc: Int,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    // val margin = mean2margin(eta0)

    val useEstimFn = estimFn ?: TruncShrinkage(
        N = Nc,
        upperBound = upperBound,
        d = config.pollingConfig.d,
        eta0 = eta0,
    )

    val testFn = AlphaMart(
        estimFn = useEstimFn,
        N = Nc,
        upperBound = upperBound,
        riskLimit = config.riskLimit,
    )

    val result: RunTestRepeatedResult = runTestRepeated(
        drawSample = sampleFn,
        ntrials = config.nsimEst,
        testFn = testFn,
        testParameters = mapOf("ntrials" to config.nsimEst.toDouble(), "polling" to 1.0) + moreParameters,
        startingTestStatistic = startingTestStatistic,
        // margin = margin,
        Nc = Nc,
    )
    return result
}

///////////////////////////////////////////////////////////////////////////////////////////////////
//// OneAudit

// estimateClcaAssertionRound

fun estimateOneAuditAssertionRound(
    roundIdx: Int,
    config: AuditConfig,
    contestUA: ContestUnderAudit,
    cvrList: List<Cvr>,
    assertionRound: AssertionRound,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val cassertion = assertionRound.assertion as ClcaAssertion
    val oaCassorter = cassertion.cassorter as OneAuditClcaAssorter
    val oaConfig = config.oaConfig
    val fuzzPct = 0.0

    // this is noerrors TODO subsequent rounds
    // TODO this works but CvrsLimitedSampler doesnt on testOneAuditContestAuditTaskGenerator
    val samplerOrg =
        OneAuditNoErrorIterator(
            contestUA.id,
            contestUA.Nc,
            config.contestSampleCutoff,
            cassertion.cassorter,
            cvrList.iterator(),
        )

    val sampler = CvrsLimitedSampler(contestUA.id,  cassertion.cassorter, cvrList)

    val strategy = config.oaConfig.strategy
    val result = if (strategy == OneAuditStrategyType.optimalComparison || strategy == OneAuditStrategyType.optimalBet) {
        val bettingFn: BettingFn = OptimalComparisonNoP1(contestUA.Nc, true, oaCassorter.upperBound, p2 = 0.0)

        runRepeatedBettingMart(
            config,
            samplerOrg,
            bettingFn,
            // oaCassorter.assorter().reportedMargin(),
            oaCassorter.noerror(),
            oaCassorter.upperBound(),
            contestUA.Nc,
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
            Nc = contestUA.Nc,
            startingTestStatistic = startingTestStatistic,
            moreParameters
        )
    }

    assertionRound.estimationResult = EstimationRoundResult(
        roundIdx,
        oaConfig.strategy.name,
        fuzzPct = fuzzPct,
        startingTestStatistic = startingTestStatistic,
        estimatedDistribution = makeDeciles(result.sampleCount),
        firstSample = if (result.sampleCount.size > 0) result.sampleCount[0] else 0,
    )

    logger.info{"simulateSampleSizeOneAuditAssorter $roundIdx ${contestUA.id} ${oaCassorter.assorter().desc()} ${makeDeciles(result.sampleCount)} " +
                "first=${result.sampleCount[0]}"}
    return result
}

// take the first contestSampleCutoff cards that contain the contest, convert to Cvrs
// TODO this assume the cards are randomized, but some of the tests dont do that...track them down
class CvrsLimited(
    val contestId: Int,
    val contestSampleCutoff: Int?,
    cardIter: Iterator<AuditableCard>,
) {
    private val cvrs: List<Cvr>

    init {
        val cards = mutableListOf<AuditableCard>()
        while ((contestSampleCutoff == null || cards.size < contestSampleCutoff) && cardIter.hasNext()) {
            val card = cardIter.next()
            if (card.hasContest(contestId)) cards.add(card)
        }
        cvrs = cards.map{ it.cvr() }
    }

    fun cvrs() = cvrs
}

// already checked if cvrs.hasContest()
class CvrsLimitedSampler(
    val contestId: Int,
    val cassorter: ClcaAssorter,
    val cvrs: List<Cvr>,
): Sampler, Iterator<Double> {
    private var permutedIndex = mutableListOf<Int>()
    private var idx = 0
    private var done = false

    init {
        permutedIndex = MutableList(cvrs.size) { it }
    }

    override fun sample(): Double {
        while (idx < cvrs.size) {
            val cvr = cvrs[permutedIndex[idx]]
            idx++
            val result = cassorter.bassort(cvr, cvr)
            return result
        }
        if (!warned) {
            logger.warn { "OneAuditNoErrorIterator no samples left for ${contestId} and ComparisonAssorter ${cassorter}" }
            warned = true
        }
        return 0.0
    }

    override fun reset() {
        permutedIndex.shuffle(Random)
        idx = 0
    }

    override fun maxSamples() = cvrs.size
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = cvrs.size

    override fun hasNext() = !done && (idx < cvrs.size)
    override fun next() = sample()

    companion object {
        var warned = false
    }
}
