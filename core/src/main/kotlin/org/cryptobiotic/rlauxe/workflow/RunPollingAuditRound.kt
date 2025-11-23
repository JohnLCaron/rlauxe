package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*

private val logger = KotlinLogging.logger("PollingAudit")

// TODO parallelize over contests; see runClcaAuditRound
fun runPollingAuditRound(
    config: AuditConfig,
    contests: List<ContestRound>,
    mvrManager: MvrManager,
    roundIdx: Int,
    quiet: Boolean = true
): Boolean {
    val pairs = mvrManager.makeMvrCardPairsForRound()

    val contestsNotDone = contests.filter { !it.done }
    if (contestsNotDone.isEmpty()) {
        return true
    }

    if (!quiet) logger.debug{"runAudit round $roundIdx"}
    var allDone = true
    contestsNotDone.forEach { contest ->
        val contestAssertionStatus = mutableListOf<TestH0Status>()
        contest.assertionRounds.forEach { assertionRound ->
            if (!assertionRound.status.complete) {
                val assertion = assertionRound.assertion
                val assorter = assertion.assorter
                val sampler = PollWithoutReplacement(contest.id, pairs, assorter, allowReset = false)

                val testH0Result = auditPollingAssertion(config, contest.contestUA, assertionRound, sampler, roundIdx, quiet)
                assertionRound.status = testH0Result.status
                if (testH0Result.status.complete) assertionRound.round = roundIdx
            }
            contestAssertionStatus.add(assertionRound.status)
        }
        contest.done = contestAssertionStatus.all { it.complete }
        contest.status = contestAssertionStatus.minBy { it.rank } // use lowest rank status.
        allDone = allDone && contest.done
    }
    return allDone
}

fun auditPollingAssertion(
    config: AuditConfig,
    contestUA: ContestUnderAudit,
    assertionRound: AssertionRound,
    sampling: Sampling,
    roundIdx: Int,
    quiet: Boolean = false
): TestH0Result {
    val assertion = assertionRound.assertion
    val assorter = assertion.assorter

    val eta0 = margin2mean(assorter.reportedMargin())

    val estimFn = TruncShrinkage(
        N = contestUA.Nb,
        withoutReplacement = true,
        upperBound = assorter.upperBound(),
        d = config.pollingConfig.d,
        eta0 = eta0,
    )
    val testFn = AlphaMart(
        estimFn = estimFn,
        N = contestUA.Nb,
        withoutReplacement = true,
        riskLimit = config.riskLimit,
        upperBound = assorter.upperBound(),
    )

    val testH0Result = testFn.testH0(sampling.maxSamples(), terminateOnNullReject=true) { sampling.sample() }

    assertionRound.auditResult = AuditRoundResult(roundIdx,
        nmvrs = sampling.nmvrs(),
        maxBallotIndexUsed = sampling.maxSampleIndexUsed(),
        pvalue = testH0Result.pvalueLast,
        samplesUsed = testH0Result.sampleCount,
        status = testH0Result.status,
        measuredMean = testH0Result.tracker.mean(),
    )

    if (!quiet) logger.debug{" ${contestUA.name} ${assertionRound.auditResult}"}
    return testH0Result
}
