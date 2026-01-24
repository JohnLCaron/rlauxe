package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.BettingMart
import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.betting.TestH0Result
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskRunnerG

private val logger = KotlinLogging.logger("ClcaAudit")

// run all contests and assertions for one round with the given auditor.
// return isComplete
fun runClcaAuditRound(
    config: AuditConfig,
    auditRound: AuditRound,
    mvrManager: MvrManager,
    roundIdx: Int,
    auditor: ClcaAssertionAuditorIF,
): Boolean {
    val cvrPairs = mvrManager.makeMvrCardPairsForRound(roundIdx)

    // parallelize over contests
    val contestsNotDone = auditRound.contestRounds.filter{ !it.done }
    val auditContestTasks = mutableListOf<RunClcaContestTask>()
    contestsNotDone.forEach { contest ->
        auditContestTasks.add(RunClcaContestTask(config, contest, cvrPairs, auditor, roundIdx))
    }

    // run all tasks
    // logger.debug { "runClcaAuditRound ($roundIdx) ${auditContestTasks.size} tasks for auditor ${auditor.javaClass.simpleName} " }
    // println("---runClcaAuditRound running ${auditContestTasks.size} tasks")
    val complete: List<Boolean> = ConcurrentTaskRunnerG<Boolean>().run(auditContestTasks)

    // given the cvrPairs, and each ContestRound's maxSampleIndexUsed, count the cvrs that were not used
    val maxIndex = contestsNotDone.associate { it.id to it.maxSampleIndexUsed() }
    var countUnused = 0
    cvrPairs.forEachIndexed { idx, mvrCardPair ->
        val card = mvrCardPair.second
        var wasUsed = false
        contestsNotDone.forEach { contest ->
            if (card.hasContest(contest.id) && idx < maxIndex[contest.id]!!) wasUsed = true
        }
        if (!wasUsed) countUnused++
    }
    auditRound.samplesNotUsed =  countUnused

    return if (complete.isEmpty()) true else complete.reduce { acc, b -> acc && b }
}

class RunClcaContestTask(
    val config: AuditConfig,
    val contest: ContestRound,
    val cvrPairs: List<Pair<CvrIF, AuditableCard>>, // Pair(mvr, card)
    val auditor: ClcaAssertionAuditorIF,
    val roundIdx: Int): ConcurrentTaskG<Boolean> {

    override fun name() = "RunContestTask for ${contest.contestUA.name} round $roundIdx nassertions ${contest.assertionRounds.size}"

    override fun run(): Boolean {
        val contestAssertionStatus = mutableListOf<TestH0Status>()
        contest.assertionRounds.forEach { assertionRound ->
            if (!assertionRound.status.complete) {
                val cassertion = assertionRound.assertion as ClcaAssertion
                val cassorter = cassertion.cassorter
                val sampler = ClcaSampler(contest.id, cvrPairs.size, cvrPairs, cassorter, allowReset = false)
                // println("contest ${contest.id} maxSampleIndex ${contest.maxSampleIndex} maxSamples ${sampler.maxSamples()} ")

                val testH0Result = auditor.run(config, contest, assertionRound, sampler, roundIdx)
                assertionRound.status = testH0Result.status
                if (testH0Result.status.complete) assertionRound.roundProved = roundIdx
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
        sampling: Sampler,
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

        val bettingFn = // if (clcaConfig.strategy == ClcaStrategyType.generalAdaptive) {
            GeneralAdaptiveBetting(
                contestUA.Npop,
                startingErrors = ClcaErrorCounts.empty(cassorter.noerror(), cassorter.assorter.upperBound()), // TODO why not use tracker ??
                contest.Nphantoms(),
                oaAssortRates = null,
                d = clcaConfig.d,
                maxRisk = clcaConfig.maxRisk)

        val tracker = ClcaErrorTracker(
            cassorter.noerror(),
            cassorter.assorter.upperBound(),
        )

        val testFn = BettingMart(
            bettingFn = bettingFn,
            N = contestUA.Npop,
            sampleUpperBound = cassorter.upperBound(),
            riskLimit = config.riskLimit,
            withoutReplacement = true,
            tracker = tracker
        )
        // TODO make optional
        tracker.setDebuggingSequences(testFn.setDebuggingSequences())

        val terminateOnNullReject = config.auditSampleLimit == null
        // TODO remove tracker from testH0
        val testH0Result = testFn.testH0(sampler.maxSamples(), terminateOnNullReject = terminateOnNullReject) { sampler.sample() }

        val measuredCounts: ClcaErrorCounts? = if (testH0Result.tracker is ClcaErrorTracker) testH0Result.tracker.measuredClcaErrorCounts() else null
        assertionRound.auditResult = AuditRoundResult(
            roundIdx,
            nmvrs = sampler.maxSamples(),
            maxSampleIndexUsed = sampler.maxSampleIndexUsed(),
            plast = testH0Result.pvalueLast,
            pmin = testH0Result.pvalueMin,
            samplesUsed = testH0Result.sampleCount,
            status = testH0Result.status,
            measuredCounts = measuredCounts,
        )

        if (!quiet) {
            logger.debug{" (${contest.id}) ${contest.name} ${cassertion} ${assertionRound.auditResult}"}
        }
        return testH0Result
    }
}