package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OneAuditAssorter
import org.cryptobiotic.rlauxe.oneaudit.OneAuditContest

class OneAudit(
    val auditConfig: AuditConfig,
    contestsToAudit: List<OneAuditContest>, // the contests you want to audit
    val mvrManager: MvrManagerClcaIF,
): RlauxAuditIF {
    private val contestsUA: List<ContestUnderAudit>
    private val auditRounds = mutableListOf<AuditRound>()

    init {
        require (auditConfig.auditType == AuditType.ONEAUDIT)
        contestsUA = contestsToAudit.map { it.makeContestUnderAudit() }

        // check contests well formed etc
        // check(auditConfig, contests)
    }

    override fun runAuditRound(auditRound: AuditRound, quiet: Boolean): Boolean  {
        val complete = runClcaAudit(auditConfig, auditRound.contestRounds, mvrManager, auditRound.roundIdx,
            auditor = OneAuditAssertionAuditor()
        )
        auditRound.auditWasDone = true
        auditRound.auditIsComplete = complete
        return complete
    }

    override fun auditConfig() =  this.auditConfig
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestUnderAudit> = contestsUA

    override fun mvrManager() = mvrManager
}

class OneAuditAssertionAuditor(val quiet: Boolean = true) : ClcaAssertionAuditor {

    override fun run(
        auditConfig: AuditConfig,
        contest: ContestIF,
        assertionRound: AssertionRound,
        sampler: Sampler,
        roundIdx: Int,
    ): TestH0Result {
        val cassertion = assertionRound.assertion as ClcaAssertion
        val cassorter = cassertion.cassorter as OneAuditAssorter

        // // default: eta0 = reportedMean, shrinkTrunk
        //// bet99: eta0 = reportedMean, 99% max bet
        //// eta0Eps: eta0 = upper*(1 - eps), shrinkTrunk
        //// maximal: eta0 = upper*(1 - eps), 99% max bet

        val strategy = auditConfig.oaConfig.strategy
        val eta0 = if (strategy == OneAuditStrategyType.eta0Eps)
            cassorter.upperBound() * (1.0 - eps)
        else
            cassorter.meanAssort() // seems reasonable, but I dont think SHANGRLA ever uses, so maybe not?

        val estimFn = if (auditConfig.oaConfig.strategy == OneAuditStrategyType.bet99) {
            FixedEstimFn(.99 * cassorter.upperBound())
        } else {
            TruncShrinkage(
                N = contest.Nc,
                withoutReplacement = true,
                upperBound = cassorter.upperBound(),
                d = auditConfig.pollingConfig.d,
                eta0 = eta0,
            )
        }

        val testFn = AlphaMart(
            estimFn = estimFn,
            N = contest.Nc,
            withoutReplacement = true,
            riskLimit = auditConfig.riskLimit,
            upperBound = cassorter.upperBound(),
        )

        val testH0Result = testFn.testH0(sampler.maxSamples(), terminateOnNullReject = true) { sampler.sample() }

        assertionRound.auditResult = AuditRoundResult(
            roundIdx,
            nmvrs = sampler.nmvrs(),
            maxBallotIndexUsed = sampler.maxSampleIndexUsed(),
            pvalue = testH0Result.pvalueLast,
            samplesUsed = testH0Result.sampleCount,
            status = testH0Result.status,
            measuredMean = testH0Result.tracker.mean(),
        )

        if (!quiet) println(" ${contest.info.name} ${assertionRound.auditResult}")
        return testH0Result
    }
}