package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.EstimationRoundResult
import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.util.roundUp
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

private val logger = KotlinLogging.logger("CalculateSampleSizes")

// originally this was to replace estimation for round 1.
// Now, after estimation, calculateSampleSizes() is called and assertionRound.calcNewMvrsNeeded is written to
// assertionRound.estNewMvrs, assertionRound.estMvrs,
// estimationResult.calcNewMvrsNeeded
// and if (overwrite) contestRound.estNewMvrs, contestRound.maxNewEstMvrs

// TODO not needed I think
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

            val calcNewSamples = assertionRound.calcNewMvrsNeeded(contestRound.contestUA, config)
            if (calcNewSamples < 0) {
                logger.warn { "calculateSampleSizes $calcNewSamples for contest ${contestRound.contestUA.id} assertion ${assertionRound.assertion.assorter.shortName()} "}
                assertionRound.calcNewMvrsNeeded(contestRound.contestUA, config) // debug
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
                    simNewMvrsNeeded = calcNewSamples,
                    quantile=0, lastIndex=0,
                )
            } else {
                assertionRound.estimationResult = simResult.copy(calcNewMvrsNeeded = calcNewSamples)
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

/**
 * From colorado-rla Audit.optimistic().
 * Based on SuperSimple paper, generalization of equations in section 4.1, esp eq 24.
 * Computes the expected number of ballots to audit overall given the specified numbers of over- and understatements.
 *
 * @param gamma the "error inflator" parameter. error inflation factor γ ≥ 100%.
 *   γ controls a tradeoff between initial sample size and the amount of additional counting required when the
 *   sample finds too many overstatements, especially two-vote overstatements.
 *   The larger γ is, the larger the initial sample needs to be, but the less additional counting will be required
 *   if the sample finds a two-vote overstatement or a large number of one-vote maximum overstatements. (paper has 1.1)
 * @param twoOver the number of two-vote overstatements
 * @param oneOver the number of one-vote overstatements
 * @param oneUnder the number of one-vote understatements
 * @param twoUnder the number of two-vote understatements
 */
fun estimateCorla(
    riskLimit: Double,
    dilutedMargin: Double,
    gamma: Double = 1.03905,
    twoOver: Int = 0,
    oneOver: Int = 0,
    oneUnder: Int = 0,
    twoUnder: Int = 0,
): Int {
    val two_under_term = twoUnder * ln( 1 + 1 / gamma)
    val one_under_term = oneUnder * ln( 1 + 1 / (2 * gamma))
    val one_over_term = oneOver * ln( 1 - 1 / (2 * gamma))
    val two_over_term = twoOver * ln( 1 - 1 / gamma)

    // "sample-size multiplier" rho is independent of margin
    val rho: Double = -(2.0 * gamma) * (ln(riskLimit) + two_under_term + one_under_term + one_over_term + two_over_term)
    val r = ceil(rho / dilutedMargin)  // round up
    val over_under_sum = (twoUnder + oneUnder + oneOver + twoOver).toDouble()
    // println("   rho=$rho r=$r")
    return roundUp(max(r, over_under_sum))
}

// TODO not including the phantoms ....
fun estimateSampleSizePayloads(
    alpha: Double,
    errors: ClcaErrorCounts, // (val errorCounts: Map<Double, Int>, val totalSamples: Int, val noerror: Double, val upper: Double) {
    ): Int {
    // noerror = 1/(2-v/u) = 1/(2-v) when u = 1
    // probably λ = 2 / gamma
    val maxRisk = 1.0 / 1.03905
    val lam = 2 * maxRisk
    val noerror = errors.noerror

    // payoff_noerror = (1 + λ * (noerror − 1/2))
    val payoffNoerror = 1 + lam * (noerror - 0.5)
    val lnPayoffNoerror = ln(payoffNoerror)

    // payoff the risk
    // payoff_noerror^n_risk > (1 / alpha)
    // n_risk = -ln(alpha) / ln(payoff_noerror)
    val n_risk = -ln(alpha) / lnPayoffNoerror

    // payoff the errors
    // payoff_tau = (1 + λ * (tau * noerror − 1/2))
    // payoff_noerror^n_tau * payoff_tau = 1.0                             (eq 1)
    // n_tau = -ln(payoff_tau) / ln(payoff_noerror)

    // estimated samples =
    // n = n_risk + Sum_taus { count * nTaus }

    val sumNTaus = errors.errorCounts.map { (bassort, count) ->
        val payoff = 1 + lam * (bassort - 0.5)
        val n_tau = -ln(payoff) / lnPayoffNoerror
        count * n_tau
    }.sum()

    return roundUp(n_risk + sumNTaus )
}