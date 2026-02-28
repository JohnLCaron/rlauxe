package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.betting.TausRates
import org.cryptobiotic.rlauxe.util.secureRandom
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode

enum class AuditType { POLLING, CLCA, ONEAUDIT;
    fun isClca() = (this == CLCA)
    fun isOA() = (this == ONEAUDIT)
    fun isPolling() = (this == POLLING)
}

// TODO not compelling; could have the ContestInfo here?
data class ElectionInfo(
    val auditType: AuditType,
    val ncards: Int,
    val ncontests: Int,
    val cvrsContainUndervotes: Boolean = true,
    val persistedWorkflowMode: PersistedWorkflowMode =  PersistedWorkflowMode.testSimulated,
)

data class ElectionInfo2(
    val auditType: AuditType,
    val ncards: Int,
    val ncontests: Int,
    val cvrsContainUndervotes: Boolean,
    val poolsHaveOneCardStyle: Boolean?,
)

// wed like the AuditConfig to not be needed for election creation.
data class AuditConfig(
    val auditType: AuditType,
    val riskLimit: Double = 0.05,
    val seed: Long = secureRandom.nextLong(), // determines sample order. set carefully to ensure truly random.

    // simulation control
    val nsimEst: Int = 100, // number of simulation estimation trials
    val quantile: Double = 0.80, // use this percentile success for estimated sample size
    val simFuzzPct: Double? = null, // for simulating the estimation fuzzing

    // audit sample size control
    val contestSampleCutoff: Int? = 30000, // use this number of cvrs in the estimation, set to null to use all
    val removeCutoffContests: Boolean = (contestSampleCutoff != null), // remove contests that need more samples than contestSampleCutoff
    val minRecountMargin: Double = 0.005, // do not audit contests less than this recount margin TODO really it should be noerror?
    val minMargin: Double = 0.0, // do not audit contests less than this margin
    val maxSamplePct: Double = 0.0, // do not audit contests with (estimated nmvrs / contestNc) greater than this
    val removeMaxContests: Int? = null, // remove top n estimated nmvrs contests
    val removeTooManyPhantoms: Boolean = false, // do not audit contests if phantoms > margin

    // this turns the audit into a "risk measuring" audit
    val auditSampleLimit: Int? = null, // limit audit sample size; audit all samples, ignore risk limit

    val pollingConfig: PollingConfig = PollingConfig(),
    val clcaConfig: ClcaConfig = ClcaConfig(),
    val oaConfig: OneAuditConfig = OneAuditConfig(),

    val persistedWorkflowMode: PersistedWorkflowMode =  PersistedWorkflowMode.testSimulated,
    val simulationStrategy: SimulationStrategy =  SimulationStrategy.optimistic,

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
        append("  minRecountMargin=$minRecountMargin minMargin=$minMargin removeTooManyPhantoms=$removeTooManyPhantoms")
        if (removeMaxContests != null) { append(" removeMaxContests=$removeMaxContests") }
        if (contestSampleCutoff != null) { append(" contestSampleCutoff=$contestSampleCutoff removeCutoffContests=$removeCutoffContests") }
        if (auditSampleLimit != null) { append(" auditSampleLimit=$auditSampleLimit (risk measuring audit)") }
        appendLine()
        appendLine("  nsimEst=$nsimEst, quantile=$quantile, simFuzzPct=${simFuzzPct}, simulationStrategy=$simulationStrategy, mvrFuzzPct=${mvrFuzzPct()},")

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

data class SimControl(
    // simulation control
    val nsimEst: Int = 100, // number of simulation estimation trials
    val quantile: Double = 0.80, // use this percentile success for estimated sample size
    val simFuzzPct: Double? = null, // for simulating the estimation fuzzing
)

data class ContestSampleControl(
    val contestSampleCutoff: Int? = 30000, // use this number of cvrs in the estimation, set to null to use all
    val removeCutoffContests: Boolean = (contestSampleCutoff != null), // remove contests that need more samples than contestSampleCutoff
    val minRecountMargin: Double = 0.005, // do not audit contests less than this recount margin TODO really it should be noerror?
    val minMargin: Double = 0.0, // do not audit contests less than this margin
    val maxSamplePct: Double = 0.0, // do not audit contests with (estimated nmvrs / contestNc) greater than this
    val removeMaxContests: Int? = null, // remove top n estimated nmvrs contests
    val removeTooManyPhantoms: Boolean = false, // do not audit contests if phantoms > margin
)

// optimistic: round 1 assume no errors, subsequent rounds use measured error rates
enum class SimulationStrategy { regular, optimistic  }

// could be called AlphaMartConfig
data class PollingConfig(
    val d: Int = 100,  // shrinkTrunc weight
)

enum class ClcaStrategyType { generalAdaptive, generalAdaptive2}
data class ClcaConfig(
    val strategy: ClcaStrategyType = ClcaStrategyType.generalAdaptive2,
    val fuzzMvrs: Double? = null, // used by PersistedMvrManagerTest to fuzz mvrs when persistedWorkflowMode=testSimulate
    val d: Int = 100,  // shrinkTrunc weight for error rates
    val maxLoss: Double = 0.90,  // max loss on any one bet, 0 < maxLoss < 1
    val apriori: TausRates = TausRates(emptyMap()),
)

// TODO dont need this
// simulate: simulate for estimation
enum class OneAuditStrategyType { simulate }
data class OneAuditConfig(
    val strategy: OneAuditStrategyType = OneAuditStrategyType.simulate,
)

