package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.betting.TausRates
import org.cryptobiotic.rlauxe.util.secureRandom
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode

private val logger = KotlinLogging.logger("Config")

data class Config(
    val election: ElectionInfo,
    val creation: AuditCreationConfig,
    val round: AuditRoundConfig,
    val version: String = "0.8.4",  // how do we get library version ??
) {
    val auditType = election.auditType
    val isClca = auditType == AuditType.CLCA
    val isOA = auditType == AuditType.ONEAUDIT
    val isPolling = auditType == AuditType.POLLING

    init {
        require(creation.auditType == election.auditType) {"creation.auditType must equal electionInfo.auditType"}
    }

    val simulation = round.simulation
    val sampling = round.sampling
    val riskLimit = creation.riskLimit
    val seed = creation.seed
    val persistedWorkflowMode = creation.persistedWorkflowMode

    // only used in PersistedMvrManagerTest
    fun mvrFuzzPct(): Double {
        return when (auditType) {
            AuditType.POLLING -> 0.0
            AuditType.CLCA, AuditType.ONEAUDIT -> round.clcaConfig!!.fuzzMvrs  ?: 0.0
        }
    }

    fun toAuditConfig() = AuditConfig(
        auditType = election.auditType,
        riskLimit = creation.riskLimit,
        seed = creation.seed,
        nsimEst = simulation.nsimEst,
        quantile = if (simulation.estPercentile.size > 1) .01 * simulation.estPercentile[1] else .50,
        simFuzzPct = simulation.simFuzzPct,
        contestSampleCutoff = sampling.contestSampleCutoff,
        auditSampleCutoff = sampling.auditSampleCutoff,
        removeCutoffContests = sampling.removeCutoffContests,
        maxSamplePct = sampling.maxSamplePct,
        removeMaxContests = sampling.removeMaxContests(),
        minRecountMargin = sampling.minRecountMargin,
        minMargin = sampling.minMargin,
        auditSampleLimit = creation.riskMeasuringSampleLimit,
        pollingConfig = round.pollingConfig ?: PollingConfig(),
        clcaConfig = round.clcaConfig ?: ClcaConfig(),
        persistedWorkflowMode = creation.persistedWorkflowMode,
        quantile1 = if (simulation.estPercentile.size > 0) .01 * simulation.estPercentile[0] else .50,
    )

    fun replace(clcaConfig: ClcaConfig): Config {
        return Config(this.election, this.creation, this.round.copy(clcaConfig = clcaConfig), this.version)
    }

    override fun toString() = buildString {
        appendLine("Config(")
        appendLine("  electionInfo=$election, ")
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

        fun from( auditType: AuditType,
                  riskLimit:Double= .05,
                  nsimEst:Int=10,
                  simFuzzPct: Double? = null,
                  fuzzMvrs:Double? = null,
                  contestSampleCutoff:Int? = if (auditType.isPolling()) 10000 else 2000,
                  apriori: TausRates = TausRates(emptyMap()),
                  persistedWorkflowMode: PersistedWorkflowMode = if (auditType.isClca())
                      PersistedWorkflowMode.testClcaSimulated else PersistedWorkflowMode.testPrivateMvrs,): Config {

            return from(ElectionInfo.forTest(auditType),
                riskLimit, nsimEst, simFuzzPct, fuzzMvrs, contestSampleCutoff, apriori, persistedWorkflowMode)
        }

        fun from( electionInfo: ElectionInfo,
                  riskLimit:Double= .05,
                  nsimEst:Int=10,
                  simFuzzPct: Double? = null,
                  fuzzMvrs:Double? = null,
                  contestSampleCutoff:Int? = if (electionInfo.auditType.isPolling()) 10000 else 2000,
                  apriori: TausRates = TausRates(emptyMap()),
                  persistedWorkflowMode: PersistedWorkflowMode = if (electionInfo.auditType.isClca())
                      PersistedWorkflowMode.testClcaSimulated else PersistedWorkflowMode.testPrivateMvrs,): Config {

            return if (electionInfo.auditType.isPolling()) forPolling(electionInfo,
                riskLimit, nsimEst, simFuzzPct, contestSampleCutoff, persistedWorkflowMode)
            else forClca(electionInfo,
                riskLimit, nsimEst, simFuzzPct, fuzzMvrs, contestSampleCutoff, apriori, persistedWorkflowMode)
        }

        // AuditConfig(AuditType.CLCA, seed = 12356667890L, nsimEst=10, contestSampleCutoff = 1000, simFuzzPct = .01,
        //            persistedWorkflowMode=PersistedWorkflowMode.testPrivateMvrs
        //        )
        // AuditConfig(AuditType.CLCA, seed = 12356667890L, nsimEst=10, contestSampleCutoff = 1000, simFuzzPct = .01,
        //            persistedWorkflowMode=PersistedWorkflowMode.testPrivateMvrs
        //        )
        //             AuditConfig(AuditType.CLCA, seed = 12356667890L, nsimEst = 100,
        //                clcaConfig = ClcaConfig(apriori = TausRates(mapOf("win-oth" to .001))),
        //            )
        fun forClca( electionInfo: ElectionInfo,
                     riskLimit:Double= .05,
                     nsimEst:Int= 10,
                     simFuzzPct: Double? = null,
                     fuzzMvrs:Double? = null,
                     contestSampleCutoff:Int?= 2000,
                     apriori: TausRates = TausRates(emptyMap()),
                     persistedWorkflowMode: PersistedWorkflowMode = PersistedWorkflowMode.testClcaSimulated): Config {
            val creation = AuditCreationConfig(electionInfo.auditType, riskLimit=riskLimit, persistedWorkflowMode=persistedWorkflowMode)
            val round = AuditRoundConfig(
                SimulationControl(nsimEst=nsimEst, simFuzzPct=simFuzzPct),
                ContestSampleControl(contestSampleCutoff=contestSampleCutoff),
                ClcaConfig(fuzzMvrs=fuzzMvrs, apriori=apriori),
                null
            )
            return Config(electionInfo, creation, round)
        }
        //             AuditConfig(AuditType.POLLING, seed = 12356667890L, nsimEst = 100, // skipContests=skipContests,
        //                pollingConfig = PollingConfig())
        fun forPolling( electionInfo: ElectionInfo,
                        riskLimit:Double= .05,
                        nsimEst:Int=10,
                        simFuzzPct: Double? = null,
                        contestSampleCutoff:Int? = 10000,
                        persistedWorkflowMode:PersistedWorkflowMode = PersistedWorkflowMode.testPrivateMvrs): Config {
            val creation = AuditCreationConfig(electionInfo.auditType, riskLimit=riskLimit, persistedWorkflowMode=persistedWorkflowMode)
            val round = AuditRoundConfig(
                SimulationControl(nsimEst=nsimEst, simFuzzPct=simFuzzPct),
                ContestSampleControl(contestSampleCutoff=contestSampleCutoff),
                null,
                PollingConfig()
            )
            return Config(electionInfo, creation, round)
        }
    }
}


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
) {
    companion object {
        fun forTest(auditType: AuditType) = ElectionInfo("testing", auditType, 42, 1)
    }
}

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
                SimulationControl(config.nsimEst, listOf((100 * config.quantile1).toInt(), (100*config.quantile).toInt()), config.simFuzzPct)

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
    val estPercentile: List<Int> = listOf(50, 80), // use this percentile of the distribution of estimated sample sizes
    val simFuzzPct: Double? = null, // for estimation fuzzing
    val simulationStrategy: SimulationStrategy = SimulationStrategy.optimistic
) {
    fun percentile(roundIdx: Int): Int {
        return when {
            estPercentile.isEmpty() -> 50   // optimistic I guess
            estPercentile.size >= roundIdx -> estPercentile[roundIdx-1]  // roundIdx is 1 based
            estPercentile.size < roundIdx -> estPercentile.last()
            else -> throw RuntimeException("cant happen")
        }
    }
}

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



