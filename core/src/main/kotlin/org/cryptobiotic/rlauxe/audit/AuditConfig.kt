package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.util.secureRandom
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode
import kotlin.Int

data class AuditConfig(
    val auditType: AuditType,
    val riskLimit: Double = 0.05,
    val seed: Long = secureRandom.nextLong(), // determines sample order. set carefully to ensure truly random.

    // simulation control
    val nsimEst: Int = 100, // number of simulation estimation trials
    val quantile: Double = 0.80, // use this percentile success for estimated sample size
    val simFuzzPct: Double? = null, // for simulating the estimation fuzzing
    val simulationStrategy: SimulationStrategy =  SimulationStrategy.optimistic,

    // consistentSampling: contestRound.status
    val contestSampleCutoff: Int? = 20000, // max number of cvrs for any one contest, set to null to use all
    val auditSampleCutoff: Int? = 100000, // max number of cvrs for any one contest, set to null to use all
    val removeCutoffContests: Boolean = (contestSampleCutoff != null), // remove contests that need more samples than contestSampleCutoff
    val maxSamplePct: Double = 0.0, // do not audit contests with (estimated nmvrs / contestNc) greater than this
    val removeMaxContests: Int? = null, // remove top n estimated nmvrs contests

    // checkContestsCorrectlyFormed: preAuditStatus
    val minRecountMargin: Double = 0.005, // do not audit contests less than this recount margin
    val minMargin: Double = 0.0, // do not audit contests less than this margin TODO really it should be noerror?
    // val removeTooManyPhantoms: Boolean = false, // do not audit contests if phantoms > margin TODO deprecated

    // this turns the audit into a "risk measuring" audit; terminateOnNullReject = false;
    val auditSampleLimit: Int? = null, // limit audit sample size; audit all samples, ignore risk limit

    val pollingConfig: PollingConfig = PollingConfig(),
    val clcaConfig: ClcaConfig = ClcaConfig(),

    val persistedWorkflowMode: PersistedWorkflowMode =
        if (auditType.isClca()) PersistedWorkflowMode.testClcaSimulated else PersistedWorkflowMode.testPrivateMvrs,

    val quantile1: Double = 0.50, // use this percentile success for estimated sample size
    val version: Double = 2.0,
) {
    val isClca = auditType == AuditType.CLCA
    val isOA = auditType == AuditType.ONEAUDIT
    val isPolling = auditType == AuditType.POLLING
    var electionName: String? = null

    init {
        if (persistedWorkflowMode == PersistedWorkflowMode.testClcaSimulated && !isClca) {
            throw RuntimeException("PersistedWorkflowMode.testClcaSimulated must be CLCA")
        }
    }

    fun isRiskMeasuringAudit() = auditSampleLimit != null

    // only used in PersistedMvrManagerTest
    fun mvrFuzzPct(): Double {
        return when (auditType) {
            AuditType.POLLING -> 0.0
            AuditType.CLCA, AuditType.ONEAUDIT -> clcaConfig.fuzzMvrs  ?: 0.0
        }
    }

    override fun toString() = buildString {
        appendLine("AuditConfig(auditType=$auditType, riskLimit=$riskLimit, seed=$seed persistedWorkflowMode=$persistedWorkflowMode," )
        append(" minRecountMargin=$minRecountMargin, minMargin=$minMargin,")
        if (removeMaxContests != null) { append(" removeMaxContests=$removeMaxContests,") }
        if (contestSampleCutoff != null) { append(" contestSampleCutoff=$contestSampleCutoff, auditSampleCutoff=$auditSampleCutoff, removeCutoffContests=$removeCutoffContests,") }
        if (auditSampleLimit != null) { append(" auditSampleLimit=$auditSampleLimit (risk measuring audit)") }
        appendLine()
        appendLine("  nsimEst=$nsimEst, quantile1=$quantile1, quantile=$quantile, simFuzzPct=${simFuzzPct}, simulationStrategy=$simulationStrategy, mvrFuzzPct=${mvrFuzzPct()},")

        // if (skipContests.isNotEmpty()) { appendLine("  skipContests=$skipContests") }
        when (auditType) {
            AuditType.POLLING -> appendLine("  $pollingConfig")
            AuditType.CLCA, AuditType.ONEAUDIT -> appendLine("  $clcaConfig")
        }
    }

    fun strategy() : String {
        return when (auditType) {
            AuditType.POLLING -> "polling"
            AuditType.CLCA, AuditType.ONEAUDIT -> clcaConfig.strategy.toString()
        }
    }
}
