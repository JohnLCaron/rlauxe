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
): RlauxWorkflowIF {
    private val contestsUA: List<ContestUnderAudit> = contestsToAudit.map { ContestUnderAudit(it, isComparison=false, auditConfig.hasStyles) }
    val ballotsUA: List<BallotUnderAudit>
    private val auditRounds = mutableListOf<AuditRound>()

    init {
        require (auditConfig.auditType == AuditType.POLLING)
        require (ballotManifest.ballots.size == Nb)

        /* contestsUA.forEach {
            if (it.choiceFunction != SocialChoiceFunction.IRV) {
                checkWinners(it, (it.contest as Contest).votes.entries.sortedByDescending { it.value })
            }
        } */

        // TODO filter out contests that are done...
        contestsUA.forEach { contest ->
            contest.makePollingAssertions()
        }

        /* check contests well formed etc
        contests = contestsUA.map { ContestRound(it, 1) }
        check(auditConfig, contests) */

        // must be done once and for all rounds
        val prng = Prng(auditConfig.seed)
        ballotsUA = ballotManifest.ballots.map { BallotUnderAudit(it, prng.next()) }
    }

    override fun startNewRound(quiet: Boolean): AuditRound {
        val previousRound = if (auditRounds.isEmpty()) null else auditRounds.last()
        val roundIdx = auditRounds.size + 1

        val auditRound = if (previousRound == null) {
            val contestRounds = contestsUA.map { ContestRound(it, roundIdx) }
            AuditRound(roundIdx, contestRounds = contestRounds, sampledIndices = emptyList())
        } else {
            previousRound.createNextRound()
        }
        auditRounds.add(auditRound)

        estimateSampleSizes(
            auditConfig,
            auditRound,
            emptyList(),
            show=!quiet,
        )

        auditRound.sampledIndices = sample(this, auditRound, auditRounds.previousSamples(roundIdx), quiet)
        return auditRound
    }

    override fun runAudit(auditRound: AuditRound, mvrs: List<Cvr>, quiet: Boolean): Boolean  { // return allDone
        return runPollingAudit(auditConfig, auditRound.contestRounds, mvrs, auditRound.roundIdx, quiet)
    }

    override fun auditConfig() =  this.auditConfig
    override fun getContests(): List<ContestUnderAudit> = contestsUA
    override fun getBallotsOrCvrs() : List<BallotOrCvr> = ballotsUA
}

fun runPollingAudit(
    auditConfig: AuditConfig,
    contests: List<ContestRound>,
    mvrs: List<Cvr>,
    roundIdx: Int,
    quiet: Boolean = false
): Boolean {
    val contestsNotDone = contests.filter { !it.done }
    if (contestsNotDone.isEmpty()) {
        return true
    }

    if (!quiet) println("runAudit round $roundIdx")
    var allDone = true
    contestsNotDone.forEach { contest ->
        val contestAssertionStatus = mutableListOf<TestH0Status>()
        contest.assertionRounds.forEach { assertionRound ->
            if (!assertionRound.status.complete) {
                val testH0Result = auditPollingAssertion(auditConfig, contest.contestUA.contest, assertionRound, mvrs, roundIdx, quiet)
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
    mvrs: List<Cvr>,
    roundIdx: Int,
    quiet: Boolean = false
): TestH0Result {
    val assertion = assertionRound.assertion
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

    val testH0Result = testFn.testH0(sampler.maxSamples(), terminateOnNullReject=true) { sampler.sample() }

    assertionRound.auditResult = AuditRoundResult(roundIdx,
        nmvrs = sampler.maxSamples(),
        maxBallotIndexUsed = sampler.maxSampleIndexUsed(),
        pvalue = testH0Result.pvalueLast,
        samplesNeeded = testH0Result.sampleFirstUnderLimit, // one based
        samplesUsed = testH0Result.sampleCount,
        status = testH0Result.status,
        measuredMean = testH0Result.tracker.mean(),
    )

    if (!quiet) println(" ${contest.info.name} ${assertionRound.auditResult}")
    return testH0Result
}
