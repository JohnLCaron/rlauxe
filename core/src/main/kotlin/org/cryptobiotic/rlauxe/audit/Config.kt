package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.betting.TausRates
import org.cryptobiotic.rlauxe.util.secureRandom

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
    val mvrSource = election.mvrSource

    // only used in PersistedMvrManagerTest
    fun mvrFuzzPct(): Double {
        return when (auditType) {
            AuditType.POLLING -> 0.0
            AuditType.CLCA, AuditType.ONEAUDIT -> round.clcaConfig!!.fuzzMvrs  ?: 0.0
        }
    }

    fun replaceClcaConfig(clcaConfig: ClcaConfig): Config {
        return Config(this.election, this.creation, this.round.copy(clcaConfig = clcaConfig), this.version)
    }

    fun replaceSeed(seed: Long): Config {
        return Config(this.election, this.creation.copy(seed = seed), this.round, this.version)
    }


    override fun toString() = buildString {
        appendLine("Config(")
        appendLine("  electionInfo=$election, ")
        appendLine("  creation=$creation, ")
        appendLine("  simulation=$simulation, ")
        appendLine("  sampling=$sampling)")
        if (round.clcaConfig != null) appendLine("  clcaConfig=${round.clcaConfig} )")
        if (round.pollingConfig != null) appendLine("  pollingConfig=${round.pollingConfig} )")
    }

    companion object {

        fun from(auditType: AuditType,
                 riskLimit:Double= .05,
                 nsimTrials:Int=10,
                 simFuzzPct: Double? = null,
                 fuzzMvrs:Double? = null,
                 contestSampleCutoff:Int? = if (auditType.isPolling()) 10000 else 2000,
                 apriori: TausRates = TausRates(emptyMap()),
                 mvrSource: MvrSource = if (auditType.isClca())
                      MvrSource.testClcaSimulated else MvrSource.testPrivateMvrs,): Config {

            return from(
                ElectionInfo.forTest(auditType, mvrSource),
                riskLimit, nsimTrials, simFuzzPct, fuzzMvrs, contestSampleCutoff, apriori)
        }

        fun from(electionInfo: ElectionInfo,
                 riskLimit:Double= .05,
                 nsimTrials:Int=10,
                 simFuzzPct: Double? = null,
                 fuzzMvrs:Double? = null,
                 contestSampleCutoff:Int? = if (electionInfo.auditType.isPolling()) 10000 else 2000,
                 apriori: TausRates = TausRates(emptyMap()),
           ): Config {

            return if (electionInfo.auditType.isPolling()) forPolling(electionInfo,
                riskLimit, nsimTrials, simFuzzPct, contestSampleCutoff)
            else forClca(electionInfo,
                riskLimit, nsimTrials, simFuzzPct, fuzzMvrs, contestSampleCutoff, apriori)
        }

        fun forClca(electionInfo: ElectionInfo,
                    riskLimit:Double= .05,
                    nsimTrials:Int= 10,
                    simFuzzPct: Double? = null,
                    fuzzMvrs:Double? = null,
                    contestSampleCutoff:Int?= 2000,
                    apriori: TausRates = TausRates(emptyMap()),
            ): Config {
            val creation = AuditCreationConfig(electionInfo.auditType, riskLimit=riskLimit)
            val round = AuditRoundConfig(
                SimulationControl(nsimTrials=nsimTrials, simFuzzPct=simFuzzPct),
                ContestSampleControl(contestSampleCutoff=contestSampleCutoff),
                ClcaConfig(fuzzMvrs=fuzzMvrs, apriori=apriori),
                null
            )
            return Config(electionInfo, creation, round)
        }

        fun forPolling(electionInfo: ElectionInfo,
                       riskLimit:Double= .05,
                       nsimTrials:Int=10,
                       simFuzzPct: Double? = null,
                       contestSampleCutoff:Int? = 10000,
        ): Config {
            val creation = AuditCreationConfig(electionInfo.auditType, riskLimit=riskLimit)
            val round = AuditRoundConfig(
                SimulationControl(nsimTrials=nsimTrials, simFuzzPct=simFuzzPct),
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

enum class MvrSource {
    real,               // sampleMvrs$round.csv must be written from external program.
    testPrivateMvrs,    // sampleMvrs$round.csv are taken from private/sortedMvrs.csv
    testClcaSimulated,  // use PersistedMvrManagerTest to fuzz the mvrs on the fly (clca only)
}

// what information cannot be changed by the auditors?
data class ElectionInfo(
    val electionName: String,
    val auditType: AuditType,
    val totalCardCount: Int,    // total cards in the election
    val contestCount: Int,

    val cvrsContainUndervotes: Boolean = true, // TODO implement cvrsContainUndervotes = false
    // val poolsHaveOneCardStyle: Boolean? = null, // TODO dont seem to be using this; instead its set indididually on pool/batch
    val pollingMode: PollingMode? = null, // TODO also needed for cvrsContainUndervotes = false ? Maybe "BatchesMode ??

    val mvrSource: MvrSource =
        if (auditType.isClca()) MvrSource.testClcaSimulated else MvrSource.testPrivateMvrs,

    val other: Map<String, Any> = emptyMap(),    // soft parameters to ease migration
) {
    init {
        if (mvrSource == MvrSource.testClcaSimulated && auditType.isPolling()) {
            throw RuntimeException("MvrSource must be CLCA or OneAudit")
        }
        if (pollingMode == null && auditType.isPolling()) {
            throw RuntimeException("Polling Audits must set pollingMode")
        }
    }
    companion object {
        fun forTest(auditType: AuditType, mvrSource: MvrSource) =
            ElectionInfo("testing", auditType, 42, 1, mvrSource=mvrSource,
                pollingMode = if (auditType.isPolling()) PollingMode.withBatches else null)
    }
}

//// commit at createAuditRecord(), after seed has been chosen

data class AuditCreationConfig(
    val auditType: AuditType, // must agree with ElectionInfo
    val riskLimit: Double = 0.05,

    val seed: Long = secureRandom.nextLong(),
    val riskMeasuringSampleLimit: Int? = null, // the number of samples we are willing to audit; this turns the audit into a "risk measuring" audit
    val other: Map<String, Any> = emptyMap(),    // soft parameters
) {
    fun isRiskMeasuringAudit() = riskMeasuringSampleLimit != null
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
    }
}

// optimistic: round 1 assume no errors, subsequent rounds use measured error rates
// reglar" old estimateSampleSizes(), deprecated
enum class SimulationStrategy { regular, optimistic  }

data class SimulationControl(
    val nsimTrials: Int = 20, // number of simulation estimation trials
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
    val maxSamplePct: Double = 0.0, // do not audit contests with (estimated nmvrs / Npop) greater than this
    val contestSampleCutoff: Int? = 1000, // max number of cvrs for any one contest, set to null to use all
    val auditSampleCutoff: Int? = 10000, // max number of cvrs in the audit, set to null to use all

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
        val NONE = ContestSampleControl(0.0, 0.0, 0.0, null, null, emptyMap())
        private val logger = KotlinLogging.logger("ContestSampleControl")
    }
}

data class PollingConfig(
    val d: Int = 100,  // shrinkTrunc weight
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



