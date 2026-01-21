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
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
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
    val hasStyle: Boolean, // has Card Style Data (CSD), i.e. we know which contests each card/ballot contains
    val riskLimit: Double = 0.05,
    val seed: Long = secureRandom.nextLong(), // determines sample order. set carefully to ensure truly random.

    // simulation control
    val nsimEst: Int = 100, // number of simulation estimation trials
    val quantile: Double = 0.80, // use this percentile success for estimated sample size
    val contestSampleCutoff: Int? = 30000, // use this number of cvrs in the estimation, set to null to use all
    val simFuzzPct: Double = 0.0, // for simulating the estimation and testMvr fuzzing

    // audit sample size control
    val removeCutoffContests: Boolean = false, // remove contests that need more samples than contestSampleCutoff
    val minRecountMargin: Double = 0.005, // do not audit contests less than this recount margin
    val removeTooManyPhantoms: Boolean = false, // do not audit contests if phantoms > margin
    val auditSampleLimit: Int? = null, // limit audit sample size; audit all samples, ignore risk limit

    // old config, replace by error strategies
    val pollingConfig: PollingConfig = PollingConfig(),
    val clcaConfig: ClcaConfig = ClcaConfig(ClcaStrategyType.previous),
    val oaConfig: OneAuditConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = false),

    // default error strategies
    val pollingErrorStrategy: PollingErrorStrategy = PollingErrorStrategy(),
    val clcaBettingStrategy: ClcaBettingStrategy = ClcaBettingStrategy(),
    val oaBettingStrategy: OneAuditBettingStrategy = OneAuditBettingStrategy(),

    val skipContests: List<Int> = emptyList(),
    val version: Double = 2.0,
)
 */
@Serializable
data class AuditConfigJson(
    val auditType: String,
    val hasStyle: Boolean,
    val riskLimit: Double,
    val seed: Long,

    val nsimEst: Int,
    val quantile: Double,
    val contestSampleCutoff: Int?,
    val removeCutoffContests: Boolean?,
    val simFuzzPct: Double?,

    val minRecountMargin: Double, // should be minRecountMargin
    val removeTooManyPhantoms: Boolean,
    val auditSampleLimit: Int?,

    val pollingConfig: PollingConfigJson? = null,
    val clcaConfig: ClcaConfigJson? = null,
    val oaConfig: OneAuditConfigJson?  = null,

    val persistedWorkflowMode: PersistedWorkflowMode =  PersistedWorkflowMode.testSimulated,
    val version : Double,
    val skipContests: List<Int>?  = null,
)

fun AuditConfig.publishJson() : AuditConfigJson {
    return when (this.auditType) {
        AuditType.CLCA -> AuditConfigJson(
            this.auditType.name,
            this.hasStyle,
            this.riskLimit,
            this.seed,
            this.nsimEst,
            this.quantile,
            this.contestSampleCutoff,
            this.removeCutoffContests,
            this.simFuzzPct,

            this.minRecountMargin,
            this.removeTooManyPhantoms,
            this.auditSampleLimit,
            clcaConfig = this.clcaConfig.publishJson(),

            persistedWorkflowMode = this.persistedWorkflowMode,
            skipContests = skipContests,
            version = this.version,
        )

        AuditType.POLLING -> AuditConfigJson(
            this.auditType.name,
            this.hasStyle,
            this.riskLimit,
            this.seed,
            this.nsimEst,
            this.quantile,
            this.contestSampleCutoff,
            this.removeCutoffContests,
            this.simFuzzPct,

            this.minRecountMargin,
            this.removeTooManyPhantoms,
            this.auditSampleLimit,
            pollingConfig = this.pollingConfig.publishJson(),

            persistedWorkflowMode = this.persistedWorkflowMode,
            skipContests = skipContests,
            version = this.version,
        )

        AuditType.ONEAUDIT -> AuditConfigJson(
            this.auditType.name,
            this.hasStyle,
            this.riskLimit,
            this.seed,
            this.nsimEst,
            this.quantile,
            this.contestSampleCutoff,
            this.removeCutoffContests,
            this.simFuzzPct,

            this.minRecountMargin,
            this.removeTooManyPhantoms,
            this.auditSampleLimit,
            clcaConfig = this.clcaConfig.publishJson(),
            oaConfig = this.oaConfig.publishJson(),

            persistedWorkflowMode = this.persistedWorkflowMode,
            skipContests = skipContests,
            version = this.version,
        )
    }
}

