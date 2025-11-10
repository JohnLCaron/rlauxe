package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.ClcaErrorRates
import org.cryptobiotic.rlauxe.util.secureRandom

enum class AuditType { POLLING, CLCA, ONEAUDIT;
    fun isClca() = (this == CLCA)
    fun isOA() = (this == ONEAUDIT)
    fun isPolling() = (this == POLLING)
}

data class AuditConfig(
    val auditType: AuditType,
    val hasStyle: Boolean, // has Card Style Data (CSD), i.e. we know which contests each card/ballot contains
    val riskLimit: Double = 0.05,
    val seed: Long = secureRandom.nextLong(), // determines sample order. set carefully to ensure truly random.

    // simulation control
    val nsimEst: Int = 100, // number of simulation estimations
    val quantile: Double = 0.80, // use this percentile success for estimated sample size
    val contestSampleCutoff: Int? = 30000, // use this number of cvrs in the estimation, set to null to use all

    // audit sample size control
    val removeCutoffContests: Boolean = false, // remove contests that need more samples than contestSampleCutoff
    val minRecountMargin: Double = 0.005, // do not audit contests less than this recount margin
    val removeTooManyPhantoms: Boolean = false, // do not audit contests if phantoms > margin
    val auditSampleLimit: Int? = null, // limit audit sample size; audit all samples, ignore risk limit

    val pollingConfig: PollingConfig = PollingConfig(),
    val clcaConfig: ClcaConfig = ClcaConfig(ClcaStrategyType.previous),
    val oaConfig: OneAuditConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true),

    val version: Double = 1.2,
    val skipContests: List<Int> = emptyList()
) {
    val isClca = auditType == AuditType.CLCA
    val isOA = auditType == AuditType.ONEAUDIT
    val isPolling = auditType == AuditType.POLLING

    override fun toString() = buildString {
        appendLine("AuditConfig(auditType=$auditType, hasStyle=$hasStyle, riskLimit=$riskLimit, seed=$seed version=$version" )
        appendLine("  nsimEst=$nsimEst, quantile=$quantile, contestSampleCutoff=$contestSampleCutoff, auditSampleLimit=$auditSampleLimit, minRecountMargin=$minRecountMargin removeTooManyPhantoms=$removeTooManyPhantoms")
        if (skipContests.isNotEmpty()) { appendLine("  skipContests=$skipContests") }
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
// optimalComparison:  OptimalComparisonNoP1, assume P1 = 0, closed form solution for lamda
enum class ClcaStrategyType { oracle, noerror, fuzzPct, apriori, phantoms, previous, optimalComparison }
data class ClcaConfig(
    val strategy: ClcaStrategyType,
    val simFuzzPct: Double? = null, // use to generate apriori errorRates for simulation
    val errorRates: ClcaErrorRates? = null, // use as apriori errorRates for simulation and audit
    val d: Int = 100,  // shrinkTrunc weight for error rates
)

// reportedMean: eta0 = reportedMean, shrinkTrunk
// bet99: eta0 = reportedMean, 99% max bet
// eta0Eps: eta0 = upper*(1 - eps), shrinkTrunk (default strategy)
// optimalBet == optimalComparison = uses bettingMart with OptimalComparisonNoP1
// replace optimalBet with optimalComparison
enum class OneAuditStrategyType { reportedMean, bet99, eta0Eps, optimalBet, optimalComparison }
data class OneAuditConfig(
    val strategy: OneAuditStrategyType = OneAuditStrategyType.optimalComparison,
    val simFuzzPct: Double? = null, // for the estimation
    val d: Int = 100,  // shrinkTrunc weight
    val useFirst: Boolean = false, // use actual cvrs for estimation
)


