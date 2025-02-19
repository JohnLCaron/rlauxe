package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.ErrorRates
import org.cryptobiotic.rlauxe.util.secureRandom

enum class AuditType { POLLING, CARD_COMPARISON, ONEAUDIT }

data class AuditConfig(
    val auditType: AuditType,
    val hasStyles: Boolean,
    val riskLimit: Double = 0.05,
    val seed: Long = secureRandom.nextLong(), // determines smaple order. set carefully to ensure truly random.

    // simulation control
    val nsimEst: Int = 100, // number of simulation estimations
    val quantile: Double = 0.80, // use this percentile success for estimated sample size
    val samplePctCutoff: Double = 1.0, // dont sample more than this pct of N
    val minMargin: Double = 0.0, // do not audit contests less than this reported margin
    val removeTooManyPhantoms: Boolean = false, // do not audit contests if phantoms > margin

    val pollingConfig: PollingConfig = PollingConfig(),
    val clcaConfig: ClcaConfig = ClcaConfig(ClcaStrategyType.noerror),
    val oaConfig: OneAuditConfig = OneAuditConfig(OneAuditStrategyType.default),
    val version: Double = 1.0,
) {
    override fun toString() = buildString {
        appendLine("AuditConfig(auditType=$auditType, hasStyles=$hasStyles, riskLimit=$riskLimit, seed=$seed")
        appendLine("  nsimEst=$nsimEst, quantile=$quantile, samplePctCutoff=$samplePctCutoff, minMargin=$minMargin version=$version")
        when (auditType) {
            AuditType.POLLING -> appendLine("  $pollingConfig")
            AuditType.CARD_COMPARISON -> appendLine("  $clcaConfig")
            AuditType.ONEAUDIT -> appendLine("  $oaConfig, ")
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
// previous: use error rates from previous batch
// phantoms: use phantom rates
// mixed: use phantom rates for audit, noerror for sample
enum class ClcaStrategyType { oracle, noerror, fuzzPct, apriori, previous, phantoms, mixed }
data class ClcaConfig(
    val strategy: ClcaStrategyType,
    val simFuzzPct: Double? = null, // use to generate apriori errorRates for simulation
    val errorRates: ErrorRates? = null, // use as apriori
    val d: Int = 100,  // shrinkTrunc weight for error rates
)

enum class OneAuditStrategyType { default, max99 }
data class OneAuditConfig(
    val strategy: OneAuditStrategyType,
    val simFuzzPct: Double? = null, // for the estimation
    val d: Int = 100,  // shrinkTrunc weight
)


