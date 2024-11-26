package org.cryptobiotic.rlauxe.core

enum class AuditType { POLLING, CARD_COMPARISON, ONEAUDIT }

data class AuditConfig(val auditType: AuditType,
                       val riskLimit: Double,
                       val seed: Long,
                       val ntrials: Int = 100,
                       val quantile: Double = .80,
                       val fuzzPct: Double = .001,
                       val p1: Double = 1.0e-2,
                       val p2: Double = 1.0e-4,
                       val p3: Double = 1.0e-2,
                       val p4: Double = 1.0e-4,
                       val d1: Int = 100,  // for trunc_shrinkage
                       val d2: Int = 100,
)