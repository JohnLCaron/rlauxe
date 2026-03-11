package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.audit.EstimationRoundResult
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPool
import org.cryptobiotic.rlauxe.util.Quantiles.percentiles
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.workflow.CardManifest
import kotlin.math.max
import kotlin.math.min

private val logger = KotlinLogging.logger("EstimateOneAudit2")

// only diff with round > 1 is that we want to incorporate the measured errors from previous rounds ??
// can we use vunderPool to do so ?? no. that only uses fuzz
// just change the assortValue randomly p percent of the time. can do the same for clca.
// also TODO simulate entire audit each round, apply quantiles to that distribution
class EstimateOneAudit2(
    val config: AuditConfig,
    val roundIdx: Int,
    val contests: List<ContestRound>,
    val pools: List<OneAuditPool>,
    val cardManifest: CardManifest,
) {
    val contestsToAudit = contests.filter { !it.done && it.included }

    fun run(): List<RunRepeatedResult> {
        if (contestsToAudit.isEmpty())
            return emptyList()

        contestsToAudit.forEach { contestRound ->
            var maxEst = 0
            var maxNewEst = 0
            contestRound.assertionRounds.forEach { assertionRound ->
                val prevAssertionRound = assertionRound.prevAssertionRound!!
                val prevEstimation: EstimationRoundResult = prevAssertionRound.estimationResult!!
                val distribution = prevEstimation.estimatedDistribution
                val pct = if (roundIdx == 2) 80 else 96
                val nmvrs = roundUp(percentiles().index(pct).compute(*distribution.toIntArray()))
                val newMvrs = nmvrs - prevEstimation.simNewMvrsNeeded

                assertionRound.estMvrs = nmvrs
                assertionRound.estNewMvrs = newMvrs
                assertionRound.estimationResult = EstimationRoundResult(
                    roundIdx,
                    "EstimateOneAudit2",
                    calcNewMvrsNeeded = prevEstimation.calcNewMvrsNeeded,
                    startingTestStatistic = 1.0 / prevAssertionRound.auditResult!!.plast,
                    startingErrorRates = emptyMap(), // TODO capture nphantoms ?
                    estimatedDistribution = distribution,
                    lastIndex = prevEstimation.lastIndex,
                    quantile = pct,
                    ntrials = 0,
                    simNewMvrsNeeded = newMvrs,
                    simMvrsNeeded = nmvrs,
                )

                maxEst = max(maxEst, nmvrs)
                maxNewEst = max(maxNewEst, newMvrs)
            }
            contestRound.estMvrs = maxEst
            contestRound.estNewMvrs = maxNewEst
        }

        // TODO maybe you should just rerun the estimation always, since you might have some errorCounts to integrate ??
        // if errorCounts vary by assertion, you want to run them for each assertion....
        // logger.info { "EstimateOneAudit2 ntrials=${config.nsimEst} ncontests=${contestsToAudit.size} took $stopwatch" }

        return emptyList() // bogus
    }
}
