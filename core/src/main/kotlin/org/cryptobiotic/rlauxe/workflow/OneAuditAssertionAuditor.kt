package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.BettingMart
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting2
import org.cryptobiotic.rlauxe.betting.SamplerTracker
import org.cryptobiotic.rlauxe.betting.TestH0Result
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter

private val logger = KotlinLogging.logger("OneAuditAssertionAuditor")

// allows to run OneAudit with runClcaAuditRound
class OneAuditAssertionAuditor(val pools: List<OneAuditPoolIF>, val quiet: Boolean = true) : ClcaAssertionAuditorIF {

    override fun run(
        config: AuditConfig,
        contestRound: ContestRound,
        assertionRound: AssertionRound,
        samplerTracker: SamplerTracker,
        roundIdx: Int,
    ): TestH0Result {
        val contestUA = contestRound.contestUA
        val cassertion = assertionRound.assertion as ClcaAssertion
        val oaCassorter = cassertion.cassorter as OneAuditClcaAssorter
        val clcaConfig = config.clcaConfig

        val noerror=oaCassorter.noerror()
        val upper=oaCassorter.assorter.upperBound()
        val apriori = clcaConfig.apriori.makeErrorCounts(contestUA.Npop, noerror, upper)

        val bettingFn = if (clcaConfig.strategy == ClcaStrategyType.generalAdaptive) {
            GeneralAdaptiveBetting(
                contestUA.Npop,
                startingErrors = ClcaErrorCounts.empty(noerror, upper),
                contestUA.contest.Nphantoms(),
                oaAssortRates = oaCassorter.oaAssortRates,
                d = clcaConfig.d,
                maxLoss = clcaConfig.maxLoss)
        } else {
            GeneralAdaptiveBetting2(
                contestUA.Npop,
                aprioriCounts = apriori,
                nphantoms = contestUA.contest.Nphantoms(),
                maxLoss = clcaConfig.maxLoss,
                d = clcaConfig.d,
            )
        }

        val testFn = BettingMart(
            bettingFn = bettingFn,
            N = contestUA.Npop,
            sampleUpperBound = oaCassorter.upperBound(),
            riskLimit = config.riskLimit,
            tracker = samplerTracker
        )
        testFn.setDebuggingSequences()

        val terminateOnNullReject = config.auditSampleLimit == null
        val testH0Result = testFn.testH0(samplerTracker.maxSamples(), terminateOnNullReject = terminateOnNullReject) { samplerTracker.sample() }

        assertionRound.auditResult = AuditRoundResult(
            roundIdx,
            nmvrs = samplerTracker.maxSamples(),
            // countCvrsUsedInAudit = samplerTracker.countCvrsUsedInAudit(),
            plast = testH0Result.pvalueLast,
            pmin = testH0Result.pvalueMin,
            samplesUsed = testH0Result.sampleCount,
            status = testH0Result.status,
            measuredCounts = samplerTracker.measuredClcaErrorCounts(),
        )

        if (!quiet) logger.debug{" ${contestUA.name} auditResult= ${assertionRound.auditResult}"}
        return testH0Result
    }
}