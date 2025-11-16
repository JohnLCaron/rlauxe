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

    logger.info { "Run ${auditContestTasks.size} tasks for auditor ${auditor.javaClass.name} " }

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

                val testH0Result = auditor.run(config, contest.contestUA, assertionRound, sampler, roundIdx)
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
        contestUA: ContestUnderAudit,
        assertionRound: AssertionRound,
        sampler: Sampler,
        roundIdx: Int,
    ): TestH0Result
}

class ClcaAssertionAuditor(val quiet: Boolean = true): ClcaAssertionAuditorIF {

    override fun run(
        config: AuditConfig,
        contestUA: ContestUnderAudit,
        assertionRound: AssertionRound,
        sampler: Sampler,
        roundIdx: Int,
    ): TestH0Result {
        val contest = contestUA.contest
        val cassertion = assertionRound.assertion as ClcaAssertion
        val cassorter = cassertion.cassorter
        val clcaConfig = config.clcaConfig

        //// same as estimateClcaAssertionRound
        var errorRates: ClcaErrorRates = when {
            // Subsequent rounds, always use measured rates.
            (assertionRound.prevAuditResult != null) -> {
                // TODO should be average of previous rates?
                assertionRound.prevAuditResult!!.measuredRates!!
            }
            (clcaConfig.strategy == ClcaStrategyType.fuzzPct)  -> {
                ClcaErrorTable.getErrorRates(contest.ncandidates, clcaConfig.simFuzzPct) // TODO do better
            }
            (clcaConfig.strategy == ClcaStrategyType.apriori) -> {
                clcaConfig.errorRates!!
            }
            else -> {
                ClcaErrorRates.Zero
            }
        }
        if (errorRates.p2o < contest.phantomRate())
            errorRates = errorRates.copy( p2o = contest.phantomRate())

        val bettingFn: BettingFn = if (clcaConfig.strategy == ClcaStrategyType.oracle) {
            OracleComparison(a = cassorter.noerror(), errorRates = errorRates)
        }  else if (clcaConfig.strategy == ClcaStrategyType.optimalComparison) {
            OptimalComparisonNoP1(N = contestUA.Nb, withoutReplacement = true, upperBound = cassorter.noerror(), p2 = errorRates.p2o)
        } else {
            AdaptiveBetting(N = contestUA.Nb, a = cassorter.noerror(), d = clcaConfig.d, errorRates = errorRates)
        }

        val testFn = BettingMart(
            bettingFn = bettingFn,
            N = contestUA.Nb,
            noerror = cassorter.noerror(),
            upperBound = cassorter.upperBound(),
            riskLimit = config.riskLimit,
            withoutReplacement = true
        )
        // testFn.setDebuggingSequences()

        val terminateOnNullReject = config.auditSampleLimit == null
        val testH0Result = testFn.testH0(sampler.maxSamples(), terminateOnNullReject = terminateOnNullReject) { sampler.sample() }

        assertionRound.auditResult = AuditRoundResult(
            roundIdx,
            nmvrs = sampler.maxSamples(),
            maxBallotIndexUsed = sampler.maxSampleIndexUsed(), // TODO only for audit, not estimation I think
            pvalue = testH0Result.pvalueLast,
            samplesUsed = testH0Result.sampleCount,
            status = testH0Result.status,
            measuredMean = testH0Result.tracker.mean(),
            startingRates = errorRates,
            measuredRates = testH0Result.tracker.errorRates(),
        )

        if (!quiet) {
            logger.debug{" (${contest.id}) ${contest.name} ${cassertion} ${assertionRound.auditResult}"}
        }
        return testH0Result
    }
}