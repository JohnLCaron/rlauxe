package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.OAClcaAssorter
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.raire.RaireContest
import org.cryptobiotic.rlauxe.raire.simulateRaireContest
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.makeDeciles
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.math.min

private val debug = false
private val debugErrorRates = false
private val debugSampleDist = false
private val debugSizeNudge = true

////////////////////////////////////////////////////////////////////////////////////////////
//// Comparison, Polling, OneAudit.

fun estimateSampleSizes(
    auditConfig: AuditConfig,
    auditRound: AuditRound,
    showTasks: Boolean = false,
    nthreads: Int = 30,
): List<RunTestRepeatedResult> {

    // create the estimation tasks
    val tasks = mutableListOf<SimulateSampleSizeTask>()
    auditRound.contestRounds.filter { !it.done }.forEach { contest ->
        tasks.addAll(makeEstimationTasks(auditConfig, contest, auditRound.roundIdx))
    }
    // run tasks concurrently
    val estResults: List<EstimationResult> = ConcurrentTaskRunnerG<EstimationResult>(showTasks).run(tasks, nthreads)

    // put results into assertionRounds
    estResults.forEach { estResult ->
        val task = estResult.task
        val result = estResult.repeatedResult

        val estNewSamples = result.findQuantile(auditConfig.quantile)
        task.assertionRound.estNewSampleSize = estNewSamples
        task.assertionRound.estSampleSize = min(estNewSamples + task.prevSampleSize, task.contest.Nc)

        if (debug) println(result.showSampleDist(estResult.task.contest.id))
        if (result.avgSamplesNeeded() < 10) {
            println(" ** avgSamplesNeeded ${result.avgSamplesNeeded()} task=${task.name()}")
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
    contest: ContestRound,
    roundIdx: Int,
    moreParameters: Map<String, Double> = emptyMap(),
): List<SimulateSampleSizeTask> {
    val tasks = mutableListOf<SimulateSampleSizeTask>()

    contest.assertionRounds.map { assertionRound ->
        if (!assertionRound.status.complete) {
            var prevSampleSize = 0
            var startingTestStatistic = 1.0
            if (roundIdx > 1) {
                val prevAuditResult = assertionRound.prevAuditResult!!
                if (prevAuditResult.samplesUsed == contest.Nc) {   // TODO or pct of ?
                    println("***LimitReached $contest")
                    contest.done = true
                    contest.status = TestH0Status.LimitReached
                }
                // start where the audit left off
                prevSampleSize = prevAuditResult.samplesUsed
                startingTestStatistic = 1.0 / prevAuditResult.pvalue
            }

            if (!contest.done) {
                tasks.add(
                    SimulateSampleSizeTask(
                        roundIdx,
                        auditConfig,
                        contest,
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
class SimulateSampleSizeTask(
    val roundIdx: Int,
    val auditConfig: AuditConfig,
    val contest: ContestRound,
    val assertionRound: AssertionRound,
    val startingTestStatistic: Double,
    val prevSampleSize: Int,
    val moreParameters: Map<String, Double> = emptyMap(),
) : ConcurrentTaskG<EstimationResult> {

    override fun name() = "task ${contest.name} ${assertionRound.assertion.assorter.desc()} ${auditConfig.strategy()}}"

    // each time the task is run, the contest and cvr simulation is done once, then ran ntrials=auditConfig.nsimEst times,
    // each time the cvrs are randomly permuted. The result is a distribution of ntrials sampleSizes.
    override fun run(): EstimationResult {
        val result: RunTestRepeatedResult = when (auditConfig.auditType) {
            AuditType.CLCA ->
                simulateSampleSizeClcaAssorter(
                    roundIdx,
                    auditConfig,
                    contest.contestUA.contest,
                    assertionRound,
                    startingTestStatistic
                )
            AuditType.POLLING ->
                simulateSampleSizePollingAssorter(
                    roundIdx,
                    auditConfig,
                    contest.contestUA.contest as Contest, // TODO COntestIF Raire??
                    assertionRound,
                    startingTestStatistic,
                    moreParameters=moreParameters,
                )
            AuditType.ONEAUDIT ->
                simulateSampleSizeOneAuditAssorter(
                    roundIdx,
                    auditConfig,
                    contest.contestUA as OAContestUnderAudit,
                    assertionRound,
                    startingTestStatistic,
                    moreParameters=moreParameters,
                )
        }
        return EstimationResult(this, result)
    }
}

data class EstimationResult(
    val task: SimulateSampleSizeTask,
    val repeatedResult: RunTestRepeatedResult,
)

/////////////////////////////////////////////////////////////////////////////////////////////////
//// Clca, including with IRV

fun simulateSampleSizeClcaAssorter(
    roundIdx: Int,
    auditConfig: AuditConfig,
    contest: ContestIF,
    assertionRound: AssertionRound,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val clcaConfig = auditConfig.clcaConfig
    val cassertion = assertionRound.assertion as ClcaAssertion
    val cassorter = cassertion.cassorter

    // Simulation of Contest that reflects the exact votes and Nc, along with undervotes and phantoms, as specified in Contest.
    val cvrs =  if (contest.isIRV()) {
        simulateRaireContest(contest as RaireContest)
    } else {
        val contestSim = ContestSimulation(contest as Contest)
        contestSim.makeCvrs()
    }

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
    val (sampler: Sampler, bettingFn: BettingFn) = if (errorRates != null && !errorRates.areZero()) {
        val irvFuzz = (contest.isIRV() && clcaConfig.simFuzzPct != null)
        if (irvFuzz) fuzzPct = clcaConfig.simFuzzPct!! // TODO
        Pair(
            if (irvFuzz) ClcaFuzzSampler(clcaConfig.simFuzzPct!!, cvrs, contest, cassorter)
            else ClcaSimulation(cvrs, contest, cassorter, errorRates), // TODO why cant we use this with IRV??
            AdaptiveComparison(Nc = contest.Nc, a = cassorter.noerror(), d = clcaConfig.d, errorRates = errorRates)
        )
    } else {
        // this is noerrors
        Pair(
            makeClcaNoErrorSampler(contest.id, cvrs, cassorter),
            AdaptiveComparison(Nc = contest.Nc, a = cassorter.noerror(), d = clcaConfig.d, errorRates = ClcaErrorRates(0.0, 0.0, 0.0, 0.0))
        )
    }

    // we need a permutation to get uniform distribution of errors, since some simulations puts all the errors at the beginning
    sampler.reset()

    // run the simulation ntrials (auditConfig.nsimEst) times
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
    assertionRound: AssertionRound,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val assorter = assertionRound.assertion.assorter
    val margin = assorter.reportedMargin()

    // Simulation of multicandidate Contest that reflects the exact votes and Nc, along with undervotes and phantoms, as specified in Contest.
    // TODO maximum cvrs for estimation
    // TODO what about supermajority?
    val simContest = /* if (contest.isIRV()) ContestIrvSimulation(contest as Contest) else */ ContestSimulation(contest as Contest)
    val cvrs = simContest.makeCvrs() // fake Cvrs with reported margin,

    // optional fuzzing of the cvrs
    var fuzzPct = 0.0
    val pollingConfig = auditConfig.pollingConfig
    val sampler = if (pollingConfig.simFuzzPct == null || pollingConfig.simFuzzPct == 0.0) {
        PollWithoutReplacement(contest.id, cvrs, assorter, allowReset=true)
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
    margin: Double,
    upperBound: Double,
    Nc: Int,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val eta0 = margin2mean(margin)
    val c = (eta0 - 0.5) / 2 // TODO should this be sometimes different? TruncShrinkage with AlphaMart

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
    contestUA: OAContestUnderAudit,
    assertionRound: AssertionRound,
    startingTestStatistic: Double = 1.0,
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val cassertion = assertionRound.assertion as ClcaAssertion
    val cassorter = cassertion.cassorter as OAClcaAssorter
    val oaConfig = auditConfig.oaConfig
    var fuzzPct = 0.0

    val cvrs = contestUA.contestOA.makeTestCvrs()

    // TODO is this right, no special processing for the "hasCvr" strata?
    val sampler = if (oaConfig.simFuzzPct == null) {
        ClcaWithoutReplacement(contestUA.id, cvrs.zip( cvrs), cassorter, allowReset=true, trackStratum=false)
    } else {
        fuzzPct = oaConfig.simFuzzPct
        OneAuditFuzzSampler(oaConfig.simFuzzPct, cvrs, contestUA, cassorter) // TODO cant use Raire
    }

    sampler.reset()

    val result = simulateSampleSizeAlphaMart(
        auditConfig,
        sampler,
        mean2margin(cassorter.meanAssort()),
        cassorter.upperBound(),
        contestUA.Nc,
        startingTestStatistic,
        moreParameters
    )

    assertionRound.estimationResult = EstimationRoundResult(roundIdx,
        oaConfig.strategy.name,
        fuzzPct = fuzzPct,
        startingTestStatistic = startingTestStatistic,
        estimatedDistribution = makeDeciles(result.sampleCount),
    )

    return result
}