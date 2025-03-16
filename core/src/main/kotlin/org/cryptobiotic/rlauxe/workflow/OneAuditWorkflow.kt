package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OAClcaAssorter
import org.cryptobiotic.rlauxe.oneaudit.OneAuditContest

class OneAuditWorkflow(
    val auditConfig: AuditConfig,
    contestsToAudit: List<OneAuditContest>, // the contests you want to audit
    val ballotCards: BallotCardsClcaStart, // mutable
): RlauxWorkflowIF {
    private val contestsUA: List<ContestUnderAudit>
    // private val cvrsUA: List<CvrUnderAudit>
    private val auditRounds = mutableListOf<AuditRound>()

    init {
        require (auditConfig.auditType == AuditType.ONEAUDIT)
        contestsUA = contestsToAudit.map { it.makeContestUnderAudit(ballotCards.cvrs) }

        // check contests well formed etc
        // check(auditConfig, contests)
    }

    //  return allDone
    override fun runAudit(auditRound: AuditRound, quiet: Boolean): Boolean  {
        return runClcaAudit(auditConfig, auditRound.contestRounds, ballotCards,
            auditRound.roundIdx, auditor = OneAuditClcaAssertion())
    }

    override fun auditConfig() =  this.auditConfig
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestUnderAudit> = contestsUA
    override fun addMvrs(mvrs: List<CvrUnderAudit>) {
        ballotCards.setMvrs(mvrs)
    }

    override fun ballotCards() = ballotCards
}

class OneAuditClcaAssertion(val quiet: Boolean = true) : ClcaAssertionAuditor {

    override fun run(
        auditConfig: AuditConfig,
        contest: ContestIF,
        assertionRound: AssertionRound,
        // cvrPairs: List<Pair<Cvr, Cvr>>, // (mvr, cvr)
        sampler: Sampler,
        roundIdx: Int,
    ): TestH0Result {
        val cassertion = assertionRound.assertion as ClcaAssertion
        val assorter = cassertion.cassorter as OAClcaAssorter

        val eta0 = assorter.meanAssort()
        val c = (eta0 - 0.5) / 2

        // TODO is this right, no special processing for the "hasCvr" strata?
        val estimFn = if (auditConfig.oaConfig.strategy == OneAuditStrategyType.max99) {
            FixedEstimFn(.99 * assorter.upperBound())
        } else {
            TruncShrinkage(
                N = contest.Nc,
                withoutReplacement = true,
                upperBound = assorter.upperBound(),
                d = auditConfig.pollingConfig.d,
                eta0 = eta0,
                c = c,
            )
        }

        val testFn = AlphaMart(
            estimFn = estimFn,
            N = contest.Nc,
            withoutReplacement = true,
            riskLimit = auditConfig.riskLimit,
            upperBound = assorter.upperBound(),
        )

        val testH0Result = testFn.testH0(sampler.maxSamples(), terminateOnNullReject = true) { sampler.sample() }

        assertionRound.auditResult = AuditRoundResult(
            roundIdx,
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
}