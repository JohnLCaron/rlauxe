package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.BettingFn
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.ClcaAssorterOneAudit
import org.cryptobiotic.rlauxe.oneaudit.OneAuditErrorsFromPools
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.VunderBar
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.makeDeciles
import org.cryptobiotic.rlauxe.workflow.ClcaSampling
import org.cryptobiotic.rlauxe.workflow.Sampling
import kotlin.collections.List
import kotlin.collections.mutableListOf
import kotlin.math.min

private val debug = false
private val debugSampleDist = false
private val debugSampleSmall = false

private val logger = KotlinLogging.logger("EstimateSampleSizes")

////////////////////////////////////////////////////////////////////////////////////////////
//// Comparison, Polling, OneAudit.

// for CLCA and OA, take the first L=config.contestSampleCutoff values in the cardManifest (i.e. the actual cards)
// For polling, use ContestSimulation.simulateCvrsDilutedMargin(contest as Contest, config).makeCvrs()
//    using Npop and diluted margin.

// 1. _Estimation_: for each contest, estimate how many samples are needed for this AuditRound
fun estimateSampleSizes(
    config: AuditConfig,
    auditRound: AuditRound,
    cardManifest: CloseableIterable<AuditableCard>, // Clca, OneAudit, ignored for Polling
    cardPools: List<CardPoolIF>?, // Clca, OneAudit, ignored for Polling
    showTasks: Boolean = false,
    nthreads: Int = 32,
): List<RunTestRepeatedResult> {

    // simulate the card pools for all OneAudit contests; here because its over all contests
    val infos = auditRound.contestRounds.map { it.contestUA.contest.info() }.associateBy { it.id }
    val vunderFuzz = if (!config.isOA) null else {
        OneAuditVunderBarFuzzer(VunderBar(cardPools!!), infos, config.simFuzzPct ?: 0.0)
    }

    // create the estimation tasks for each contest
    val tasks = mutableListOf<EstimateSampleSizeTask>()
    auditRound.contestRounds.filter { !it.done }.forEach { contestRound ->
        tasks.addAll(makeEstimationTasks(config, contestRound, auditRound.roundIdx, cardManifest,  vunderFuzz))
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
        task.assertionRound.estSampleSize = min(estNewSamples + task.prevSampleSize, task.contestRound.Npop)

        if (debug) println(result.showSampleDist(estResult.task.contestRound.id))
        if (debugSampleSmall && result.avgSamplesNeeded() < 10) {
            println(" ** avgSamplesNeeded ${result.avgSamplesNeeded()} < 10; task=${task.name()}")
        }
        if (debugSampleDist) {
            println(
                "---debugSampleDist for '${task.name()}' ${auditRound.roundIdx} ntrials=${config.nsimEst} pctSamplesNeeded=" +
                        "${df(result.pctSamplesNeeded())} estSampleSize=${task.assertionRound.estSampleSize}" +
                        " totalSamplesNeeded=${result.totalSamplesNeeded} nsuccess=${result.nsuccess}" +
                        "\n  sampleDist = ${result.showSampleDist(estResult.task.contestRound.id)}"
            )
        }
    }

    // put results into contestRounds
    auditRound.contestRounds.filter { !it.done }.forEach { contest ->
        val sampleSizes = estResults.filter { it.task.contestRound.id == contest.id }
            .map { it.task.assertionRound.estSampleSize }
        contest.estSampleSize = if (sampleSizes.isEmpty()) 0 else sampleSizes.max()
        val newSampleSizes = estResults.filter { it.task.contestRound.id == contest.id }
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
    vunderFuzz: OneAuditVunderBarFuzzer?,
    moreParameters: Map<String, Double> = emptyMap(),
): List<EstimateSampleSizeTask> {

    val tasks = mutableListOf<EstimateSampleSizeTask>()

    // get the first n cards for this contest
    // assumes the cards are already randomized
    val contestCards = ContestCardsLimited(contestRound.contestUA.id, config.contestSampleCutoff, cardManifest.iterator()).cards()

    contestRound.assertionRounds.map { assertionRound ->
        if (!assertionRound.status.complete) {
            var prevSampleSize = 0
            var startingTestStatistic = 1.0
            if (roundIdx > 1) {
                val prevAuditResult = assertionRound.prevAuditResult!!
                if (prevAuditResult.samplesUsed == contestRound.Npop) {
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
                        contestCards = contestCards,
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
    return tasks
}

// For one contest, for one assertion, a concurrent task
class EstimateSampleSizeTask(
    val roundIdx: Int,
    val config: AuditConfig,
    val contestCards: List<AuditableCard>,
    val vunderFuzz: OneAuditVunderBarFuzzer?,
    val contestRound: ContestRound,
    val assertionRound: AssertionRound,
    val startingTestStatistic: Double,
    val prevSampleSize: Int,
    val moreParameters: Map<String, Double> = emptyMap(),
) : ConcurrentTaskG<EstimationResult> {

    override fun name() = "task ${contestRound.name} ${assertionRound.assertion.assorter.desc()} ${config.strategy()}}"

    // all assertions share the same cvrs. run ntrials (=config.nsimEst times).
    // each time the trial is run, the cvrs are randomly permuted. The result is a distribution of ntrials sampleSizes.
    override fun run(): EstimationResult {
        val result: RunTestRepeatedResult = when (config.auditType) {
            AuditType.CLCA ->
                estimateClcaAssertionRound(
                    roundIdx,
                    config,
                    contestCards,
                    contestRound,
                    assertionRound,
                    startingTestStatistic
                )
            AuditType.POLLING ->
                estimatePollingAssertionRound(
                    roundIdx,
                    config,
                    contestRound.contestUA,
                    contestCards,
                    assertionRound,
                    startingTestStatistic,
                    moreParameters=moreParameters,
                )
            AuditType.ONEAUDIT ->
                estimateOneAuditAssertionRound(
                    roundIdx,
                    config,
                    contestCards,
                    vunderFuzz!!,
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
    val repeatedResult: RunTestRepeatedResult,
)

/////////////////////////////////////////////////////////////////////////////////////////////////
//// Clca, including with IRV

private const val quiet = true

fun estimateClcaAssertionRound(
    roundIdx: Int,
    config: AuditConfig,
    contestCards: List<AuditableCard>,
    contestRound: ContestRound,
    assertionRound: AssertionRound,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val contestUA = contestRound.contestUA
    val contest = contestUA.contest

    val clcaConfig = config.clcaConfig
    val cassertion = assertionRound.assertion as ClcaAssertion
    val cassorter = cassertion.cassorter

    // duplicate to ClcaAssertionAuditor
    val prevRounds: ClcaErrorCounts = assertionRound.accumulatedErrorCounts(contestRound)
    prevRounds.setPhantomRate(contest.phantomRate()) // TODO ??

    val bettingFn: BettingFn = // if (clcaConfig.strategy == ClcaStrategyType.generalAdaptive) {
        GeneralAdaptiveBetting(contestUA.Npop, oaErrorRates = null, d = clcaConfig.d, maxRisk=clcaConfig.maxRisk)

    /* } else if (clcaConfig.strategy == ClcaStrategyType.apriori) {
        //AdaptiveBetting(N = contestUA.Npop, a = cassorter.noerror(), d = clcaConfig.d, errorRates=clcaConfig.pluralityErrorRates!!) // just stick with them
        val errorRates= ClcaErrorCounts.fromPluralityAndPrevRates(clcaConfig.pluralityErrorRates!!, prevRounds)
        GeneralAdaptiveBettingOld(N = contestUA.Npop, startingErrorRates = errorRates, d = clcaConfig.d,)

    } else if (clcaConfig.strategy == ClcaStrategyType.fuzzPct) {
        val errorsP = ClcaErrorTable.getErrorRates(contest.ncandidates, clcaConfig.fuzzPct) // TODO do better
        val errorRates= ClcaErrorCounts.fromPluralityAndPrevRates(errorsP, prevRounds)
        // AdaptiveBetting(N = contestUA.Npop, a = cassorter.noerror(), d = clcaConfig.d, errorRates=errorsP) // just stick with them
        GeneralAdaptiveBettingOld(N = contestUA.Npop, startingErrorRates = errorRates, d = clcaConfig.d,)

    } else {
        throw RuntimeException("unsupported strategy ${clcaConfig.strategy}")
    } */

    // TODO track down simulations and do initial permutation there; we want first trial to use the actual permutation
    // we need a permutation to get uniform distribution of errors, since some simulations put all the errors at the beginning
    // sampler.reset()

    val sampler = ClcaCardFuzzSampler(config.simFuzzPct ?: 0.0, contestCards, contestUA.contest, cassorter)

    // run the simulation ntrials (=config.nsimEst) times
    val result: RunTestRepeatedResult = runRepeatedBettingMart(
        config,
        sampler,
        bettingFn,
        cassorter.noerror(),
        upper=cassorter.assorter.upperBound(),
        clcaUpper=cassorter.upperBound(),
        contestUA.Npop,
        startingTestStatistic,
        moreParameters
    )

    // The result is a distribution of ntrials sampleSizes
    assertionRound.estimationResult = EstimationRoundResult(roundIdx,
        clcaConfig.strategy.name,
        fuzzPct = config.simFuzzPct,
        startingTestStatistic = startingTestStatistic,
        startingRates = prevRounds.errorRates(),
        estimatedDistribution = makeDeciles(result.sampleCount),
        firstSample = if (result.sampleCount.isEmpty()) 0 else result.sampleCount[0],
    )

    return result
}

fun runRepeatedBettingMart(
    config: AuditConfig,
    sampleFn: Sampling,
    bettingFn: BettingFn,
    noerror: Double,
    upper: Double, // cassorter.assorter.upperBound(),
    clcaUpper: Double, // clca assorter
    N: Int,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {

    val tracker = ClcaErrorTracker(noerror, upper)

    val testFn = BettingMart(
        bettingFn = bettingFn,
        N = N,
        riskLimit = config.riskLimit,
        sampleUpperBound = clcaUpper,
    )

    // run the simulation ntrials (config.nsimEst) times
    val result: RunTestRepeatedResult = runTestRepeated(
        drawSample = sampleFn,
        ntrials = config.nsimEst,
        testFn = testFn,
        testParameters = moreParameters,
        startingTestStatistic = startingTestStatistic,
        tracker = tracker,
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
    val eta0 = assorter.dilutedMean()

    // TODO isnt this the same problam as OneAudit ??
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
        firstSample = if (result.sampleCount.isEmpty()) 0 else result.sampleCount[0],
    )

    return result
}

fun runRepeatedAlphaMart(
    config: AuditConfig,
    sampleFn: Sampling,
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
        tracker = ClcaErrorTracker(0.0, 1.0),
        N = N,
    )
    return result
}

///////////////////////////////////////////////////////////////////////////////////////////////////
//// OneAudit

fun estimateOneAuditAssertionRound(
    roundIdx: Int,
    config: AuditConfig,
    contestCards: List<AuditableCard>,
    vunderFuzz: OneAuditVunderBarFuzzer,
    contestRound: ContestRound,
    assertionRound: AssertionRound,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val contestUA = contestRound.contestUA
    val cassertion = assertionRound.assertion as ClcaAssertion
    val oaCassorter = cassertion.cassorter as ClcaAssorterOneAudit
    val oaConfig = config.oaConfig
    val clcaConfig = config.clcaConfig

    // TODO could pass the fuzzed mvrs in always, to simplify
    // TODO wait we already wrote the fuzzed cards, I think.
    // The estimation and the audit are using a different fuzz.
    // TODO this doesnt maintain the right proportions when you dont fuzz the entire set, ie you just take the first 20K
    // So can we grab the fuzzed cards?
    vunderFuzz.reset()
    val oaFuzzedPairs: List<Pair<Cvr, AuditableCard>> = vunderFuzz.makePairsFromCards(contestCards)
    val pools = vunderFuzz.vunderBar.pools

    /////////////////////////////////////////////////////////
    /* for debugging, lets write these to disk so we can compare to the audited fuzzed cards
    val fuzzedMvrs = oaFuzzedPairs.map { it.first }
    val tempFile = "/home/stormy/rla/persist/testRunCli/oneaudit/audit/estMvrs${roundIdx}.csv"
    writeUnsortedMvrs(fuzzedMvrs, tempFile)

    val info2 = ContestInfo("contest2", 2,  mapOf("Wes" to 1), SocialChoiceFunction.PLURALITY)
    val infos = mapOf(contestUA.id to contestUA.contest.info(), 2 to info2)
     val fuzzedMvrTab = tabulateCvrs(fuzzedMvrs.iterator(), infos)
    println("fuzzedMvrTab= ${fuzzedMvrTab[contestUA.id]}")

    val fuzzedPool = calcCardPoolsFromMvrs(
        infos,
        cardStyles = listOf(CardStyle("pool42", listOf(1,2), 42)),
        fuzzedMvrs,
    )
    println("pool= ${pools.first().show()}")
    println("fuzzedPool= ${fuzzedPool.first().show()}")
    // require(pools == fuzzedPool)
    println()
    ////////////////////////////////////////////////////////////////////
     */

    // duplicate to OneAuditAssertionAuditor
    val prevRounds: ClcaErrorCounts = assertionRound.accumulatedErrorCounts(contestRound)
    prevRounds.setPhantomRate(contestUA.contest.phantomRate()) // TODO ??

    // could also get from the vunderFuzz
    val oneAuditErrorsFromPools = OneAuditErrorsFromPools(pools)
    val oaErrorRates = oneAuditErrorsFromPools.oaErrorRates(contestUA, oaCassorter)

    val bettingFn: BettingFn = // if (clcaConfig.strategy == ClcaStrategyType.generalAdaptive) {
        GeneralAdaptiveBetting(Npop = contestUA.Npop, oaErrorRates=oaErrorRates, d = clcaConfig.d, maxRisk=clcaConfig.maxRisk)

    /*
    val clcaBettingFn: BettingFn = if (clcaConfig.strategy == ClcaStrategyType.generalAdaptive) {
        GeneralAdaptiveBettingOld(N = contestUA.Npop, startingErrorRates = prevRounds, d = clcaConfig.d,)

    } else if (clcaConfig.strategy == ClcaStrategyType.apriori) {
        val errorRates= ClcaErrorCounts.fromPluralityAndPrevRates(clcaConfig.pluralityErrorRates!!, prevRounds)
        GeneralAdaptiveBettingOld(N = contestUA.Npop, startingErrorRates = errorRates, d = clcaConfig.d,)

    } else if (clcaConfig.strategy == ClcaStrategyType.fuzzPct) {
        val errorsP = ClcaErrorTable.getErrorRates(contestUA.contest.ncandidates, clcaConfig.fuzzPct) // TODO do better
        val errorRates= ClcaErrorCounts.fromPluralityAndPrevRates(errorsP, prevRounds)
        GeneralAdaptiveBettingOld(N = contestUA.Npop, startingErrorRates = errorRates, d = clcaConfig.d,)

    } else {
        throw RuntimeException("unsupported strategy ${clcaConfig.strategy}")
    }


    // enum class OneAuditStrategyType { reportedMean, bet99, eta0Eps, optimalComparison }
    val strategy = config.oaConfig.strategy
    val result = if (strategy == OneAuditStrategyType.clca || strategy == OneAuditStrategyType.optimalComparison) {
        val bettingFn: BettingFn = if (strategy == OneAuditStrategyType.clca) clcaBettingFn else {
            // TODO p2o = clcaBettingFn.startingErrorRates.get("p2o")
            OptimalComparisonNoP1(contestUA.Npop, true, oaCassorter.upperBound)
        } */

    val sampler = ClcaSampling(contestUA.contest.id, oaFuzzedPairs, oaCassorter, allowReset = true)

    val result = runRepeatedBettingMart(
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

    /* } else {
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
            N = contestUA.Npop,
            startingTestStatistic = startingTestStatistic,
            moreParameters
        )
    } */

    assertionRound.estimationResult = EstimationRoundResult(
        roundIdx,
        oaConfig.strategy.name,
        fuzzPct = config.simFuzzPct, // TODO used ??
        startingTestStatistic = startingTestStatistic,
        estimatedDistribution = makeDeciles(result.sampleCount),
        firstSample = if (result.sampleCount.size > 0) result.sampleCount[0] else 0,
    )

    logger.info{"estimateOneAuditAssertionRound $roundIdx ${contestUA.id} ${oaCassorter.assorter().desc()} ${makeDeciles(result.sampleCount)} " +
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
