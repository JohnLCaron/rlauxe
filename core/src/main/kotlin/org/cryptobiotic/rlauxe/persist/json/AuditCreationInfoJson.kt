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
data class AuditCreationConfig(
    val auditType: AuditType, // must agree with ElectionInfo
    val riskLimit: Double = 0.05,
    val seed: Long = secureRandom.nextLong(),
    val riskMeasuringSampleLimit: Int? = null, // the number of samples we are willing to audit; this turns the audit into a "risk measuring" audit

    val persistedWorkflowMode: PersistedWorkflowMode =
        if (auditType.isClca()) PersistedWorkflowMode.testClcaSimulated else PersistedWorkflowMode.testPrivateMvrs,

    val other: Map<String, Any> = emptyMap(),    // soft parameters
)
*/

@Serializable
data class AuditCreationConfigJson(
    val auditType: AuditType,
    val riskLimit: Double,
    val seed: Long,
    val riskMeasuringSampleLimit: Int?,
    val persistedWorkflowMode: PersistedWorkflowMode,
)

fun AuditCreationConfig.publishJson() = AuditCreationConfigJson(
    this.auditType,
    this.riskLimit,
    this.seed,
    this.riskMeasuringSampleLimit,
    this.persistedWorkflowMode,
)

fun AuditCreationConfigJson.import() = AuditCreationConfig(
        this.auditType,
        this.riskLimit,
        this.seed,
        this.riskMeasuringSampleLimit,
        this.persistedWorkflowMode,
    )

/////////////////////////////////////////////////////////////////////////////////

fun writeAuditCreationConfigJsonFile(auditConfig: AuditCreationConfig, filename: String) {
    val json = auditConfig.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(json, out)
        out.close()
    }
}

fun readAuditCreationConfigJsonFile(filename: String): Result<AuditCreationConfig, ErrorMessages> {
    val errs = ErrorMessages("readAuditConfigJsonFile '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<AuditCreationConfigJson>(inp)
            val auditConfig = json.import()
            if (errs.hasErrors()) Err(errs) else Ok(auditConfig)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}

fun readAuditCreationConfigUnwrapped(filename: String): AuditCreationConfig? {
    val result = readAuditCreationConfigJsonFile(filename)
    return if (result.isOk) result.unwrap() else null
}