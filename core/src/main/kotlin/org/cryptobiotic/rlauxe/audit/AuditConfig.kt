package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.util.secureRandom
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode

enum class AuditType { POLLING, CLCA, ONEAUDIT;
    fun isClca() = (this == CLCA)
    fun isOA() = (this == ONEAUDIT)
    fun isPolling() = (this == POLLING)
}

// TODO add directory to config
data class AuditConfig(
    val auditType: AuditType,
    val hasStyle: Boolean = true, // TODO deprecated?; perhaps useful when all pools have hasSingleCardStyle=true ?? etc
    val riskLimit: Double = 0.05,
    val seed: Long = secureRandom.nextLong(), // determines sample order. set carefully to ensure truly random.

    // simulation control
    val nsimEst: Int = 100, // number of simulation estimation trials
    val quantile: Double = 0.80, // use this percentile success for estimated sample size
    val contestSampleCutoff: Int? = 30000, // use this number of cvrs in the estimation, set to null to use all
    val simFuzzPct: Double? = null, // for simulating the estimation fuzzing

    // audit sample size control
    val removeCutoffContests: Boolean = false, // remove contests that need more samples than contestSampleCutoff
    val minRecountMargin: Double = 0.005, // do not audit contests less than this recount margin
    val removeTooManyPhantoms: Boolean = false, // do not audit contests if phantoms > margin
    val auditSampleLimit: Int? = null, // limit audit sample size; audit all samples, ignore risk limit

    val pollingConfig: PollingConfig = PollingConfig(),
    val clcaConfig: ClcaConfig = ClcaConfig(),
    val oaConfig: OneAuditConfig = OneAuditConfig(),

    val persistedWorkflowMode: PersistedWorkflowMode =  PersistedWorkflowMode.testSimulated,
    val skipContests: List<Int> = emptyList(),
    val version: Double = 2.0,
) {
    val isClca = auditType == AuditType.CLCA
    val isOA = auditType == AuditType.ONEAUDIT
    val isPolling = auditType == AuditType.POLLING

    fun mvrFuzzPct(): Double {
        return when (auditType) {
            AuditType.POLLING -> clcaConfig.fuzzMvrs ?: 0.0
            AuditType.CLCA -> clcaConfig.fuzzMvrs  ?: 0.0
            AuditType.ONEAUDIT -> clcaConfig.fuzzMvrs  ?: 0.0
        }
    }

    override fun toString() = buildString {
        appendLine("AuditConfig(auditType=$auditType, riskLimit=$riskLimit, seed=$seed persistedWorkflowMode=$persistedWorkflowMode" )
        append("  minRecountMargin=$minRecountMargin removeTooManyPhantoms=$removeTooManyPhantoms")
        if (contestSampleCutoff != null) { append(" contestSampleCutoff=$contestSampleCutoff removeCutoffContests=$removeCutoffContests") }
        if (auditSampleLimit != null) { append(" auditSampleLimit=$auditSampleLimit (risk measuring audit)") }
        appendLine()
        appendLine("  nsimEst=$nsimEst, quantile=$quantile, simFuzzPct=${simFuzzPct}, mvrFuzzPct=${mvrFuzzPct()},")

        if (skipContests.isNotEmpty()) { appendLine("  skipContests=$skipContests") }
        when (auditType) {
            AuditType.POLLING -> appendLine("  $pollingConfig")
            AuditType.CLCA -> appendLine("  $clcaConfig")
            AuditType.ONEAUDIT -> {
                appendLine("  $oaConfig")
            }
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

// uses AlphaMart
data class PollingConfig(
    val d: Int = 100,  // shrinkTrunc weight TODO study what this should be, eg for noerror assumption?
)

// oracle: use actual measured error rates, testing only
// fuzzPct: model errors with fuzz simulation at first, then measured
// apriori: pass in apriori errorRates at first, then measured
// phantoms: use phantom rates at first.
// previous: use phantom rates at first, then measured.
// optimalComparison:  OptimalComparisonNoP1, assume P1 = 0, closed form solution for lamda

// Error Rates: the minimum p1o is always the phantom rate. Subsequent rounds, always use measured rates.
//  apriori: pass in apriori errorRates for first round.
//  fuzzPct: ClcaErrorTable.getErrorRates(contest.ncandidates, clcaConfig.simFuzzPct) for first round.
//  oracle: use actual measured error rates for first round. (violates martingale condition)

// TODO only generalAdaptive is used, remove others
enum class ClcaStrategyType { generalAdaptive, apriori, fuzzPct, oracle  }
data class ClcaConfig(
    val strategy: ClcaStrategyType = ClcaStrategyType.generalAdaptive,
    val fuzzMvrs: Double? = null, // used by PersistedMvrManagerTest to fuzz mvrs when persistedWorkflowMode=testSimulate
    val d: Int = 100,  // shrinkTrunc weight for error rates
    val maxLoss: Double = 0.90,  // max loss on any one bet, 0 < maxLoss < 1
    val cvrsContainUndervotes: Boolean = true,
)

// simulate: simulate for estimation
// calcMvrsNeeded: calculate for estimation
enum class OneAuditStrategyType { simulate, calcMvrsNeeded }
data class OneAuditConfig(
    val strategy: OneAuditStrategyType = OneAuditStrategyType.simulate,
)

