package org.cryptobiotic.rlauxe.workflow

enum class AuditType { POLLING, CARD_COMPARISON, ONEAUDIT }

class AuditConfig(val auditType: AuditType,
                       val hasStyles: Boolean,
                       val seed: Long,
                       val fuzzPct: Double? = null,
                       val errorRates: List<Double>? = null,
                       val ntrials: Int = 100, // when estimating the sample size
                       val quantile: Double = 0.80, // use this percentile success for estimated sample size
                       val d1: Int = 100,  // for trunc_shrinkage
                       val d2: Int = 100,
                       val riskLimit: Double = 0.05,
                       val samplePctCutoff: Double = 0.33, // dont sample more than this pct of N
                       pollingConfigInput: PollingConfig? = null,
                       clcaConfigInput: ClcaConfig? = null,
) {
    val clcaConfig: ClcaConfig?
    val pollingConfig: PollingConfig? // also used for oneaudit

    // TODO temporary backfill
    init {
        pollingConfig = if (auditType != AuditType.CARD_COMPARISON && pollingConfigInput == null) {
            PollingConfig(fuzzPct = fuzzPct, d = d1)
        } else {
            pollingConfigInput
        }

        clcaConfig = if (auditType == AuditType.CARD_COMPARISON && clcaConfigInput == null) {
            if (fuzzPct != null) ClcaConfig(ClcaSimulationType.fuzzPct, fuzzPct=fuzzPct, d1=d1, d2=d2)
            else if (errorRates != null) ClcaConfig(ClcaSimulationType.apriori, errorRates=errorRates, d1=d1, d2=d2)
            else ClcaConfig(ClcaSimulationType.oracle)
        } else {
            clcaConfigInput
        }


    }
}

// oracle: use actual measured error rates, testing only
// noerror: assume no errors, with adaption
// fuzzPct: model errors with fuzz simulation, with adaption
// apriori: pass in apriori errorRates, with adaption
enum class ClcaSimulationType { oracle, noerror, fuzzPct, apriori }
data class ClcaConfig(
    val simType: ClcaSimulationType,
    val fuzzPct: Double? = null, // needed when fuzzPct
    val errorRates: List<Double>? = null, // use as apriori for adaptive
    val d1: Int = 100,  // for p2o, p2u
    val d2: Int = 100,  // for p1o, p1u
)

data class PollingConfig(
    val fuzzPct: Double? = null, // needed when fuzzPct
    val d: Int = 100,  // for shrink_trunkage
)

