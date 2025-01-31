package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.ErrorRates
import org.cryptobiotic.rlauxe.util.secureRandom

enum class AuditType { POLLING, CARD_COMPARISON, ONEAUDIT }

data class AuditConfig(
    val auditType: AuditType,
    val hasStyles: Boolean,
    val seed: Long = secureRandom.nextLong(),
    val riskLimit: Double = 0.05,
    val nsimEst: Int = 100, // number of simulation estimations
    val quantile: Double = 0.80, // use this percentile success for estimated sample size
    val samplePctCutoff: Double = 0.33, // dont sample more than this pct of N TODO
    val pollingConfig: PollingConfig = PollingConfig(),
    val clcaConfig: ClcaConfig = ClcaConfig(ClcaStrategyType.oracle),
    val oaConfig: OneAuditConfig = OneAuditConfig(OneAuditStrategyType.standard),
    val version: Double = 1.0,
)


// oracle: use actual measured error rates, testing only
// noerror: assume no errors, with adaptation
// fuzzPct: model errors with fuzz simulation, with adaptation
// apriori: pass in apriori errorRates, with adaptation
enum class ClcaStrategyType { oracle, noerror, fuzzPct, apriori }
data class ClcaConfig(
    val strategy: ClcaStrategyType,
    val simFuzzPct: Double? = null, // use to generate apriori errorRates for simulation
    val errorRates: ErrorRates? = null, // use as apriori
    val d: Int = 100,  // shrinkTrunc weight for error rates
)

data class PollingConfig(
    val fuzzPct: Double? = null, // for the estimation
    val d: Int = 100,  // shrinkTrunc weight
)

enum class OneAuditStrategyType { standard, max99 }
data class OneAuditConfig(
    val strategy: OneAuditStrategyType,
    val fuzzPct: Double? = null, // for the estimation
    val d: Int = 100,  // shrinkTrunc weight
)


