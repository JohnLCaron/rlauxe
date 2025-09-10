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

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

@Serializable
data class AuditConfigJson(
    val auditType: String,
    val hasStyles: Boolean,
    val riskLimit: Double,
    val seed: Long,
    val nsimEst: Int,
    val quantile: Double,
    val sampleLimit: Int,
    val minRecountMargin: Double, // should be minRecountMargin
    val removeTooManyPhantoms: Boolean,
    val version : Double,
    val pollingConfig: PollingConfigJson? = null,
    val clcaConfig: ClcaConfigJson? = null,
    val oaConfig: OneAuditConfigJson?  = null,
)

fun AuditConfig.publishJson() : AuditConfigJson {
    return when (this.auditType) {
        AuditType.CLCA -> AuditConfigJson(
            this.auditType.name,
            this.hasStyles,
            this.riskLimit,
            this.seed,
            this.nsimEst,
            this.quantile,
            this.sampleLimit,
            this.minRecountMargin,
            this.removeTooManyPhantoms,
            this.version,
            clcaConfig = this.clcaConfig.publishJson(),
        )

        AuditType.POLLING -> AuditConfigJson(
            this.auditType.name,
            this.hasStyles,
            this.riskLimit,
            this.seed,
            this.nsimEst,
            this.quantile,
            this.sampleLimit,
            this.minRecountMargin,
            this.removeTooManyPhantoms,
            this.version,
            pollingConfig = this.pollingConfig.publishJson(),
        )

        AuditType.ONEAUDIT -> AuditConfigJson(
            this.auditType.name,
            this.hasStyles,
            this.riskLimit,
            this.seed,
            this.nsimEst,
            this.quantile,
            this.sampleLimit,
            this.minRecountMargin,
            this.removeTooManyPhantoms,
            this.version,
            oaConfig = this.oaConfig.publishJson(),
        )
    }
}

fun AuditConfigJson.import(): AuditConfig {
    val auditType = enumValueOf(this.auditType, AuditType.entries) ?: AuditType.CLCA
    return when (auditType) {
        AuditType.CLCA -> AuditConfig(
            auditType,
            this.hasStyles,
            this.riskLimit,
            this.seed,
            this.nsimEst,
            this.quantile,
            this.sampleLimit,
            this.minRecountMargin,
            this.removeTooManyPhantoms,
            clcaConfig = this.clcaConfig!!.import(),
            version = this.version,
        )

        AuditType.POLLING -> AuditConfig(
            auditType,
            this.hasStyles,
            this.riskLimit,
            this.seed,
            this.nsimEst,
            this.quantile,
            this.sampleLimit,
            this.minRecountMargin,
            this.removeTooManyPhantoms,
            pollingConfig = this.pollingConfig!!.import(),
            version = this.version,
        )

        AuditType.ONEAUDIT -> AuditConfig(
            auditType,
            this.hasStyles,
            this.riskLimit,
            this.seed,
            this.nsimEst,
            this.quantile,
            this.sampleLimit,
            this.minRecountMargin,
            this.removeTooManyPhantoms,
            oaConfig = this.oaConfig!!.import(),
            version = this.version,
        )
    }
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
    val useFirst: Boolean,
)

fun OneAuditConfig.publishJson() : OneAuditConfigJson {
    return OneAuditConfigJson(
        this.strategy.name,
        this.simFuzzPct,
        this.d,
        this.useFirst
    )
}

fun OneAuditConfigJson.import(): OneAuditConfig {
    val strategy = enumValueOf(this.strategy, OneAuditStrategyType.entries) ?: OneAuditStrategyType.reportedMean
    return OneAuditConfig(
        strategy,
        this.simFuzzPct,
        this.d,
        this.useFirst
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