package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.estimate.*
import org.cryptobiotic.rlauxe.util.*

class PollingWorkflow(
    val auditConfig: AuditConfig,
    contestsToAudit: List<ContestIF>, // the contests you want to audit
    ballotManifest: BallotManifest,
    val Nb: Int, // total number of ballots/cards TODO same as ballots.size ??
    val quiet: Boolean = true,
): RlauxWorkflowIF {
    val contestsUA: List<ContestUnderAudit> = contestsToAudit.map { ContestUnderAudit(it, isComparison=false, auditConfig.hasStyles) }
    val ballotsUA: List<BallotUnderAudit>

    init {
        require (auditConfig.auditType == AuditType.POLLING)
        require (ballotManifest.ballots.size == Nb)

        contestsUA.forEach {
            if (it.choiceFunction != SocialChoiceFunction.IRV) {
                checkWinners(it, (it.contest as Contest).votes.entries.sortedByDescending { it.value })
            }
        }

        contestsUA.filter { !it.done }.forEach { contest ->
            contest.makePollingAssertions()
        }

        // check contests well formed etc
        check(auditConfig, contestsUA)

        // must be done once and for all rounds
        val prng = Prng(auditConfig.seed)
        ballotsUA = ballotManifest.ballots.map { BallotUnderAudit(it, prng.next()) }
    }

    override fun chooseSamples(roundIdx: Int, show: Boolean): List<Int> {
        if (!quiet) println("estimateSampleSizes round $roundIdx")
        estimateSampleSizes(
            auditConfig,
            contestsUA,
            emptyList(),
            roundIdx,
            show=show,
        )

        return sample(this, roundIdx, quiet)
    }

    override fun runAudit(sampleIndices: List<Int>, mvrs: List<Cvr>, roundIdx: Int): Boolean {
        return runPollingAudit(auditConfig, contestsUA, mvrs, roundIdx, quiet)
    }

    override fun auditConfig() =  this.auditConfig
    override fun getContests() : List<ContestUnderAudit> = contestsUA
    override fun getBallotsOrCvrs() : List<BallotOrCvr> = ballotsUA
}

fun runPollingAudit(
    auditConfig: AuditConfig,
    contestsUA: List<ContestUnderAudit>,
    mvrs: List<Cvr>,
    roundIdx: Int,
    quiet: Boolean = false
): Boolean {
    val contestsNotDone = contestsUA.filter { !it.done }
    if (contestsNotDone.isEmpty()) {
        return true
    }

    var allDone = true
    contestsNotDone.forEach { contestUA ->
        var allAssertionsDone = true
        contestUA.pollingAssertions.forEach { assertion ->
            if (!assertion.status.complete) {
                val testResult = auditPollingAssertion(auditConfig, contestUA.contest as Contest, assertion, mvrs, roundIdx, quiet)
                assertion.status = testResult.status
                assertion.round = roundIdx
                allAssertionsDone = allAssertionsDone && assertion.status.complete
            }
        }
        if (allAssertionsDone) {
            contestUA.done = true
            contestUA.status = TestH0Status.StatRejectNull // TODO
        }
        allDone = allDone && contestUA.done
    }
    return allDone
}

fun auditPollingAssertion(
    auditConfig: AuditConfig,
    contest: Contest,
    assertion: Assertion,
    mvrs: List<Cvr>,
    roundIdx: Int,
    quiet: Boolean = false
): TestH0Result {
    val assorter = assertion.assorter
    val sampler = PollWithoutReplacement(contest, mvrs, assorter, allowReset=false)

    val eta0 = margin2mean(assorter.reportedMargin())
    val c = (eta0 - 0.5) / 2

    val estimFn = TruncShrinkage(
        N = contest.Nc,
        withoutReplacement = true,
        upperBound = assorter.upperBound(),
        d = auditConfig.pollingConfig.d,
        eta0 = eta0,
        c = c,
    )
    val testFn = AlphaMart(
        estimFn = estimFn,
        N = contest.Nc,
        withoutReplacement = true,
        riskLimit = auditConfig.riskLimit,
        upperBound = assorter.upperBound(),
    )

    // do not terminate on null reject, continue to use all available samples
    val testH0Result = testFn.testH0(sampler.maxSamples(), terminateOnNullReject=true) { sampler.sample() }

    val roundResult = AuditRoundResult(roundIdx,
        estSampleSize=assertion.estSampleSize,
        maxBallotsUsed = sampler.maxSamplesUsed(),
        pvalue = testH0Result.pvalueLast,
        samplesNeeded = testH0Result.sampleFirstUnderLimit, // one based
        samplesUsed = testH0Result.sampleCount,
        status = testH0Result.status,
        errorRates = testH0Result.tracker.errorRates()
    )
    assertion.roundResults.add(roundResult)

    if (!quiet) println(" ${contest.name} $roundResult")
    return testH0Result
}
