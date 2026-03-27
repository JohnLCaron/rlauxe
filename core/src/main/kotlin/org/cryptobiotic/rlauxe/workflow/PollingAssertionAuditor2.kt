package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.AlphaMart
import org.cryptobiotic.rlauxe.betting.PollingSamplerTracker
import org.cryptobiotic.rlauxe.betting.SamplerTracker
import org.cryptobiotic.rlauxe.betting.TestH0Result
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.betting.TruncShrinkage
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*

// run all contests and assertions for one round with the given auditor.
// return isComplete
fun runPollingAuditRound(
    config: Config,
    auditRound: AuditRound,
    mvrManager: MvrManager,
    roundIdx: Int,
    onlyTask: OnlyTask? = null,
): Boolean {
    val mvrCvrs = mvrManager.makeMvrCardPairsForRound(roundIdx)

    // parallelize over contests
    val contestsNotDone = auditRound.contestRounds.filter{ !it.done }
    val auditContestTasks = mutableListOf<RunPollingContestTask>()
    contestsNotDone.forEach { contest ->
        auditContestTasks.add( RunPollingContestTask(config, contest, mvrCvrs, roundIdx, onlyTask) )
    }
    val complete: List<Boolean> = ConcurrentTaskRunner<Boolean>().run(auditContestTasks)

    // given the cvrPairs, and each ContestRound's maxSamplesUsed, count the cvrs that were not used
    val contestCounts = mutableMapOf<Int, Int>()
    var countUsed = 0
    var countUnused = 0
    mvrCvrs.forEach { mvrCardPair ->
        val card = mvrCardPair.second
        var wasUsed = false
        contestsNotDone.forEach { contest ->
            val count = contestCounts.getOrPut(contest.id) { 0 }
            if (card.hasContest(contest.id)) {
                if (count < contest.maxSamplesUsed()) wasUsed = true
                contestCounts[contest.id] = count + 1
            }
        }
        if (wasUsed) countUsed++ else countUnused++
    }
    auditRound.mvrsUnused =  countUnused
    auditRound.mvrsUsed =  countUsed

    return if (complete.isEmpty()) true else complete.reduce { acc, b -> acc && b }
}

class RunPollingContestTask(
    val config: Config,
    val contestRound: ContestRound,
    val mvrCvrs: List<Pair<CvrIF, AuditableCard>>, // Pair(mvr, card)
    val roundIdx: Int,
    val onlyTask: OnlyTask? = null,
): ConcurrentTask<Boolean> {

    override fun name() = "RunPollingContestTask for ${contestRound.contestUA.name} round $roundIdx nassertions ${contestRound.assertionRounds.size}"

    override fun run(): Boolean {
        val contestAssertionStatus = mutableListOf<TestH0Status>()

        contestRound.assertionRounds.forEach { assertionRound ->
            val taskName = "${contestRound.contestUA.id}-${assertionRound.assertion.assorter.shortName()}"
            if (onlyTask == null || onlyTask.taskName == taskName) {
                if (!assertionRound.status.complete) {
                    val assertion = assertionRound.assertion
                    val assorter = assertion.assorter
                    val sampler = PollingSamplerTracker.withMaxSample(
                        contestRound.id,
                        assorter,
                        mvrCvrs,
                        contestRound.maxSampleAllowed
                    )

                    val testH0Result =
                        auditPollingAssertion(config, contestRound.contestUA, assertionRound, sampler, roundIdx)
                    assertionRound.status = testH0Result.status
                    if (testH0Result.status.complete) assertionRound.roundProved = roundIdx
                }
            }
            contestAssertionStatus.add(assertionRound.status)
        }
        if (contestAssertionStatus.isNotEmpty()) {
            contestRound.done = contestAssertionStatus.all { it.complete }
            contestRound.status = contestAssertionStatus.minBy { it.rank } // use lowest rank status.
        }
        return contestRound.done
    }
}

fun auditPollingAssertion(
    config: Config,
    contestUA: ContestWithAssertions,
    assertionRound: AssertionRound,
    samplerTracker: SamplerTracker,
    roundIdx: Int,
): TestH0Result {
    val assertion = assertionRound.assertion
    val assorter = assertion.assorter

    val eta0 = margin2mean(assorter.dilutedMargin())

    val estimFn = TruncShrinkage(
        N = contestUA.Npop,
        withoutReplacement = true,
        upperBound = assorter.upperBound(),
        d = config.round.pollingConfig!!.d,
        eta0 = eta0,
    )

    val testFn = AlphaMart(
        estimFn = estimFn,
        N = contestUA.Npop,
        tracker = samplerTracker,
        withoutReplacement = true,
        riskLimit = config.riskLimit,
        upperBound = assorter.upperBound(),
    )
    testFn.setDebuggingSequences()

    val terminateOnNullReject = !config.creation.isRiskMeasuringAudit()
    val testH0Result = testFn.testH0(samplerTracker.nmvrs(), terminateOnNullReject=terminateOnNullReject) { samplerTracker.sample() }

    assertionRound.auditResult = AuditRoundResult(
        roundIdx,
        nmvrs = samplerTracker.nmvrs(),
        plast = testH0Result.pvalueLast,
        pmin = testH0Result.pvalueMin,
        samplesUsed = testH0Result.sampleCount,
        status = testH0Result.status,
        clcaErrorTracker = null,
    )

    return testH0Result
}
