package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.util.secureRandom
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode
import kotlin.Double
import kotlin.Int

// practice run
data class Config(
    val electionInfo: ElectionInfo,
    val creation: AuditCreationConfig,
    val round: AuditRoundConfig,
    val version: String = "0.8.4",  // how do we get library version ??
) {
    val auditType = electionInfo.auditType
    val isClca = auditType == AuditType.CLCA
    val isOA = auditType == AuditType.ONEAUDIT
    val isPolling = auditType == AuditType.POLLING

    init {
        require(creation.auditType == electionInfo.auditType) {"creation.auditType must equal electionInfo.auditType"}
    }

    val simulation = round!!.simulation
    val sampling = round!!.sampling

    fun toAuditConfig() = AuditConfig(
        auditType = electionInfo.auditType,
        riskLimit = creation!!.riskLimit,
        seed = creation.seed,
        nsimEst = simulation.nsimEst,
        quantile = simulation.estPercentSuccess[1],
        simFuzzPct = simulation.simFuzzPct,
        contestSampleCutoff = sampling.contestSampleCutoff,
        auditSampleCutoff = sampling.auditSampleCutoff,
        removeCutoffContests = sampling.removeCutoffContests,
        maxSamplePct = sampling.maxSamplePct,
        removeMaxContests = sampling.removeMaxContests(),
        minRecountMargin = sampling.minRecountMargin,
        minMargin = sampling.minMargin,
        auditSampleLimit = creation.riskMeasuringSampleLimit,
        pollingConfig = round!!.pollingConfig ?: PollingConfig(),
        clcaConfig = round.clcaConfig ?: ClcaConfig(),
        persistedWorkflowMode = creation.persistedWorkflowMode,
        quantile1 = simulation.estPercentSuccess[0],
    )

    override fun toString() = buildString {
        appendLine("Config(")
        appendLine("  electionInfo=$electionInfo, ")
        appendLine("  creation=$creation, ")
        appendLine("  simulation=$simulation, ")
        appendLine("  sampling=$sampling)")
    }

    companion object {
        fun fromAuditConfig( electionInfo: ElectionInfo, config: AuditConfig): Config {
            val creation = AuditCreationConfig.fromAuditConfig(  config)
            val round = AuditRoundConfig.fromAuditConfig(  config)
            return Config(electionInfo, creation, round)
        }
    }
}

class AuditConfigBuilder(val electionInfo: ElectionInfo,) {
    var roundConfigBuilder = RoundConfigBuilder(this)
    var creationConfig = AuditCreationConfig(electionInfo.auditType)

    fun setCreation(
            riskLimit: Double,
            persistedWorkflowMode: PersistedWorkflowMode,
            seed: Long = secureRandom.nextLong(),
            riskMeasuringSampleLimit: Int? = null
        ):AuditConfigBuilder  {
        creationConfig = AuditCreationConfig(
            auditType=electionInfo.auditType,
            riskLimit=riskLimit,
            seed=seed,
            riskMeasuringSampleLimit=riskMeasuringSampleLimit,
            persistedWorkflowMode=persistedWorkflowMode,
        )
        return this
    }

    fun setRoundConfig(): RoundConfigBuilder  {
        return roundConfigBuilder
    }

    fun build(): Config {
        val auditRoundConfig = roundConfigBuilder.buildit()
        return Config(electionInfo, creationConfig, auditRoundConfig)
    }
}

// seems like you want to take the defaults from the previous Round
class RoundConfigBuilder(val configBuilder: AuditConfigBuilder) {
    var simControl: SimulationControl? = null
    var sampleControl: ContestSampleControl? = null
    var fuzzMvrs: Double? = null

    fun setSimulation(
        nsimEst: Int = 20, // number of simulation estimation trials
        estPercentSuccess: List<Double> = listOf(0.50, 0.80), // use this percentile success for estimated sample size in round idx+1
        simFuzzPct: Double? = null, // for simulating the estimation fuzzing
        mvrFuzz: Double? = null,
    ): RoundConfigBuilder  {

        fuzzMvrs = mvrFuzz
        simControl = SimulationControl(nsimEst, estPercentSuccess, simFuzzPct)
        return this
    }

