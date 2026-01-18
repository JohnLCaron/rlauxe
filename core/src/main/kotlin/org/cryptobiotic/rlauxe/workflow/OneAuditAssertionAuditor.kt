package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.BettingFn
import org.cryptobiotic.rlauxe.betting.BettingMart
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker
import org.cryptobiotic.rlauxe.betting.TestH0Result
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.oneaudit.ClcaAssorterOneAudit
import org.cryptobiotic.rlauxe.oneaudit.OneAuditRatesFromPools

private val logger = KotlinLogging.logger("OneAuditAssertionAuditor")

// allows to run OneAudit with runClcaAuditRound
class OneAuditAssertionAuditor(val pools: List<OneAuditPoolIF>, val quiet: Boolean = true) : ClcaAssertionAuditorIF {

    override fun run(
        config: AuditConfig,
        contestRound: ContestRound,
        assertionRound: AssertionRound,
        sampling: Sampler,
        roundIdx: Int,
    ): TestH0Result {
        val contestUA = contestRound.contestUA
        val cassertion = assertionRound.assertion as ClcaAssertion
        val oaCassorter = cassertion.cassorter as ClcaAssorterOneAudit
        val clcaConfig = config.clcaConfig

        val oneAuditErrorsFromPools = OneAuditRatesFromPools(pools)
        val oaErrorRates = oneAuditErrorsFromPools.oaErrorRates(contestUA, oaCassorter)

        val bettingFn = // if (clcaConfig.strategy == ClcaStrategyType.generalAdaptive) {
            GeneralAdaptiveBetting(
                Npop = contestUA.Npop,
                startingErrors = ClcaErrorCounts.empty(oaCassorter.noerror(), oaCassorter.assorter.upperBound()),
                contestUA.contest.Nphantoms(),
                oaAssortRates = oaErrorRates,
                d = clcaConfig.d,
                maxRisk = clcaConfig.maxRisk
            )

        val testH0Result = runBetting(config, contestUA.Npop, oaCassorter, sampling, bettingFn)

        val measuredCounts: ClcaErrorCounts? = if (testH0Result.tracker is ClcaErrorTracker) testH0Result.tracker.measuredClcaErrorCounts() else null
        assertionRound.auditResult = AuditRoundResult(
            roundIdx,
            nmvrs = sampling.maxSamples(),
            maxBallotIndexUsed = sampling.maxSampleIndexUsed(),
            pvalue = testH0Result.pvalueLast,
            samplesUsed = testH0Result.sampleCount,
            status = testH0Result.status,
            // startingRates = bettingFn.startingErrorRates(),
            measuredCounts = measuredCounts,
            // params = mapOf("poolAvg" to poolAvg)
        )

        if (!quiet) logger.debug{" ${contestUA.name} auditResult= ${assertionRound.auditResult}"}
        return testH0Result
    }

    fun runBetting(
        config: AuditConfig,
        N: Int,
        cassorter: ClcaAssorterOneAudit,
        sampling: Sampler,
        bettingFn: BettingFn,
    ): TestH0Result {

        val tracker = ClcaErrorTracker(
            cassorter.noerror(),
            cassorter.assorter.upperBound(),
        )

        val testFn = BettingMart(
            bettingFn = bettingFn,
            N = N,
            sampleUpperBound = cassorter.upperBound(),
            riskLimit = config.riskLimit,
            withoutReplacement = true,
            tracker = tracker,
        )
        // TODO make optional
        tracker.setDebuggingSequences(testFn.setDebuggingSequences())

        // TODO how come you dont need startingTestStatistic: Double,
        return testFn.testH0(sampling.maxSamples(), terminateOnNullReject = true) { sampling.sample() }
    }
}