package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.betting.TausRates
import org.cryptobiotic.rlauxe.util.secureRandom
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode

private val logger = KotlinLogging.logger("ElectionInfo")

//// commit at createElectionRecord()

enum class AuditType { POLLING, CLCA, ONEAUDIT;
    fun isClca() = (this == CLCA)
    fun isOA() = (this == ONEAUDIT)
    fun isPolling() = (this == POLLING)
}

enum class PollingMode { withPools, withBatches, withoutBatches;
    fun withPools() = (this == withPools)
    fun withBatches() = (this == withBatches)
    fun withoutBatches() = (this == withoutBatches)
}

// what information cannot be changed by the auditors?
data class ElectionInfo(
    val electionName: String,
    val auditType: AuditType,
    val totalCardCount: Int,    // total cards in the election
    val contestCount: Int,

    val cvrsContainUndervotes: Boolean = true, // TODO where do we use this ??
    val poolsHaveOneCardStyle: Boolean? = null,
    val pollingMode: PollingMode? = null,

    val other: Map<String, Any> = emptyMap(),    // soft parameters to ease migration
)

//// commit at createAuditRecord(), after seed has been chosen

data class AuditCreationConfig(
    val auditType: AuditType, // must agree with ElectionInfo
    val riskLimit: Double = 0.05,
    val persistedWorkflowMode: PersistedWorkflowMode =
        if (auditType.isClca()) PersistedWorkflowMode.testClcaSimulated else PersistedWorkflowMode.testPrivateMvrs,

    val seed: Long = secureRandom.nextLong(),
    val riskMeasuringSampleLimit: Int? = null, // the number of samples we are willing to audit; this turns the audit into a "risk measuring" audit
    val other: Map<String, Any> = emptyMap(),    // soft parameters
) {

    init {
        if (persistedWorkflowMode == PersistedWorkflowMode.testClcaSimulated && !auditType.isClca()) {
            throw RuntimeException("PersistedWorkflowMode.testClcaSimulated must be CLCA")
        }
    }

    fun isRiskMeasuringAudit() = riskMeasuringSampleLimit != null

    companion object {
        fun fromAuditConfig(config: AuditConfig): AuditCreationConfig {
            return AuditCreationConfig(config.auditType, config.riskLimit, config.persistedWorkflowMode, config.seed, config.auditSampleLimit)
        }
    }
}

//// can configure each round seperately; commit when each round has been audited

data class AuditRoundConfig(
    val simulation: SimulationControl,
    val sampling: ContestSampleControl,
    val clcaConfig: ClcaConfig?,
    val pollingConfig: PollingConfig?,

    val other: Map<String, String> = emptyMap(),    // soft parameters
) {

    companion object {
        val CLCA = AuditRoundConfig(SimulationControl(), ContestSampleControl(), ClcaConfig(), null)
        val POLLING = AuditRoundConfig(SimulationControl(),
            ContestSampleControl(contestSampleCutoff=20000, auditSampleCutoff=100_000), null, PollingConfig())

        fun standard(auditType: AuditType) = if (auditType.isPolling()) POLLING else CLCA

        fun fromAuditConfig(config: AuditConfig): AuditRoundConfig {
            val simulation =
                SimulationControl(config.nsimEst, listOf(config.quantile1, config.quantile), config.simFuzzPct)

            val other = mutableMapOf<String, String>()
            if (config.removeMaxContests != null) other["removeMaxContests"] = config.removeMaxContests.toString()
            val sampling = ContestSampleControl(
                config.minRecountMargin, config.minMargin, config.maxSamplePct, config.contestSampleCutoff, config.auditSampleCutoff,
                removeCutoffContests=config.removeCutoffContests,
                other=other,
            )

            return AuditRoundConfig(simulation, sampling, config.clcaConfig, config.pollingConfig)
        }
    }
}

// optimistic: round 1 assume no errors, subsequent rounds use measured error rates
// reglar" old estimateSampleSizes(), deprecated
enum class SimulationStrategy { regular, optimistic  }

data class SimulationControl(
    val nsimEst: Int = 20, // number of simulation estimation trials
    val estPercentSuccess: List<Double> = listOf(0.50, 0.80), // use this percentile success for estimated sample size in round idx+1
    val simFuzzPct: Double? = null, // for estimation fuzzing
    val simulationStrategy: SimulationStrategy = SimulationStrategy.optimistic
)

// at each round the EA manually reviews the removed contests; these parameters automate that for testing and simulation
data class ContestSampleControl(
    //// checkContestsCorrectlyFormed: preAuditStatus
    val minRecountMargin: Double = 0.005, // do not audit contests less than this recount margin
    val minMargin: Double = 0.0, // do not audit contests less than this margin TODO really it should be noerror for clca?

    //// consistentSampling: contestRound.status, depends on having estimation
    val maxSamplePct: Double = 0.0, // do not audit contests with (estimated nmvrs / contestNc) greater than this // TODO should be Npop
    val contestSampleCutoff: Int? = 1000, // max number of cvrs for any one contest, set to null to use all
    val auditSampleCutoff: Int? = 10000, // max number of cvrs in the audit, set to null to use all
    val removeCutoffContests: Boolean = (contestSampleCutoff != null || auditSampleCutoff != null), // TODO keep this ??

    // soft parameters
    val other: Map<String, String> = emptyMap(),    // soft parameters to ease migration
    // val removeMaxContests: Int? = null, // remove top n estimated nmvrs contests, for plotting CaseStudiesRemoveNmax
) {

    fun removeMaxContests(): Int? {
        val stringValue = other[removeMaxContests] ?: return null
        try {
            val intValue = stringValue.toInt()
            return intValue
        } catch (e: NumberFormatException) {
            logger.error { "removeMaxContests cant parse '$stringValue' as Int"}
            return null
        }
    }

    companion object {
        val removeMaxContests = "removeMaxContests"
        val NONE = ContestSampleControl(0.0, 0.0, 0.0, null, null, false,emptyMap())
    }
}

data class PollingConfig(
    val d: Int = 100,  // shrinkTrunc weight
    val mode: PollingMode = PollingMode.withPools, // move to ElectionInfo, cant be changed
)

enum class ClcaStrategyType { generalAdaptive }
data class ClcaConfig(
    val strategy: ClcaStrategyType = ClcaStrategyType.generalAdaptive,
    val fuzzMvrs: Double? = null, // used by PersistedMvrManagerTest to fuzz mvrs when persistedWorkflowMode=testSimulate
    val d: Int = 100,  // shrinkTrunc weight for error rates
    val maxLoss: Double = 1.0 / 1.03905,  // max loss on any one bet, 0 < maxLoss < 1 //  = .9624 from Corla gamma = 1.03905;
                                          // SHANGRLA has gamma = 1.1 which is ~ 1/.9
    val apriori: TausRates = TausRates(emptyMap()),
)



