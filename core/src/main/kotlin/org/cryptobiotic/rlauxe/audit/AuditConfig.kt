package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.ClcaErrorRates
import org.cryptobiotic.rlauxe.util.secureRandom

enum class AuditType { POLLING, CLCA, ONEAUDIT }

data class AuditConfig(
    val auditType: AuditType,
    val hasStyles: Boolean, // has Card Style Data (CSD), i.e. we know which contests each card/ballot contains
    val riskLimit: Double = 0.05,
    val seed: Long = secureRandom.nextLong(), // determines sample order. set carefully to ensure truly random.

    // simulation control
    val nsimEst: Int = 100, // number of simulation estimations
    val quantile: Double = 0.80, // use this percentile success for estimated sample size
    val sampleLimit: Int = -1, // dont sample more than this, -1 means dont use
    val minRecountMargin: Double = 0.005, // do not audit contests less than this recount margin
    val removeTooManyPhantoms: Boolean = false, // do not audit contests if phantoms > margin

    val pollingConfig: PollingConfig = PollingConfig(),
    val clcaConfig: ClcaConfig = ClcaConfig(ClcaStrategyType.phantoms),
    val oaConfig: OneAuditConfig = OneAuditConfig(OneAuditStrategyType.optimalBet),
    val version: Double = 1.0,
) {
    val isClca = auditType == AuditType.CLCA || auditType == AuditType.ONEAUDIT

    override fun toString() = buildString {
        appendLine("AuditConfig(auditType=$auditType, hasStyles=$hasStyles, riskLimit=$riskLimit, seed=$seed")
        appendLine("  nsimEst=$nsimEst, quantile=$quantile, sampleLimit=$sampleLimit, minRecountMargin=$minRecountMargin version=$version")
        when (auditType) {
            AuditType.POLLING -> appendLine("  $pollingConfig")
            AuditType.CLCA -> appendLine("  $clcaConfig")
            AuditType.ONEAUDIT -> appendLine("  $oaConfig")
        }
    }
    fun strategy() : String {
        return when (auditType) {
            AuditType.POLLING -> "polling"
            AuditType.CLCA -> clcaConfig.strategy.toString()
            AuditType.ONEAUDIT -> oaConfig.strategy.toString()
        }
    }
}

data class PollingConfig(
    val simFuzzPct: Double? = null, // for the estimation
    val d: Int = 100,  // shrinkTrunc weight TODO study what this should be, eg for noerror assumption?
)

// oracle: use actual measured error rates, testing only
// noerror: assume no errors, with adaptation
// fuzzPct: model errors with fuzz simulation, with adaptation
// apriori: pass in apriori errorRates, with adaptation
// phantoms: use phantom rates for apriori
// previous: use phantom rates for apriori, then previous round measured
enum class ClcaStrategyType { oracle, noerror, fuzzPct, apriori, phantoms, previous }
data class ClcaConfig(
    val strategy: ClcaStrategyType,
    val simFuzzPct: Double? = null, // use to generate apriori errorRates for simulation
    val errorRates: ClcaErrorRates? = null, // use as apriori errorRates for simulation and audit
    val d: Int = 100,  // shrinkTrunc weight for error rates
)

// reportedMean: eta0 = reportedMean, shrinkTrunk
// bet99: eta0 = reportedMean, 99% max bet
// eta0Eps: eta0 = upper*(1 - eps), shrinkTrunk (default strategy)
enum class OneAuditStrategyType { reportedMean, bet99, eta0Eps, optimalBet }
data class OneAuditConfig(
    val strategy: OneAuditStrategyType = OneAuditStrategyType.optimalBet,
    val simFuzzPct: Double? = null, // for the estimation
    val d: Int = 100,  // shrinkTrunc weight
)


