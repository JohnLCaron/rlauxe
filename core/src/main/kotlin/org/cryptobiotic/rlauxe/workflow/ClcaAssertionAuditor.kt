package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskRunnerG

private val logger = KotlinLogging.logger("ClcaAudit")

// run all contests and assertions for one round with the given auditor
fun runClcaAuditRound(
    config: AuditConfig,
    contests: List<ContestRound>,
    mvrManager: MvrManager,
    roundIdx: Int,
    auditor: ClcaAssertionAuditorIF,
): Boolean {
    val cvrPairs = mvrManager.makeMvrCardPairsForRound(roundIdx)

    // parallelize over contests
    val contestsNotDone = contests.filter{ !it.done }
    val auditContestTasks = mutableListOf<RunClcaContestTask>()
    contestsNotDone.forEach { contest ->
        auditContestTasks.add(RunClcaContestTask(config, contest, cvrPairs, auditor, roundIdx))
    }

    // logger.debug { "runClcaAuditRound ($roundIdx) ${auditContestTasks.size} tasks for auditor ${auditor.javaClass.simpleName} " }
    // println("---runClcaAuditRound running ${auditContestTasks.size} tasks")

    val complete: List<Boolean> = ConcurrentTaskRunnerG<Boolean>().run(auditContestTasks)
    return if (complete.isEmpty()) true else complete.reduce { acc, b -> acc && b }
}

class RunClcaContestTask(
    val config: AuditConfig,
    val contest: ContestRound,
    val cvrPairs: List<Pair<CardIF, CardIF>>, // Pair(mvr, card)
    val auditor: ClcaAssertionAuditorIF,
    val roundIdx: Int): ConcurrentTaskG<Boolean> {

    override fun name() = "RunContestTask for ${contest.contestUA.name} round $roundIdx nassertions ${contest.assertionRounds.size}"

    override fun run(): Boolean {
        val contestAssertionStatus = mutableListOf<TestH0Status>()
        contest.assertionRounds.forEach { assertionRound ->
            if (!assertionRound.status.complete) {
                val cassertion = assertionRound.assertion as ClcaAssertion
                val cassorter = cassertion.cassorter
                val sampler = ClcaSampling(contest.id, cvrPairs, cassorter, allowReset = false)

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
        sampling: Sampling,
        roundIdx: Int,
    ): TestH0Result
}

class ClcaAssertionAuditor(val quiet: Boolean = true): ClcaAssertionAuditorIF {

    override fun run(
        config: AuditConfig,
        contestRound: ContestRound,
        assertionRound: AssertionRound,
        sampling: Sampling,
        roundIdx: Int,
    ): TestH0Result {
        val contestUA = contestRound.contestUA
        val contest = contestUA.contest
        val cassertion = assertionRound.assertion as ClcaAssertion
        val cassorter = cassertion.cassorter
        val clcaConfig = config.clcaConfig

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

        // TODO put tracker back on bettingMart I think
        val testFn = BettingMart(
            bettingFn = bettingFn,
            N = contestUA.Npop,
            sampleUpperBound = cassorter.upperBound(),
            riskLimit = config.riskLimit,
            withoutReplacement = true
        )

        // TODO make optional
        val sequences = testFn.setDebuggingSequences()
        val tracker = ClcaErrorTracker(cassorter.noerror(), cassorter.assorter.upperBound(), sequences) // track pool data; something better to do?

        val terminateOnNullReject = config.auditSampleLimit == null
        // TODO remove tracker from testH0
        val testH0Result = testFn.testH0(sampling.maxSamples(), terminateOnNullReject = terminateOnNullReject, tracker=tracker) { sampling.sample() }

        val measuredCounts: ClcaErrorCounts? = if (testH0Result.tracker is ClcaErrorTracker) testH0Result.tracker.measuredErrorCounts() else null
        assertionRound.auditResult = AuditRoundResult(
            roundIdx,
            nmvrs = sampling.maxSamples(),
            maxBallotIndexUsed = sampling.maxSampleIndexUsed(),
            pvalue = testH0Result.pvalueLast,
            samplesUsed = testH0Result.sampleCount,
            status = testH0Result.status,
            measuredMean = testH0Result.tracker.mean(),
            startingRates = prevRounds,
            measuredCounts = measuredCounts,
        )

        if (!quiet) {
            logger.debug{" (${contest.id}) ${contest.name} ${cassertion} ${assertionRound.auditResult}"}
        }
        return testH0Result
    }
}