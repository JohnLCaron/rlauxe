package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.AlphaMart
import org.cryptobiotic.rlauxe.betting.PollingSamplerTracker
import org.cryptobiotic.rlauxe.betting.SamplerTracker
import org.cryptobiotic.rlauxe.betting.TestH0Result
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.betting.TruncShrinkage
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*

private val logger = KotlinLogging.logger("PollingAudit")

// TODO parallelize over contests; see runClcaAuditRound
fun runPollingAuditRound(
    config: AuditConfig,
    auditRound: AuditRound,
    mvrManager: MvrManager,
    roundIdx: Int,
    quiet: Boolean = true
): Boolean {
    val pairs = mvrManager.makeMvrCardPairsForRound(roundIdx)

    val contestsNotDone = auditRound.contestRounds.filter { !it.done }
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

                val sampler =  PollingSamplerTracker(contest.id, assorter, pairs)

                val testH0Result = auditPollingAssertion(config, contest.contestUA, assertionRound, sampler, roundIdx, quiet)
                assertionRound.status = testH0Result.status
                if (testH0Result.status.complete) assertionRound.roundProved = roundIdx
            }
            contestAssertionStatus.add(assertionRound.status)
        }
        contest.done = contestAssertionStatus.all { it.complete }
        contest.status = contestAssertionStatus.minBy { it.rank } // use lowest rank status.
        allDone = allDone && contest.done
    }

    // given the cvrPairs, and each ContestRound's maxSampleIndexUsed, count the cvrs that were not used
    val maxIndex = contestsNotDone.associate { it.id to it.maxSampleIndexUsed() }
    var countUnused = 0
    pairs.forEachIndexed { idx, mvrCardPair ->
        val card = mvrCardPair.second
        var wasUsed = false
        contestsNotDone.forEach { contest ->
            if (card.hasContest(contest.id) && idx < maxIndex[contest.id]!!) wasUsed = true
        }
        if (!wasUsed) countUnused++
    }

    auditRound.samplesNotUsed =  countUnused
    return allDone
}

fun auditPollingAssertion(
    config: AuditConfig,
    contestUA: ContestWithAssertions,
    assertionRound: AssertionRound,
    sampler: SamplerTracker,
    roundIdx: Int,
    quiet: Boolean = false
): TestH0Result {
    val assertion = assertionRound.assertion
    val assorter = assertion.assorter

    val eta0 = margin2mean(assorter.dilutedMargin())

    val estimFn = TruncShrinkage(
        N = contestUA.Npop,
        withoutReplacement = true,
        upperBound = assorter.upperBound(),
        d = config.pollingConfig.d,
        eta0 = eta0,
    )

    val testFn = AlphaMart(
        estimFn = estimFn,
        N = contestUA.Npop,
        tracker = sampler,
        withoutReplacement = true,
        riskLimit = config.riskLimit,
        upperBound = assorter.upperBound(),
    )

    val testH0Result = testFn.testH0(sampler.maxSamples(), terminateOnNullReject=true) { sampler.sample() }

    assertionRound.auditResult = AuditRoundResult(roundIdx,
        nmvrs = sampler.nmvrs(),
        maxSampleIndexUsed = sampler.maxSampleIndexUsed(),
        plast = testH0Result.pvalueLast,
        pmin = testH0Result.pvalueMin,
        samplesUsed = testH0Result.sampleCount,
        status = testH0Result.status,
    )

    if (!quiet) logger.debug{" ${contestUA.name} ${assertionRound.auditResult}"}
    return testH0Result
}
