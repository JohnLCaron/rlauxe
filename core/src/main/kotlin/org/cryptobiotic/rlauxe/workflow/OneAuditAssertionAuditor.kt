package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ClcaErrorCounts
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.ClcaAssorterOneAudit
import org.cryptobiotic.rlauxe.oneaudit.OneAuditErrorsFromPools

private val logger = KotlinLogging.logger("OneAuditAssertionAuditor")

// allows to run OneAudit with runClcaAuditRound
class OneAuditAssertionAuditor(val pools: List<CardPoolIF>, val quiet: Boolean = true) : ClcaAssertionAuditorIF {

    override fun run(
        config: AuditConfig,
        contestRound: ContestRound,
        assertionRound: AssertionRound,
        sampling: Sampling,
        roundIdx: Int,
    ): TestH0Result {
        val contestUA = contestRound.contestUA
        val cassertion = assertionRound.assertion as ClcaAssertion
        val oaCassorter = cassertion.cassorter as ClcaAssorterOneAudit
        val clcaConfig = config.clcaConfig

        val oneAuditErrorsFromPools = OneAuditErrorsFromPools(pools)
        val oaErrorRates = oneAuditErrorsFromPools.oaErrorRates(contestUA, oaCassorter)

        val accumErrorCounts: ClcaErrorCounts = assertionRound.accumulatedErrorCounts(contestRound)
        accumErrorCounts.setPhantomRate(contestUA.contest.phantomRate()) // TODO ??

        val bettingFn: BettingFn = // if (clcaConfig.strategy == ClcaStrategyType.generalAdaptive) {
            GeneralAdaptiveBetting(Npop = contestUA.Npop, oaErrorRates=oaErrorRates, d = clcaConfig.d, maxRisk=clcaConfig.maxRisk)

        /* } else if (clcaConfig.strategy == ClcaStrategyType.apriori) {
            val errorRates= ClcaErrorCounts.fromPluralityAndPrevRates(clcaConfig.pluralityErrorRates!!, accumErrorCounts)
            GeneralAdaptiveBettingOld(N = contestUA.Npop, startingErrorRates = errorRates, d = clcaConfig.d)

        } else if (clcaConfig.strategy == ClcaStrategyType.fuzzPct) {
            val errorsP = ClcaErrorTable.getErrorRates(contestUA.contest.ncandidates, clcaConfig.fuzzPct) // TODO do better
            val errorRates= ClcaErrorCounts.fromPluralityAndPrevRates(errorsP, accumErrorCounts)
            GeneralAdaptiveBettingOld(N = contestUA.Npop, startingErrorRates = errorRates, d = clcaConfig.d)

        } else {
            throw RuntimeException("unsupported strategy ${clcaConfig.strategy}")
        } */

        /* enum class OneAuditStrategyType { reportedMean, bet99, eta0Eps, optimalComparison }
        val strategy = config.oaConfig.strategy
        val testH0Result = if (strategy == OneAuditStrategyType.clca || strategy == OneAuditStrategyType.optimalComparison) {
            val bettingFn: BettingFn = if (strategy == OneAuditStrategyType.clca) clcaBettingFn else {
                // TODO p2o = clcaBettingFn.startingErrorRates.get("p2o")
                OptimalComparisonNoP1(contestUA.Npop, true, oaCassorter.upperBound)
            }
            runBetting(config, contestUA.Npop, oaCassorter, sampling, bettingFn)

        } else {
            runAlpha(config, contestUA.Npop, oaCassorter, sampling, oaCassorter.upperBound())
        } */

        val testH0Result = runBetting(config, contestUA.Npop, oaCassorter, sampling, bettingFn)

        val measuredCounts: ClcaErrorCounts? = if (testH0Result.tracker is ClcaErrorTracker) testH0Result.tracker.measuredErrorCounts() else null
        assertionRound.auditResult = AuditRoundResult(
            roundIdx,
            nmvrs = sampling.nmvrs(),
            maxBallotIndexUsed = sampling.maxSampleIndexUsed(),
            pvalue = testH0Result.pvalueLast,
            samplesUsed = testH0Result.sampleCount,
            status = testH0Result.status,
            measuredMean = testH0Result.tracker.mean(),
            startingRates = accumErrorCounts,
            measuredCounts = measuredCounts,
            // params = mapOf("poolAvg" to poolAvg)
        )

        if (!quiet) logger.debug{" ${contestUA.name} auditResult= ${assertionRound.auditResult}"}
        return testH0Result
    }

     fun runAlpha(
         config: AuditConfig,
         N: Int,
         cassorter: ClcaAssorterOneAudit,
         sampling: Sampling,
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

         val tracker = ClcaErrorTracker(cassorter.noerror(), cassorter.assorter.upperBound()) // track pool data; something better to do?

         return alpha.testH0(sampling.maxSamples(), terminateOnNullReject = true, tracker=tracker) { sampling.sample() }
    }

    fun runBetting(
        config: AuditConfig,
        N: Int,
        cassorter: ClcaAssorterOneAudit,
        sampling: Sampling,
        bettingFn: BettingFn,
    ): TestH0Result {

        // TODO something better ??

        val testFn = BettingMart(
            bettingFn = bettingFn,
            N = N,
            sampleUpperBound = cassorter.upperBound(),
            riskLimit = config.riskLimit,
            withoutReplacement = true
        )

        // TODO make optional
        val sequences = testFn.setDebuggingSequences()
        val tracker = ClcaErrorTracker(cassorter.noerror(), cassorter.assorter.upperBound(), sequences) // track pool data; something better to do?

        // TODO how come you dont need startingTestStatistic: Double,
        return testFn.testH0(sampling.maxSamples(), terminateOnNullReject = true, tracker=tracker) { sampling.sample() }
    }
}