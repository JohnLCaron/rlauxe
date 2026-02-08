package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import kotlin.math.max
import kotlin.math.min

private val logger = KotlinLogging.logger("CalculateSampleSizes")

// not setting assertionRound.estNewMvrs, contestRound.estNewMvrs; maybe only informative ??
fun calculateSampleSizes(
    config: AuditConfig,
    auditRound: AuditRoundIF,
) {
    require(config.isOA || config.isClca)

    auditRound.contestRounds.filter { !it.done }.forEach { contestRound ->
        var maxEstMvrs = 0
        var maxNewEstMvrs = 0
        contestRound.assertionRounds.forEach { assertionRound ->

            val (calcNewSamples, _) = assertionRound.calcMvrsNeeded(contestRound.contestUA, config.clcaConfig.maxLoss, config.riskLimit, config.simFuzzPct)
            assertionRound.estNewMvrs = calcNewSamples

            val prevSampleSize = assertionRound.prevAuditResult?.samplesUsed ?: 0
            assertionRound.estMvrs = min(calcNewSamples + prevSampleSize, contestRound.contestUA.Npop)

            maxNewEstMvrs = max( maxNewEstMvrs, calcNewSamples)
            maxEstMvrs = max( maxNewEstMvrs, assertionRound.estMvrs)
        }
        contestRound.estNewMvrs = maxNewEstMvrs
        contestRound.estMvrs = maxEstMvrs
    }
}
