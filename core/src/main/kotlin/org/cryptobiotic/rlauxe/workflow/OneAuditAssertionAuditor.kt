package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter

private val logger = KotlinLogging.logger("OneAuditAssertionAuditor")

class OneAuditAssertionAuditor(val quiet: Boolean = true) : ClcaAssertionAuditorIF {

    override fun run(
        auditConfig: AuditConfig,
        contest: ContestIF,
        assertionRound: AssertionRound,
        sampler: Sampler,
        roundIdx: Int,
    ): TestH0Result {
        val cassertion = assertionRound.assertion as ClcaAssertion
        val cassorter = cassertion.cassorter as OneAuditClcaAssorter

        // // default: eta0 = reportedMean, shrinkTrunk
        //// bet99: eta0 = reportedMean, 99% max bet
        //// eta0Eps: eta0 = upper*(1 - eps), shrinkTrunk
        //// maximal: eta0 = upper*(1 - eps), 99% max bet

        val strategy = auditConfig.oaConfig.strategy

        val testH0Result = if (strategy == OneAuditStrategyType.optimalComparison || strategy == OneAuditStrategyType.optimalBet) {
            runBetting(
                auditConfig,
                contest.Nc(),
                cassorter,
                sampler,
                cassorter.upperBound()
            )
        } else {
            runAlpha(
                auditConfig,
                contest.Nc(),
                cassorter,
                sampler,
                cassorter.upperBound()
            )
        }

        // println(testH0Result)
        //println("pvalues=  ${debugSeq.pvalues()}")

        assertionRound.auditResult = AuditRoundResult(
            roundIdx,
            nmvrs = sampler.nmvrs(),
            maxBallotIndexUsed = sampler.maxSampleIndexUsed(),
            pvalue = testH0Result.pvalueLast,
            samplesUsed = testH0Result.sampleCount,
            status = testH0Result.status,
            measuredMean = testH0Result.tracker.mean(),
        )

        if (!quiet) logger.debug{" ${contest.name} strategy=$strategy auditResult= ${assertionRound.auditResult}"}
        return testH0Result
    }

    // TODO not needed?
     fun runAlpha(
        auditConfig: AuditConfig,
        Nc: Int,
        cassorter: OneAuditClcaAssorter,
        sampler: Sampler,
        upperBound: Double,
    ): TestH0Result {

         val strategy = auditConfig.oaConfig.strategy
         val eta0 = if (strategy == OneAuditStrategyType.eta0Eps)
             cassorter.upperBound() * (1.0 - eps)
         else
             cassorter.noerror() // seems reasonable, but I dont think SHANGRLA ever uses, so maybe not?

         val estimFn = if (auditConfig.oaConfig.strategy == OneAuditStrategyType.bet99) {
             FixedEstimFn(.99 * cassorter.upperBound())
         } else {
             TruncShrinkage(
                 N = Nc,
                 withoutReplacement = true,
                 upperBound = cassorter.upperBound(),
                 d = auditConfig.pollingConfig.d,
                 eta0 = eta0,
             )
         }

        val alpha = AlphaMart(
            estimFn = estimFn,
            N = Nc,
            withoutReplacement = true,
            riskLimit = auditConfig.riskLimit,
            upperBound = upperBound,
        )
        return alpha.testH0(sampler.maxSamples(), terminateOnNullReject = true) { sampler.sample() }
    }

    fun runBetting(
        auditConfig: AuditConfig,
        Nc: Int,
        cassorter: OneAuditClcaAssorter,
        sampler: Sampler,
        upperBound: Double,
    ): TestH0Result {

        // no errors!
        val bettingFn: BettingFn = OptimalComparisonNoP1(Nc, true, upperBound, p2 = 0.0)

        val testFn = BettingMart(
            bettingFn = bettingFn,
            Nc = Nc,
            noerror = cassorter.noerror(),
            upperBound = cassorter.upperBound(),
            riskLimit = auditConfig.riskLimit,
            withoutReplacement = true
        )

        return testFn.testH0(sampler.maxSamples(), terminateOnNullReject = true) { sampler.sample() }
    }
}