package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.BettingFn
import org.cryptobiotic.rlauxe.betting.BettingMart
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.betting.ErrorTracker
import org.cryptobiotic.rlauxe.betting.SamplerTracker
import org.cryptobiotic.rlauxe.betting.TestH0Result
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.oneaudit.ClcaAssorterOneAudit

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
        val oaCassorter = cassertion.cassorter as ClcaAssorterOneAudit
        val clcaConfig = config.clcaConfig

        val bettingFn = // if (clcaConfig.strategy == ClcaStrategyType.generalAdaptive) {
            GeneralAdaptiveBetting(
                Npop = contestUA.Npop,
                startingErrors = ClcaErrorCounts.empty(oaCassorter.noerror(), oaCassorter.assorter.upperBound()),
                contestUA.contest.Nphantoms(),
                oaAssortRates = oaCassorter.oaAssortRates,
                d = clcaConfig.d,
                maxLoss = clcaConfig.maxLoss
            )
        val testH0Result = runBetting(config, contestUA.Npop, oaCassorter, samplerTracker, bettingFn)

        val measuredCounts: ClcaErrorCounts? = if (samplerTracker is ErrorTracker) samplerTracker.measuredClcaErrorCounts() else null
        assertionRound.auditResult = AuditRoundResult(
            roundIdx,
            nmvrs = samplerTracker.maxSamples(),
            maxSampleIndexUsed = samplerTracker.maxSampleIndexUsed(),
            plast = testH0Result.pvalueLast,
            pmin = testH0Result.pvalueMin,
            samplesUsed = testH0Result.sampleCount,
            status = testH0Result.status,
            measuredCounts = measuredCounts,
        )

        if (!quiet) logger.debug{" ${contestUA.name} auditResult= ${assertionRound.auditResult}"}
        return testH0Result
    }

    fun runBetting(
        config: AuditConfig,
        N: Int,
        cassorter: ClcaAssorterOneAudit,
        samplerTracker: SamplerTracker,
        bettingFn: BettingFn,
    ): TestH0Result {

        val testFn = BettingMart(
            bettingFn = bettingFn,
            N = N,
            tracker = samplerTracker,
            sampleUpperBound = cassorter.upperBound(),
            riskLimit = config.riskLimit,
        )
        testFn.setDebuggingSequences()

        return testFn.testH0(samplerTracker.maxSamples(), terminateOnNullReject = true) { samplerTracker.sample() }
    }
}