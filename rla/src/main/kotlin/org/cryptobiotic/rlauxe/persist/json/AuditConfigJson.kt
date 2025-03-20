@file:OptIn(ExperimentalSerializationApi::class)
package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ClcaErrorRates
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.enumValueOf
import org.cryptobiotic.rlauxe.workflow.*

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

// enum class AuditType { POLLING, CARD_COMPARISON, ONEAUDIT }
//data class AuditConfig(
//    val auditType: AuditType,
//    val hasStyles: Boolean,
//    val riskLimit: Double = 0.05,
//    val seed: Long = secureRandom.nextLong(), // determines smaple order. set carefully to ensure truly random.
//
//    // simulation control
//    val nsimEst: Int = 100, // number of simulation estimations
//    val quantile: Double = 0.80, // use this percentile success for estimated sample size
//    val samplePctCutoff: Double = .42, // dont sample more than this pct of N
//    val minMargin: Double = 0.005,
//
//    val pollingConfig: PollingConfig = PollingConfig(),
//    val clcaConfig: ClcaConfig = ClcaConfig(ClcaStrategyType.noerror),
//    val oaConfig: OneAuditConfig = OneAuditConfig(OneAuditStrategyType.default),
//    val version: Double = 1.0,
//)

// data class AuditConfig(
//    val auditType: AuditType,
//    val hasStyles: Boolean,
//    val riskLimit: Double = 0.05,
//    val seed: Long = secureRandom.nextLong(), // determines smaple order. set carefully to ensure truly random.
//
//    // simulation control
//    val nsimEst: Int = 100, // number of simulation estimations
//    val quantile: Double = 0.80, // use this percentile success for estimated sample size
//    val samplePctCutoff: Double = 1.0, // dont sample more than this pct of N
//    val minMargin: Double = 0.0, // do not audit contests less than this reported margin
//    val removeTooManyPhantoms: Boolean = false, // do not audit contests if phantoms > margin
//
//    val pollingConfig: PollingConfig = PollingConfig(),
//    val clcaConfig: ClcaConfig = ClcaConfig(ClcaStrategyType.noerror),
//    val oaConfig: OneAuditConfig = OneAuditConfig(OneAuditStrategyType.default),
//    val version: Double = 1.0,
//)

@Serializable
data class AuditConfigJson(
    val auditType: String,
    val hasStyles: Boolean,
    val riskLimit: Double,
    val seed: Long,
    val nsimEst: Int,
    val quantile: Double,
    val samplePctCutoff: Double,
    val minMargin: Double,
    val removeTooManyPhantoms: Boolean,
    val pollingConfig: PollingConfigJson,
    val clcaConfig: ClcaConfigJson,
    val oaConfig: OneAuditConfigJson,
    val version: Double,
)

fun AuditConfig.publishJson() : AuditConfigJson {
    return AuditConfigJson(
        this.auditType.name,
        this.hasStyles,
        this.riskLimit,
        this.seed,
        this.nsimEst,
        this.quantile,
        this.samplePctCutoff,
        this.minMargin,
        this.removeTooManyPhantoms,
        this.pollingConfig.publishJson(),
        this.clcaConfig.publishJson(),
        this.oaConfig.publishJson(),
        this.version
    )
}

fun AuditConfigJson.import(): AuditConfig {
    val auditType = enumValueOf(this.auditType, AuditType.entries) ?: AuditType.CLCA
    return AuditConfig(
        auditType,
        this.hasStyles,
        this.riskLimit,
        this.seed,
        this.nsimEst,
        this.quantile,
        this.samplePctCutoff,
        this.minMargin,
        this.removeTooManyPhantoms,
        this.pollingConfig.import(),
        this.clcaConfig.import(),
        this.oaConfig.import(),
        this.version,
    )
}

// data class PollingConfig(
//    val fuzzPct: Double? = null,
//    val d: Int = 100,
//)
@Serializable
data class PollingConfigJson(
    val simFuzzPct: Double?,
    val d: Int,
)

fun PollingConfig.publishJson() : PollingConfigJson {
    return PollingConfigJson(
        this.simFuzzPct,
        this.d,
    )
}

fun PollingConfigJson.import(): PollingConfig {
    return PollingConfig(
        this.simFuzzPct,
        this.d,
    )
}

// enum class ClcaStrategyType { oracle, noerror, fuzzPct, apriori }
//data class ClcaConfig(
//    val strategy: ClcaStrategyType,
//    val simFuzzPct: Double? = null, // use to generate apriori errorRates for simulation
//    val errorRates: ErrorRates? = null, // use as apriori
//    val d: Int = 100,  // shrinkTrunc weight for error rates
//)
@Serializable
data class ClcaConfigJson(
    val strategy: String,
    val simFuzzPct: Double?,
    val errorRates: List<Double>?,
    val d: Int,
)

fun ClcaConfig.publishJson() : ClcaConfigJson {
    return ClcaConfigJson(
        this.strategy.name,
        this.simFuzzPct,
        this.errorRates?.toList(),
        this.d,
    )
}

fun ClcaConfigJson.import(): ClcaConfig {
    val strategy = enumValueOf(this.strategy, ClcaStrategyType.entries) ?: ClcaStrategyType.noerror
    return ClcaConfig(
        strategy,
        this.simFuzzPct,
        if (this.errorRates != null) ClcaErrorRates.fromList(this.errorRates) else null,
        this.d,
    )
}

// enum class OneAuditStrategyType { standard, max99 }
//data class OneAuditConfig(
//    val strategy: OneAuditStrategyType,
//    val fuzzPct: Double? = null, // for the estimation
//    val d: Int = 100,  // shrinkTrunc weight
//)
@Serializable
data class OneAuditConfigJson(
    val strategy: String,
    val simFuzzPct: Double?,
    val d: Int,
)

fun OneAuditConfig.publishJson() : OneAuditConfigJson {
    return OneAuditConfigJson(
        this.strategy.name,
        this.simFuzzPct,
        this.d,
    )
}

fun OneAuditConfigJson.import(): OneAuditConfig {
    val strategy = enumValueOf(this.strategy, OneAuditStrategyType.entries) ?: OneAuditStrategyType.default
    return OneAuditConfig(
        strategy,
        this.simFuzzPct,
        this.d,
    )
}

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