package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OAClcaAssorter
import org.cryptobiotic.rlauxe.oneaudit.OneAuditContest
import org.cryptobiotic.rlauxe.util.*

class OneAuditWorkflow(
    val auditConfig: AuditConfig,
    contestsToAudit: List<OneAuditContest>, // the contests you want to audit
    val cvrs: List<Cvr>, // includes undervotes and phantoms.
): RlauxWorkflowIF {
    private val contestsUA: List<ContestUnderAudit>
    private val cvrsUA: List<CvrUnderAudit>
    private val auditRounds = mutableListOf<AuditRound>()

    init {
        require (auditConfig.auditType == AuditType.ONEAUDIT)
        contestsUA = contestsToAudit.map { it.makeContestUnderAudit(cvrs) }

        // check contests well formed etc
        // check(auditConfig, contests)

        // must be done once and for all rounds
        val prng = Prng(auditConfig.seed)
        cvrsUA = cvrs.map { CvrUnderAudit(it, prng.next()) }
    }

    //  return allDone
    override fun runAudit(auditRound: AuditRound, mvrs: List<Cvr>, quiet: Boolean): Boolean  {
        return runClcaAudit(auditConfig, auditRound.contestRounds, auditRound.sampledIndices, mvrs, cvrs,
            auditRound.roundIdx, auditor = OneAuditClcaAssertion())
    }

    override fun auditConfig() =  this.auditConfig
    override fun auditRounds() = auditRounds
    override fun contestUA(): List<ContestUnderAudit> = contestsUA
    override fun cvrs() = cvrs
    override fun getBallotsOrCvrs() : List<BallotOrCvr> = cvrsUA
}

class OneAuditClcaAssertion(val quiet: Boolean = true) : ClcaAssertionAuditor {

    override fun run(
        auditConfig: AuditConfig,
        contest: ContestIF,
        assertionRound: AssertionRound,
        cvrPairs: List<Pair<Cvr, Cvr>>, // (mvr, cvr)
        roundIdx: Int,
    ): TestH0Result {
        val cassertion = assertionRound.assertion as ClcaAssertion
        val assorter = cassertion.cassorter as OAClcaAssorter
        val sampler = ClcaWithoutReplacement(
            contest,
            cvrPairs,
            cassertion.cassorter,
            allowReset = false,
            trackStratum = false
        )

        val eta0 = margin2mean(assorter.clcaMargin)
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