package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*

private val logger = KotlinLogging.logger("PollingAudit")

// TODO parallelize over contests
fun runPollingAuditRound(
    auditConfig: AuditConfig,
    contests: List<ContestRound>,
    mvrManager: MvrManagerPollingIF,
    roundIdx: Int,
    quiet: Boolean = true
): Boolean {
    val mvrs = mvrManager.makeMvrsForRound() // same over all contests!

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
                val sampler = PollWithoutReplacement(contest.id, auditConfig.hasStyles, mvrs, assorter, allowReset=false)

                val testH0Result = auditPollingAssertion(auditConfig, contest.contestUA.contest, assertionRound, sampler, roundIdx, quiet)
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
    auditConfig: AuditConfig,
    contest: ContestIF,
    assertionRound: AssertionRound,
    sampler: Sampler,
    roundIdx: Int,
    quiet: Boolean = false
): TestH0Result {
    val assertion = assertionRound.assertion
    val assorter = assertion.assorter

    val eta0 = margin2mean(assorter.reportedMargin())

    val estimFn = TruncShrinkage(
        N = contest.Nc(),
        withoutReplacement = true,
        upperBound = assorter.upperBound(),
        d = auditConfig.pollingConfig.d,
        eta0 = eta0,
    )
    val testFn = AlphaMart(
        estimFn = estimFn,
        N = contest.Nc(),
        withoutReplacement = true,
        riskLimit = auditConfig.riskLimit,
        upperBound = assorter.upperBound(),
    )

    val testH0Result = testFn.testH0(sampler.maxSamples(), terminateOnNullReject=true) { sampler.sample() }

    assertionRound.auditResult = AuditRoundResult(roundIdx,
        nmvrs = sampler.nmvrs(),
        maxBallotIndexUsed = sampler.maxSampleIndexUsed(),
        pvalue = testH0Result.pvalueLast,
        samplesUsed = testH0Result.sampleCount,
        status = testH0Result.status,
        measuredMean = testH0Result.tracker.mean(),
    )

    if (!quiet) logger.debug{" ${contest.name} ${assertionRound.auditResult}"}
    return testH0Result
}
