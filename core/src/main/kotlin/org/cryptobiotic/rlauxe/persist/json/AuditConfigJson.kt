@file:OptIn(ExperimentalSerializationApi::class)
package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TausRates
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.enumValueOf
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/*
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
)
 */
@Serializable
data class AuditConfigJson(
    val auditType: String,
    val riskLimit: Double,
    val seed: Long,   // convenient for testing

    val nsimEst: Int,
    val quantile: Double,
    val simFuzzPct: Double?,

    val contestSampleCutoff: Int?,
    val removeCutoffContests: Boolean,
    val minRecountMargin: Double,
    val minMargin: Double,
    val maxSamplePct: Double,
    val removeMaxContests: Int? = null, // remove top n min-margin contests
    val removeTooManyPhantoms: Boolean,

    val auditSampleLimit: Int?,

    val pollingConfig: PollingConfigJson? = null,
    val clcaConfig: ClcaConfigJson? = null,

    val persistedWorkflowMode: PersistedWorkflowMode =  PersistedWorkflowMode.testClcaSimulated,
    val simulationStrategy: SimulationStrategy =  SimulationStrategy.regular,
    val version : Double,
)

fun AuditConfig.publishJson() = AuditConfigJson(
    this.auditType.name,
    this.riskLimit,
    this.seed,

    this.nsimEst,
    this.quantile,
    this.simFuzzPct,

    this.contestSampleCutoff,
    this.removeCutoffContests,
    this.minRecountMargin,
    this.minMargin,
    this.maxSamplePct,
    this.removeMaxContests,
    this.removeTooManyPhantoms,

    this.auditSampleLimit,

    clcaConfig = if (!this.auditType.isPolling()) this.clcaConfig.publishJson() else null,
    pollingConfig = if (this.auditType.isPolling()) this.pollingConfig.publishJson() else null,

    persistedWorkflowMode = this.persistedWorkflowMode,
    simulationStrategy = this.simulationStrategy,
    // skipContests = skipContests,
    version = this.version,
)

fun AuditConfigJson.import(): AuditConfig {
    val auditType = enumValueOf(this.auditType, AuditType.entries) ?: AuditType.CLCA
    return AuditConfig(
        auditType,
        this.riskLimit,
        this.seed,

        this.nsimEst,
        this.quantile,
        this.simFuzzPct,

        contestSampleCutoff = this.contestSampleCutoff,
        removeCutoffContests = this.removeCutoffContests,
        minRecountMargin = this.minRecountMargin,
        minMargin = this.minMargin,
        maxSamplePct = this.maxSamplePct,
        removeMaxContests = this.removeMaxContests,
        removeTooManyPhantoms = this.removeTooManyPhantoms,

        auditSampleLimit = this.auditSampleLimit,

        clcaConfig = if (this.clcaConfig != null) this.clcaConfig.import() else ClcaConfig(),
        pollingConfig = if (this.pollingConfig != null) this.pollingConfig.import() else PollingConfig(),

        persistedWorkflowMode = this.persistedWorkflowMode,
        simulationStrategy = this.simulationStrategy,
        // skipContests = skipContests?: emptyList(),
        version = this.version,
    )
}

@Serializable
data class PollingConfigJson(
    val d: Int,
)

fun PollingConfig.publishJson() = PollingConfigJson(this.d)
fun PollingConfigJson.import() = PollingConfig(this.d)

// enum class ClcaStrategyType { generalAdaptive, generalAdaptive2}
//data class ClcaConfig(
//    val strategy: ClcaStrategyType = ClcaStrategyType.generalAdaptive2,
//    val fuzzMvrs: Double? = null, // used by PersistedMvrManagerTest to fuzz mvrs when persistedWorkflowMode=testSimulate
//    val d: Int = 100,  // shrinkTrunc weight for error rates
//    val maxLoss: Double = 0.90,  // max loss on any one bet, 0 < maxLoss < 1
//    val cvrsContainUndervotes: Boolean = true,
//    val apriori: TausRates = TausRates(emptyMap()),
//)
@Serializable
data class ClcaConfigJson(
    val strategy: String,
    val fuzzPct: Double?,
    val d: Int,
    val maxLoss: Double?,
    val apriori:  Map<String, Double>?,
)

fun ClcaConfig.publishJson() = ClcaConfigJson(
    this.strategy.name,
    this.fuzzMvrs,
    this.d,
    this.maxLoss,
    this.apriori.rates,
)

fun ClcaConfigJson.import() = ClcaConfig(
        enumValueOf(this.strategy, ClcaStrategyType.entries) ?: ClcaStrategyType.generalAdaptive,
        this.fuzzPct,
        this.d,
        this.maxLoss ?: 0.90,
        apriori=TausRates(this.apriori ?: emptyMap()),
    )

/////////////////////////////////////////////////////////////////////////////////

fun writeAuditConfigJsonFile(auditConfig: AuditConfig, filename: String) {
    val json = auditConfig.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(json, out)
        out.close()
    }
}

fun readAuditConfigJsonFile(filename: String): Result<AuditConfig, ErrorMessages> {
    val errs = ErrorMessages("readAuditConfigJsonFile '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<AuditConfigJson>(inp)
            val auditConfig = json.import()
            if (errs.hasErrors()) Err(errs) else Ok(auditConfig)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}

fun readAuditConfigUnwrapped(filename: String): AuditConfig? {
    val result = readAuditConfigJsonFile(filename)
    return if (result.isOk) result.unwrap() else null
}