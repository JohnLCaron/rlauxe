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
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.safeEnumValueOf
import org.cryptobiotic.rlauxe.workflow.AuditConfig
import org.cryptobiotic.rlauxe.workflow.AuditType

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

@Serializable
data class AuditConfigJson(
    val auditType: String,
    val hasStyles: Boolean,
    val seed: Long,
    val riskLimit: Double,
)

fun AuditConfig.publishJson() : AuditConfigJson {
    return AuditConfigJson(
        this.auditType.name,
        this.hasStyles,
        this.seed,
        this.riskLimit,
    )
}

fun AuditConfigJson.import(): AuditConfig {
    val auditType = safeEnumValueOf(this.auditType) ?: AuditType.CARD_COMPARISON
    return AuditConfig(
        auditType,
        this.hasStyles,
        this.seed,
        fuzzPct=null,
        riskLimit=this.riskLimit,
    )
}

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