package org.cryptobiotic.rlauxe.workflow

enum class AuditType { POLLING, CARD_COMPARISON, ONEAUDIT }

data class AuditConfig(val auditType: AuditType,
                       val riskLimit: Double,
                       val seed: Long,
                       val fuzzPct: Double?,
                       val minMargin: Double = .01, // too small to sample
                       val ntrials: Int = 100, // when estimating the sample size
                       val quantile: Double = 0.80, // sample size of 80th percentile success
                       val d1: Int = 100,  // for trunc_shrinkage
                       val d2: Int = 100,
)