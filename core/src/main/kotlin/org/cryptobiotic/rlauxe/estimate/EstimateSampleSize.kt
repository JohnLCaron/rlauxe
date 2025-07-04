package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.oneaudit.makeTestMvrsScaled
import org.cryptobiotic.rlauxe.raire.RaireContest
import org.cryptobiotic.rlauxe.raire.SimulateIrvTestData
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.makeDeciles
import org.cryptobiotic.rlauxe.util.mean2margin
import kotlin.math.min

private val debug = false
private val debugErrorRates = false
private val debugSampleDist = false
private val debugSizeNudge = true
private val debugSampleSmall = false

// TODO: always one contest, always the minimum-margin assertion (?)

////////////////////////////////////////////////////////////////////////////////////////////
//// Comparison, Polling, OneAudit.

// 1. _Estimation_: for each contest, estimate how many samples are needed to satisfy the risk function,
fun estimateSampleSizes(
    auditConfig: AuditConfig,
    auditRound: AuditRound,
    showTasks: Boolean = false,
    nthreads: Int = 32,
): List<RunTestRepeatedResult> {

    // create the estimation tasks
    val tasks = mutableListOf<EstimateSampleSizeTask>()
    auditRound.contestRounds.filter { !it.done }.forEach { contest ->
        tasks.addAll(makeEstimationTasks(auditConfig, contest, auditRound.roundIdx))
    }

    // run tasks concurrently
    val estResults: List<EstimationResult> = ConcurrentTaskRunnerG<EstimationResult>(showTasks).run(tasks, nthreads=nthreads)

    // put results into assertionRounds
    estResults.forEach { estResult ->
        val task = estResult.task
        val result = estResult.repeatedResult

        val estNewSamples = result.findQuantile(auditConfig.quantile)
        task.assertionRound.estNewSampleSize = estNewSamples
        task.assertionRound.estSampleSize = min(estNewSamples + task.prevSampleSize, task.contest.Nc)

        if (debug) println(result.showSampleDist(estResult.task.contest.id))
        if (debugSampleSmall && result.avgSamplesNeeded() < 10) {
            println(" ** avgSamplesNeeded ${result.avgSamplesNeeded()} < 10; task=${task.name()}")
        }
        if (debugSampleDist) {
            println(
                "---debugSampleDist for '${task.name()}' ${auditRound.roundIdx} ntrials=${auditConfig.nsimEst} pctSamplesNeeded=" +
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
        // if (!quiet) println(" ** contest ${contest.id} avgSamplesNeeded ${contest.estSampleSize} task=${contest.estNewSamples}")
    }

    // return repeatedResult for debugging and diagnostics
    return estResults.map { it.repeatedResult }
}

// For one contest, generate a task for each assertion thats not been completed
// starts from where the last audit left off (prevAuditResult.pvalue)
fun makeEstimationTasks(
    auditConfig: AuditConfig,
    contestRound: ContestRound,
    roundIdx: Int,
    moreParameters: Map<String, Double> = emptyMap(),
): List<EstimateSampleSizeTask> {
    val tasks = mutableListOf<EstimateSampleSizeTask>()

    // make the cvrs once for all the assertions for this contest
    val contest = contestRound.contestUA.contest
    val cvrs: List<Cvr> = when (auditConfig.auditType) {
        AuditType.CLCA -> {
            // Simulation of Contest that reflects the exact votes and Nc, along with undervotes and phantoms, as specified in Contest.
            if (contest.isIRV()) {
                SimulateIrvTestData(contest as RaireContest, contestRound.contestUA.minMargin(), auditConfig.sampleLimit).makeCvrs()
            } else {
                ContestSimulation.makeContestWithLimits(contest as Contest, auditConfig.sampleLimit).makeCvrs()
            }
        }
        AuditType.POLLING -> {
            // Simulation of multicandidate Contest that reflects the exact votes and Nc, along with undervotes and phantoms, as specified in Contest.
            // TODO what about supermajority?
            ContestSimulation.makeContestWithLimits(contest as Contest, auditConfig.sampleLimit).makeCvrs()
        }
        AuditType.ONEAUDIT -> {
            val contestOA = (contestRound.contestUA as OAContestUnderAudit).contestOA
            makeTestMvrsScaled(contestOA, auditConfig.sampleLimit)
        }
    }

    contestRound.assertionRounds.map { assertionRound ->
        if (!assertionRound.status.complete) {
            var prevSampleSize = 0
            var startingTestStatistic = 1.0
            if (roundIdx > 1) {
                val prevAuditResult = assertionRound.prevAuditResult!!
                if (prevAuditResult.samplesUsed == contestRound.Nc) {   // TODO or pct of ?
                    println("***LimitReached $contestRound")
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
                        auditConfig,
                        contestRound,
                        cvrs,
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
    val auditConfig: AuditConfig,
    val contest: ContestRound,
    val cvrs: List<Cvr>,
    val assertionRound: AssertionRound,
    val startingTestStatistic: Double,
    val prevSampleSize: Int,
    val moreParameters: Map<String, Double> = emptyMap(),
) : ConcurrentTaskG<EstimationResult> {

    override fun name() = "task ${contest.name} ${assertionRound.assertion.assorter.desc()} ${auditConfig.strategy()}}"

    // all assertions share the same cvrs. run ntrials (=auditConfig.nsimEst times).
    // each time the trial is run, the cvrs are randomly permuted. The result is a distribution of ntrials sampleSizes.
    override fun run(): EstimationResult {
        val result: RunTestRepeatedResult = when (auditConfig.auditType) {
            AuditType.CLCA ->
                simulateSampleSizeClcaAssorter(
                    roundIdx,
                    auditConfig,
                    contest.contestUA,
                    cvrs,
                    assertionRound,
                    startingTestStatistic
                )
            AuditType.POLLING ->
                simulateSampleSizePollingAssorter(
                    roundIdx,
                    auditConfig,
                    contest.contestUA.contest,
                    cvrs,
                    assertionRound,
                    startingTestStatistic,
                    moreParameters=moreParameters,
                )
            AuditType.ONEAUDIT ->
                simulateSampleSizeOneAuditAssorter(
                    roundIdx,
                    auditConfig,
                    contest.contestUA as OAContestUnderAudit,
                    cvrs,
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

private val quiet = true

fun simulateSampleSizeClcaAssorter(
    roundIdx: Int,
    auditConfig: AuditConfig,
    contestUA: ContestUnderAudit,
    cvrs: List<Cvr>,
    assertionRound: AssertionRound,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val clcaConfig = auditConfig.clcaConfig
    val cassertion = assertionRound.assertion as ClcaAssertion
    val cassorter = cassertion.cassorter
    val contest = contestUA.contest

    if (!quiet) println("simulateSampleSizeClcaAssorter ${contest.name} ${cassorter.assorter().desc()}")

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

    // optional fuzzing of the cvrs
    val isIrvFzz = (contest.isIRV() && clcaConfig.simFuzzPct != null)
    val (sampler: Sampler, bettingFn: BettingFn) = if (errorRates != null && !errorRates.areZero()) {
        if (isIrvFzz) fuzzPct = clcaConfig.simFuzzPct!!
        Pair(
            if (isIrvFzz) ClcaFuzzSampler(clcaConfig.simFuzzPct!!, cvrs, contest, cassorter)
            else ClcaSimulation(cvrs, contest, cassorter, errorRates), // TODO why cant we use this with IRV??
            AdaptiveBetting(Nc = contest.Nc, a = cassorter.noerror(), d = clcaConfig.d, errorRates = errorRates)
        )
    } else {
        // this is noerrors
        Pair(
            makeClcaNoErrorSampler(contest.id, auditConfig.hasStyles, cvrs, cassorter),
            AdaptiveBetting(Nc = contest.Nc, a = cassorter.noerror(), d = clcaConfig.d, errorRates = ClcaErrorRates(0.0, 0.0, 0.0, 0.0))
        )
    }

    // we need a permutation to get uniform distribution of errors, since some simulations put all the errors at the beginning
    sampler.reset()

    // run the simulation ntrials (=auditConfig.nsimEst) times
    val result: RunTestRepeatedResult = simulateSampleSizeBetaMart(
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

    // The result is a distribution of ntrials sampleSizes
    assertionRound.estimationResult = EstimationRoundResult(roundIdx,
        clcaConfig.strategy.name,
        fuzzPct = fuzzPct,
        startingTestStatistic = startingTestStatistic,
        startingRates = errorRates,
        estimatedDistribution = makeDeciles(result.sampleCount),
    )

    return result
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
        riskLimit = auditConfig.riskLimit,
        upperBound = upperBound,
    )

    // run the simulation ntrials (auditConfig.nsimEst) times
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
    val pollingConfig = auditConfig.pollingConfig
    val sampler = if (pollingConfig.simFuzzPct == null || pollingConfig.simFuzzPct == 0.0) {
        PollWithoutReplacement(contest.id, auditConfig.hasStyles, cvrs, assorter, allowReset=true)
    } else {
        fuzzPct = pollingConfig.simFuzzPct
        PollingFuzzSampler(pollingConfig.simFuzzPct, cvrs, contest as Contest, assorter) // TODO cant use Raire
    }

    val result = simulateSampleSizeAlphaMart(
        auditConfig,
        sampler,
        null,
        eta0 = eta0,
        upperBound = assorter.upperBound(),
        Nc = contest.Nc,
        startingTestStatistic = startingTestStatistic,
        moreParameters = moreParameters,
    )

    assertionRound.estimationResult = EstimationRoundResult(roundIdx,
        "default",
        fuzzPct = fuzzPct,
        startingTestStatistic = startingTestStatistic,
        estimatedDistribution = makeDeciles(result.sampleCount),
    )

    return result
}

// polling and oneAudit
fun simulateSampleSizeAlphaMart(
    auditConfig: AuditConfig,
    sampleFn: Sampler,
    estimFn: EstimFn?, // if null use default TruncShrinkage
    eta0: Double,  // initial estimate of mean
    upperBound: Double,
    Nc: Int,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val margin = mean2margin(eta0)

    val useEstimFn = estimFn ?: TruncShrinkage(
        N = Nc,
        upperBound = upperBound,
        d = auditConfig.pollingConfig.d,
        eta0 = eta0,
    )

    val testFn = AlphaMart(
        estimFn = useEstimFn,
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
    contestUA: OAContestUnderAudit,
    cvrs: List<Cvr>,
    assertionRound: AssertionRound,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val cassertion = assertionRound.assertion as ClcaAssertion
    val oaCassorter = cassertion.cassorter as OneAuditClcaAssorter
    val oaConfig = auditConfig.oaConfig
    var fuzzPct = 0.0

    println("simulateSampleSizeOneAuditAssorter ${contestUA.name} ${contestUA.id} ${oaCassorter.assorter().desc()} ${cvrs.size} ")

    // the sampler is specific to the assertion
    val sampler = if (oaConfig.simFuzzPct == null) {
        ClcaWithoutReplacement(contestUA.id, auditConfig.hasStyles, cvrs.zip( cvrs), oaCassorter, allowReset=true, trackStratum=false)
    } else {
        fuzzPct = oaConfig.simFuzzPct
        OneAuditFuzzSampler(oaConfig.simFuzzPct, cvrs, contestUA, oaCassorter) // TODO cant use Raire
    }
    sampler.reset()

    // the strategy effects the estimFn
    val strategy = auditConfig.oaConfig.strategy
    val eta0 = if (strategy == OneAuditStrategyType.eta0Eps)
        oaCassorter.upperBound() * (1.0 - eps)
    else
        oaCassorter.noerror()

    val estimFn = if (auditConfig.oaConfig.strategy == OneAuditStrategyType.bet99) {
        FixedEstimFn(.99 * oaCassorter.upperBound())
    } else {
        TruncShrinkage(
            N = contestUA.Nc,
            withoutReplacement = true,
            upperBound = oaCassorter.upperBound(),
            d = auditConfig.pollingConfig.d,
            eta0 = eta0,
        )
    }

    val result = simulateSampleSizeAlphaMart(
        auditConfig,
        sampler,
        estimFn = estimFn,
        eta0 = eta0,
        upperBound = oaCassorter.upperBound(),
        Nc = contestUA.Nc,
        startingTestStatistic = startingTestStatistic,
        moreParameters
    )

    assertionRound.estimationResult = EstimationRoundResult(roundIdx,
        oaConfig.strategy.name,
        fuzzPct = fuzzPct,
        startingTestStatistic = startingTestStatistic,
        estimatedDistribution = makeDeciles(result.sampleCount),
    )

    println("  finish ${contestUA.id} ${oaCassorter.assorter().desc()} ${makeDeciles(result.sampleCount)} ")
    return result
}
