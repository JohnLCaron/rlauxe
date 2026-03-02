package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.betting.TausRates
import org.cryptobiotic.rlauxe.util.secureRandom
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode

enum class AuditType { POLLING, CLCA, ONEAUDIT;
    fun isClca() = (this == CLCA)
    fun isOA() = (this == ONEAUDIT)
    fun isPolling() = (this == POLLING)
}

// commit to at Election Creation
data class ElectionInfo(
    val auditType: AuditType,
    val ncards: Int,
    val ncontests: Int,
    val cvrsContainUndervotes: Boolean,
    val poolsHaveOneCardStyle: Boolean?,
)

// commit to at Audit Creation
data class AuditCreationConfig(
    val auditType: AuditType, // must agree with ElectionInfo
    val riskLimit: Double = 0.05,
    val seed: Long = secureRandom.nextLong(),
    val auditSampleLimit: Int? = null, // the number of samples we are willing to audit; this turns the audit into a "risk measuring" audit

    val persistedWorkflowMode: PersistedWorkflowMode, // real, testSimulated, testPrivateMvrs
    val fuzzMvrs: Double? = null, // used by PersistedMvrManagerTest to fuzz mvrs when persistedWorkflowMode=testSimulate
) {
    fun isRiskMeasuringAudit() = auditSampleLimit != null
}

// could vary by round; do we really need to retain this ?
data class AuditRoundConfig(
    val simulation: SimulationControl,
    val sampling: ContestSampleControl,
    val alphaMart: AlphaMartConfig,
    val bettingMart: BettingMartConfig,
)

data class SimulationControl(
    val nsimEst: Int = 100, // number of simulation estimation trials
    val quantile: Double = 0.80, // use this percentile success for estimated sample size
    val simFuzzPct: Double? = null, // for simulating the estimation fuzzing
    val simulationStrategy: SimulationStrategy =  SimulationStrategy.optimistic,
)

// a teach round the EA manually reviews the removed contests
data class ContestSampleControl(
    //// checkContestsCorrectlyFormed: preAuditStatus
    val minRecountMargin: Double = 0.005, // do not audit contests less than this recount margin
    val minMargin: Double = 0.0, // do not audit contests less than this margin TODO really it should be noerror?
    val removeTooManyPhantoms: Boolean = false, // do not audit contests if phantoms > margin // TODO not needed

    //// consistentSampling: contestRound.status, depends on having estimation
    val maxSamplePct: Double = 0.0, // do not audit contests with (estimated nmvrs / contestNc) greater than this
    val removeMaxContests: Int? = null, // remove top n estimated nmvrs contests, for plotting CaseStudiesRemoveNmax
    // conflating maximum in SubsetForEstimation, and maximum sample size per contest and maximum overall sample size
    val contestSampleCutoff: Int? = 30000, // use this number of cvrs in the estimation, set to null to use all
    val removeCutoffContests: Boolean = (contestSampleCutoff != null), // remove contests that need more samples than contestSampleCutoff
)

data class AlphaMartConfig(
    val d: Int = 100,  // shrinkTrunc weight
)

data class BettingMartConfig(
    val d: Int = 100,  // shrinkTrunc weight for error rates
    val maxLoss: Double = 0.90,  // max loss on any one bet, 0 < maxLoss < 1
    val apriori: TausRates = TausRates(emptyMap()),
)


