package org.cryptobiotic.rlauxe.workflow

enum class AuditType { POLLING, CARD_COMPARISON, ONEAUDIT }

class AuditConfig(val auditType: AuditType,
                  val hasStyles: Boolean,
                  val seed: Long,
                  val riskLimit: Double = 0.05,
                  val ntrials: Int = 100, // when estimating the sample size
                  val quantile: Double = 0.80, // use this percentile success for estimated sample size
                  val samplePctCutoff: Double = 0.33, // dont sample more than this pct of N
                  val pollingConfig: PollingConfig = PollingConfig(),
                  val clcaConfig: ClcaConfig = ClcaConfig(ClcaSimulationType.oracle),
)


// oracle: use actual measured error rates, testing only
// noerror: assume no errors, with adaption
// fuzzPct: model errors with fuzz simulation, with adaption
// apriori: pass in apriori errorRates, with adaption
enum class ClcaSimulationType { oracle, noerror, fuzzPct, apriori }
data class ClcaConfig(
    val simType: ClcaSimulationType,
    val fuzzPct: Double? = null, // needed when fuzzPct
    val errorRates: List<Double>? = null, // use as apriori for adaptive betting
    val d1: Int = 100,  // for p2o, p2u
    val d2: Int = 100,  // for p1o, p1u
)

data class PollingConfig(
    val fuzzPct: Double? = null, // needed when fuzzPct
    val d: Int = 100,  // for shrink_trunkage
)

