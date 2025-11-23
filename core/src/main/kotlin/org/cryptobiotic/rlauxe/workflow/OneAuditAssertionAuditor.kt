package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.Sampler
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter

private val logger = KotlinLogging.logger("OneAuditAssertionAuditor")

// allows to run OneAudit with runClcaAuditRound
class OneAuditAssertionAuditor(val quiet: Boolean = true) : ClcaAssertionAuditorIF {

    override fun run(
        config: AuditConfig,
        contestRound: ContestRound,
        assertionRound: AssertionRound,
        sampler: Sampler,
        roundIdx: Int,
    ): TestH0Result {
        val contestUA = contestRound.contestUA
        val cassertion = assertionRound.assertion as ClcaAssertion
        val cassorter = cassertion.cassorter as OneAuditClcaAssorter

        val errorCounts = if (assertionRound.prevAuditResult != null) {
            assertionRound.accumulatedErrorCounts(contestRound).toPluralityErrorRates()
        } else PluralityErrorRates.Zero

        //if (errorRates.p2o < contestUA.contest.phantomRate())
        //    errorRates = errorRates.copy( p2o = contestUA.contest.phantomRate())

        // // default: eta0 = reportedMean, shrinkTrunk
        //// bet99: eta0 = reportedMean, 99% max bet
        //// eta0Eps: eta0 = upper*(1 - eps), shrinkTrunk
        //// maximal: eta0 = upper*(1 - eps), 99% max bet

        val strategy = config.oaConfig.strategy

        val testH0Result = if (strategy == OneAuditStrategyType.optimalComparison) {
            runBetting(
                config,
                contestUA.Nb,
                cassorter,
                sampler,
                cassorter.upperBound(),
                p2 = 0.0 // errorRates.p2o TODO
            )
        } else {
            runAlpha(
                config,
                contestUA.Nb,
                cassorter,
                sampler,
                cassorter.upperBound()
            )
        }

        val measuredCounts = if (testH0Result.tracker is ClcaErrorRatesIF) testH0Result.tracker.errorCounts() else null
        assertionRound.auditResult = AuditRoundResult(
            roundIdx,
            nmvrs = sampler.nmvrs(),
            maxBallotIndexUsed = sampler.maxSampleIndexUsed(),
            pvalue = testH0Result.pvalueLast,
            samplesUsed = testH0Result.sampleCount,
            status = testH0Result.status,
            measuredMean = testH0Result.tracker.mean(),
            startingRates = errorCounts.errorRates(cassorter.noerror()),
            measuredCounts = measuredCounts,
        )

        if (!quiet) logger.debug{" ${contestUA.name} strategy=$strategy auditResult= ${assertionRound.auditResult}"}
        return testH0Result
    }

     fun runAlpha(
         config: AuditConfig,
         N: Int,
         cassorter: OneAuditClcaAssorter,
         sampler: Sampler,
         upperBound: Double,
    ): TestH0Result {

         val strategy = config.oaConfig.strategy
         val eta0 = if (strategy == OneAuditStrategyType.eta0Eps)
             cassorter.upperBound() * (1.0 - eps)
         else
             cassorter.noerror() // seems reasonable, but I dont think SHANGRLA ever uses, so maybe not?

         val estimFn = if (config.oaConfig.strategy == OneAuditStrategyType.bet99) {
             FixedEstimFn(.99 * cassorter.upperBound())
         } else {
             TruncShrinkage(
                 N = N,
                 withoutReplacement = true,
                 upperBound = cassorter.upperBound(),
                 d = config.pollingConfig.d,
                 eta0 = eta0,
             )
         }

        val alpha = AlphaMart(
            estimFn = estimFn,
            N = N,
            withoutReplacement = true,
            riskLimit = config.riskLimit,
            upperBound = upperBound,
        )
        return alpha.testH0(sampler.maxSamples(), terminateOnNullReject = true) { sampler.sample() }
    }

    fun runBetting(
        config: AuditConfig,
        N: Int,
        cassorter: OneAuditClcaAssorter,
        sampler: Sampler,
        upperBound: Double,
        p2: Double,
    ): TestH0Result {

        val bettingFn: BettingFn = OptimalComparisonNoP1(N=N, true, upperBound, p2 = p2)

        val testFn = BettingMart(
            bettingFn = bettingFn,
            N = N,
            tracker = ClcaErrorTracker(cassorter.noerror()), // TODO cant track pool data??
            sampleUpperBound = cassorter.upperBound(),
            riskLimit = config.riskLimit,
            withoutReplacement = true
        )

        return testFn.testH0(sampler.maxSamples(), terminateOnNullReject = true) { sampler.sample() }
    }
}