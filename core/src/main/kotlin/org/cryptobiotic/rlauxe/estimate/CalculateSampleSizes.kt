package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.EstimationRoundResult
import kotlin.math.max
import kotlin.math.min

private val logger = KotlinLogging.logger("CalculateSampleSizes")

// not setting assertionRound.estNewMvrs, contestRound.estNewMvrs; maybe only informative ??
fun calculateSampleSizes(
    config: AuditConfig,
    auditRound: AuditRoundIF,
    overwrite: Boolean,
) {
    require(config.isOA || config.isClca)

    auditRound.contestRounds.filter { !it.done }.forEach { contestRound ->
        var maxEstMvrs = 0
        var maxNewEstMvrs = 0
        contestRound.assertionRounds.forEach { assertionRound ->

            val (calcNewSamples, _) = assertionRound.calcNewMvrsNeeded(contestRound.contestUA, config.clcaConfig.maxLoss, config.riskLimit)
            if (calcNewSamples < 0) {
                logger.warn { "calculateSampleSizes $calcNewSamples for contest ${contestRound.contestUA.id} assertion ${assertionRound.assertion.assorter.shortName()} "}
                assertionRound.calcNewMvrsNeeded(contestRound.contestUA, config.clcaConfig.maxLoss, config.riskLimit) // debug
                // perhaps fall back to a simulation ??
            }
            assertionRound.estNewMvrs = calcNewSamples

            val prevSampleSize = assertionRound.prevAuditResult?.samplesUsed ?: 0
            assertionRound.estMvrs = min(calcNewSamples + prevSampleSize, contestRound.contestUA.Npop)

            val simResult  = assertionRound.estimationResult
            if (simResult == null) {
                assertionRound.estimationResult = EstimationRoundResult(
                    auditRound.roundIdx,
                    "calculateSampleSizes",
                    calcNewMvrsNeeded = calcNewSamples,
                    startingTestStatistic = 1.0,
                    startingErrorRates = null,
                    estimatedDistribution = emptyList(),
                    ntrials = 0,
                    simNewMvrsNeeded = calcNewSamples
                )
            } else {
                assertionRound.estimationResult = simResult.copy(calcNewMvrsNeeded = calcNewSamples)
                    /* auditRound.roundIdx,
                    "calculateSampleSizes",
                    calcNewMvrsNeeded = calcNewSamples,
                    startingTestStatistic = 1.0,
                    startingErrorRates = simResult.startingErrorRates,
                    estimatedDistribution = simResult.estimatedDistribution,
                    ntrials = simResult.ntrials,
                    simNewMvrsNeeded = simResult.simNewMvrsNeeded
                ) */
            }

            maxNewEstMvrs = max( maxNewEstMvrs, calcNewSamples)
            maxEstMvrs = max( maxNewEstMvrs, assertionRound.estMvrs)
        }

        if (overwrite) {
            contestRound.estNewMvrs = maxNewEstMvrs
            contestRound.estMvrs = maxEstMvrs
        }
    }
}