    fun setSampleControl(
            minRecountMargin: Double = .005,
            minMargin: Double = 0.0,
            maxSamplePct: Double = 0.0,
            contestSampleCutoff: Int?,
            auditSampleCutoff: Int?,
            removeCutoffContests: Boolean? = null,
            removeMaxContests:Int?,
        ): AuditConfigBuilder  {

        val other = mutableMapOf<String, String>()
        if (removeMaxContests != null) other[ContestSampleControl.removeMaxContests] = removeMaxContests.toString()

        sampleControl = ContestSampleControl(
                minRecountMargin=minRecountMargin,
                minMargin=minMargin,
                maxSamplePct=maxSamplePct,
                contestSampleCutoff=contestSampleCutoff,
                removeCutoffContests= removeCutoffContests?: (contestSampleCutoff != null || auditSampleCutoff != null),
                auditSampleCutoff=auditSampleCutoff,
                other=other
        )

        return configBuilder
    }

    fun build(): Config {
        return configBuilder.build()
    }

    fun buildit(): AuditRoundConfig {
        return AuditRoundConfig(simControl!!, sampleControl!!, ClcaConfig(fuzzMvrs=fuzzMvrs), PollingConfig())
    }
}

//////////////////////////////////////////////////////////////////////////////
// experimental

// data class ContestSampleControl(
//    //// checkContestsCorrectlyFormed: preAuditStatus
//    val minRecountMargin: Double = 0.005, // do not audit contests less than this recount margin
//    val minMargin: Double = 0.0, // do not audit contests less than this margin TODO really it should be noerror for clca?
//
//    //// consistentSampling: contestRound.status, depends on having estimation
//    val maxSamplePct: Double = 0.0, // do not audit contests with (estimated nmvrs / contestNc) greater than this // TODO should be Npop
//
//    // conflating maximum in SubsetForEstimation, and maximum sample size per contest and maximum overall sample size
//    val contestSampleCutoff: Int? = 1000, // max number of cvrs for any one contest, set to null to use all
//    val auditSampleCutoff: Int? = 10000, // max number of cvrs in the audit, set to null to use all
//    val removeCutoffContests: Boolean = (contestSampleCutoff != null || auditSampleCutoff != null), // TODO keep this ??
//
//    // soft parameters
//    val other: Map<String, String> = emptyMap(),    // soft parameters to ease migration
//    // val removeMaxContests: Int? = null, // remove top n estimated nmvrs contests, for plotting CaseStudiesRemoveNmax
//)
class ContestSampleControlBuilder(prev: ContestSampleControl?) {
    val def = ContestSampleControl()

    // make everything mutable
    var minRecountMargin = prev?.minRecountMargin
    var minMargin = prev?.minMargin
    var maxSamplePct = prev?.maxSamplePct
    var contestSampleCutoff = prev?.contestSampleCutoff
    var auditSampleCutoff = prev?.auditSampleCutoff
    var removeCutoffContests = prev?.removeCutoffContests
    var other = mutableMapOf<String, String>()

    init {
        if (prev != null) other.putAll(prev.other)
    }

    // build using defaults if not set
    fun build() = ContestSampleControl(
        minRecountMargin = minRecountMargin ?: def.minRecountMargin,
        minMargin = minMargin ?: def.minMargin,
        maxSamplePct = maxSamplePct ?: def.maxSamplePct,
        contestSampleCutoff = contestSampleCutoff ?: def.contestSampleCutoff,
        auditSampleCutoff = auditSampleCutoff ?: def.auditSampleCutoff,
        removeCutoffContests = removeCutoffContests ?: def.removeCutoffContests,
        other = other
    )
}

// data class SimulationControl(
//    val nsimEst: Int = 20, // number of simulation estimation trials
//    val estPercentSuccess: List<Double> = listOf(0.50, 0.80), // use this percentile success for estimated sample size in round idx+1
//    val simFuzzPct: Double? = null, // for estimation fuzzing
//    val simulationStrategy: SimulationStrategy = SimulationStrategy.optimistic
//)

class SimulationControlBuilder(prev: SimulationControl?) {
    val def = SimulationControl()

    // make everything mutable
    var nsimEst = prev?.nsimEst
    var estPercentSuccess = prev?.estPercentSuccess
    var simFuzzPct = prev?.simFuzzPct
    var simulationStrategy = prev?.simulationStrategy

    // build using defaults if not set
    fun build() = SimulationControl(
        nsimEst = nsimEst ?: def.nsimEst,
        estPercentSuccess = estPercentSuccess ?: def.estPercentSuccess,
        simFuzzPct = simFuzzPct ?: def.simFuzzPct,
        simulationStrategy = simulationStrategy ?: def.simulationStrategy,
    )
}
