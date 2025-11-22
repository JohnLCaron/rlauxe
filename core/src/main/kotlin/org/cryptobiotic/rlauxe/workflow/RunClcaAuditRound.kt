package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.ClcaWithoutReplacement
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskRunnerG
import org.cryptobiotic.rlauxe.estimate.Sampler

private val logger = KotlinLogging.logger("ClcaAudit")

// run all contests and assertions for one round with the given auditor
fun runClcaAuditRound(
    config: AuditConfig,
    contests: List<ContestRound>,
    mvrManager: MvrManagerClcaIF,
    roundIdx: Int,
    auditor: ClcaAssertionAuditorIF,
): Boolean {
    val cvrPairs = mvrManager.makeCvrPairsForRound()

    // parallelize over contests
    val contestsNotDone = contests.filter{ !it.done }
    val auditContestTasks = mutableListOf<RunContestTask>()
    contestsNotDone.forEach { contest ->
        auditContestTasks.add(RunContestTask(config, contest, cvrPairs, auditor, roundIdx))
    }

    // logger.debug { "runClcaAuditRound ($roundIdx) ${auditContestTasks.size} tasks for auditor ${auditor.javaClass.simpleName} " }
    // println("---runClcaAuditRound running ${auditContestTasks.size} tasks")

    val complete: List<Boolean> = ConcurrentTaskRunnerG<Boolean>().run(auditContestTasks)
    return if (complete.isEmpty()) true else complete.reduce { acc, b -> acc && b }
}

class RunContestTask(
    val config: AuditConfig,
    val contest: ContestRound,
    val cvrPairs: List<Pair<Cvr, Cvr>>,
    val auditor: ClcaAssertionAuditorIF,
    val roundIdx: Int): ConcurrentTaskG<Boolean> {

    override fun name() = "RunContestTask for ${contest.contestUA.name} round $roundIdx nassertions ${contest.assertionRounds.size}"

    override fun run(): Boolean {
        val contestAssertionStatus = mutableListOf<TestH0Status>()
        contest.assertionRounds.forEach { assertionRound ->
            if (!assertionRound.status.complete) {
                val cassertion = assertionRound.assertion as ClcaAssertion
                val cassorter = cassertion.cassorter
                val sampler =
                    ClcaWithoutReplacement(contest.id, cvrPairs, cassorter, allowReset = false)

                val testH0Result = auditor.run(config, contest, assertionRound, sampler, roundIdx)
                assertionRound.status = testH0Result.status
                if (testH0Result.status.complete) assertionRound.round = roundIdx
            }
            contestAssertionStatus.add(assertionRound.status)
        }
        contest.done = contestAssertionStatus.all { it.complete }
        contest.status = contestAssertionStatus.minBy { it.rank } // use lowest rank status.
        return contest.done
    }
}

// abstraction so ClcaAudit can be used for OneAudit
fun interface ClcaAssertionAuditorIF {
    fun run(
        config: AuditConfig,
        contestRound: ContestRound,
        assertionRound: AssertionRound,
        sampler: Sampler,
        roundIdx: Int,
    ): TestH0Result
}

class ClcaAssertionAuditor(val quiet: Boolean = true): ClcaAssertionAuditorIF {

    override fun run(
        config: AuditConfig,
        contestRound: ContestRound,
        assertionRound: AssertionRound,
        sampler: Sampler,
        roundIdx: Int,
    ): TestH0Result {
        val contestUA = contestRound.contestUA
        val contest = contestUA.contest
        val cassertion = assertionRound.assertion as ClcaAssertion
        val cassorter = cassertion.cassorter
        val clcaConfig = config.clcaConfig

        // Subsequent rounds, always use measured rates.
        val prevRounds: ClcaErrorCounts = assertionRound.accumulatedErrorCounts(contestRound)
        prevRounds.setPhantomRate(contest.phantomRate()) // the minimum p1o is always the phantom rate.

        //  apriori: pass in apriori errorRates for first round.
        //  fuzzPct: ClcaErrorTable.getErrorRates(contest.ncandidates, clcaConfig.simFuzzPct) for first round.
        //  oracle: use actual measured error rates for first round. (violates martingale condition)

        val bettingFn: BettingFn = if (clcaConfig.strategy == ClcaStrategyType.generalAdaptive) {
            GeneralAdaptiveBetting(N = contestUA.Nb, prevRounds = prevRounds, d = clcaConfig.d,)

        } else if (clcaConfig.strategy == ClcaStrategyType.apriori) {
            AdaptiveBetting(N = contestUA.Nb, a = cassorter.noerror(), d = clcaConfig.d, errorRates=clcaConfig.errorRates!!) // just stick with them

        } else if (clcaConfig.strategy == ClcaStrategyType.fuzzPct) {
            val errorsP = ClcaErrorTable.getErrorRates(contest.ncandidates, clcaConfig.fuzzPct) // TODO do better
            AdaptiveBetting(N = contestUA.Nb, a = cassorter.noerror(), d = clcaConfig.d, errorRates=errorsP) // just stick with them

        // }  else if (clcaConfig.strategy == ClcaStrategyType.optimalComparison) {
        //    OptimalComparisonNoP1(N = contestUA.Nb, withoutReplacement = true, upperBound = cassorter.noerror(), p2 = errorRatesP.p2o)

        // } else if (clcaConfig.strategy == ClcaStrategyType.oracle) {
        //    OracleComparison(a = cassorter.noerror(), errorRates = errorRatesP)  // TODO where do these come from ??
            
        } else {
            throw RuntimeException("unsupported strategy ${clcaConfig.strategy}")
        }

        val tracker = if (clcaConfig.strategy == ClcaStrategyType.generalAdaptive) ClcaErrorTracker(cassorter.noerror())
            else PluralityErrorTracker(cassorter.noerror())

        val testFn = BettingMart(
            bettingFn = bettingFn,
            N = contestUA.Nb,
            tracker = tracker,
            sampleUpperBound = cassorter.upperBound(),
            riskLimit = config.riskLimit,
            withoutReplacement = true
        )
        // testFn.setDebuggingSequences()

        val terminateOnNullReject = config.auditSampleLimit == null
        val testH0Result = testFn.testH0(sampler.maxSamples(), terminateOnNullReject = terminateOnNullReject) { sampler.sample() }

        val measuredCounts = if (testH0Result.tracker is ClcaErrorRatesIF) testH0Result.tracker.errorCounts() else null
        assertionRound.auditResult = AuditRoundResult(
            roundIdx,
            nmvrs = sampler.maxSamples(),
            maxBallotIndexUsed = sampler.maxSampleIndexUsed(), // TODO only for audit, not estimation I think
            pvalue = testH0Result.pvalueLast,
            samplesUsed = testH0Result.sampleCount,
            status = testH0Result.status,
            measuredMean = testH0Result.tracker.mean(),
            startingRates = prevRounds.errorRates(),
            measuredCounts = measuredCounts,
        )

        if (!quiet) {
            logger.debug{" (${contest.id}) ${contest.name} ${cassertion} ${assertionRound.auditResult}"}
        }
        return testH0Result
    }
}