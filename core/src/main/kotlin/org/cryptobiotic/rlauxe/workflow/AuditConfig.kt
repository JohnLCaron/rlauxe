package org.cryptobiotic.rlauxe.workflow

enum class AuditType { POLLING, CARD_COMPARISON, ONEAUDIT }

data class AuditConfig(val auditType: AuditType,
                       val hasStyles: Boolean,
                       val seed: Long,
                       val fuzzPct: Double?,
                       val errorRates: List<Double>? = null,
                       val useGeneratedErrorRates: Boolean = false,
                       val ntrials: Int = 100, // when estimating the sample size
                       val quantile: Double = 0.50, // use this percentile success for estimated sample size
                       val d1: Int = 100,  // for trunc_shrinkage
                       val d2: Int = 100,
                       val riskLimit: Double = 0.05,
                       val samplePctCutoff: Double = 0.33, // dont sample more than this pct of N
)