fun AuditConfigJson.import(): AuditConfig {
    val auditType = enumValueOf(this.auditType, AuditType.entries) ?: AuditType.CLCA
    return when (auditType) {
        AuditType.CLCA -> AuditConfig(
            auditType,
            this.hasStyle,
            this.riskLimit,
            this.seed,
            this.nsimEst,
            this.quantile,
            this.contestSampleCutoff,
            this.simFuzzPct,

            this.removeCutoffContests ?: (this.contestSampleCutoff != null),
            this.minRecountMargin,
            this.removeTooManyPhantoms,
            auditSampleLimit = this.auditSampleLimit,
            clcaConfig = this.clcaConfig!!.import(),

            persistedWorkflowMode = this.persistedWorkflowMode,
            skipContests = skipContests?: emptyList(),
            version = this.version,
        )

        AuditType.POLLING -> AuditConfig(
            auditType,
            this.hasStyle,
            this.riskLimit,
            this.seed,
            this.nsimEst,
            this.quantile,
            this.contestSampleCutoff,
            this.simFuzzPct,

            this.removeCutoffContests ?: (this.contestSampleCutoff != null),
            this.minRecountMargin,
            this.removeTooManyPhantoms,
            auditSampleLimit = this.auditSampleLimit,
            pollingConfig = this.pollingConfig!!.import(),

            persistedWorkflowMode = this.persistedWorkflowMode,
            skipContests = skipContests?: emptyList(),
            version = this.version,
        )

        AuditType.ONEAUDIT -> AuditConfig(
            auditType,
            this.hasStyle,
            this.riskLimit,
            this.seed,
            this.nsimEst,
            this.quantile,
            this.contestSampleCutoff,
            this.simFuzzPct,

            this.removeCutoffContests ?: (this.contestSampleCutoff != null),
            this.minRecountMargin,
            this.removeTooManyPhantoms,
            auditSampleLimit = this.auditSampleLimit,
            clcaConfig = this.clcaConfig?.import() ?: ClcaConfig(),
            oaConfig = this.oaConfig!!.import(),

            persistedWorkflowMode = this.persistedWorkflowMode,
            skipContests = skipContests?: emptyList(),
            version = this.version,
        )
    }
}

@Serializable
data class PollingConfigJson(
    val d: Int,
)

fun PollingConfig.publishJson() = PollingConfigJson(this.d)
fun PollingConfigJson.import() = PollingConfig(this.d)

// enum class ClcaStrategyType { generalAdaptive, apriori, fuzzPct, oracle  }
//data class ClcaConfig(
//    val cvrsContainUndervotes: Boolean = true,
//    val strategy: ClcaStrategyType = ClcaStrategyType.generalAdaptive,
//    val fuzzPct: Double? = null, // use to generate apriori errorRates for simulation, only used when ClcaStrategyType = fuzzPct
//    val pluralityErrorRates: PluralityErrorRates? = null, // use as apriori errorRates for simulation and audit.
//    val d: Int = 100,  // shrinkTrunc weight for error rates
//    val maxRisk: Double = 0.90,  // max risk on any one bet
//)
@Serializable
data class ClcaConfigJson(
    val strategy: String,
    val fuzzPct: Double?,
    // val errorRates: List<Double>?,
    val d: Int,
    val maxRisk: Double?,
    val cvrsContainUndervotes: Boolean=true,
)

fun ClcaConfig.publishJson() = ClcaConfigJson(
    this.strategy.name,
    this.fuzzPct,
    // this.pluralityErrorRates?.toList(),
    this.d,
    this.maxRisk,
    this.cvrsContainUndervotes,
)

fun ClcaConfigJson.import() = ClcaConfig(
        enumValueOf(this.strategy, ClcaStrategyType.entries) ?: ClcaStrategyType.generalAdaptive,
        this.fuzzPct,
        // if (this.errorRates != null) PluralityErrorRates.fromList(this.errorRates) else null,
        this.d,
        this.maxRisk ?: 0.90, // TODO what should this be?
        this.cvrsContainUndervotes,
    )

@Serializable
data class OneAuditConfigJson(
    val strategy: String,
    val d: Int,
    val useFirst: Boolean,
)

fun OneAuditConfig.publishJson() = OneAuditConfigJson(this.strategy.name, this.d, this.useFirst)
fun OneAuditConfigJson.import() = OneAuditConfig(
        enumValueOf(this.strategy, OneAuditStrategyType.entries) ?: OneAuditStrategyType.generalAdaptive,
        this.d,
        this.useFirst
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
    return readAuditConfigJsonFile(filename).unwrap()
}