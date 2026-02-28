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
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.enumValueOf
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/*
data class ElectionInfo2(
    val auditType: AuditType,
    val ncards: Int,
    val ncontests: Int,
    val persistedWorkflowMode: PersistedWorkflowMode =  PersistedWorkflowMode.testSimulated,
    val poolsHaveOneCardStyle: Boolean,
) */
@Serializable
data class ElectionInfo2Json(
    val auditType: String,
    val ncards: Int,
    val ncontests: Int,
    val cvrsContainUndervotes: Boolean,
    val poolsHaveOneCardStyle: Boolean?,
)

fun ElectionInfo2.publishJson() = ElectionInfo2Json(
    this.auditType.name,
    ncards = this.ncards,
    ncontests = this.ncontests,
    cvrsContainUndervotes = this.cvrsContainUndervotes,
    poolsHaveOneCardStyle = this.poolsHaveOneCardStyle,
)

fun ElectionInfo2Json.import() = ElectionInfo2(
    enumValueOf(this.auditType, AuditType.entries) ?: throw RuntimeException("unknown AuditType ${this.auditType}"),
    ncards = this.ncards,
    ncontests = this.ncontests,
    cvrsContainUndervotes = this.cvrsContainUndervotes,
    poolsHaveOneCardStyle = this.poolsHaveOneCardStyle,
)

/////////////////////////////////////////////////////////////////////////////////

fun writeElectionInfo2JsonFile(electionInfo: ElectionInfo2, filename: String) {
    val json = electionInfo.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(json, out)
        out.close()
    }
}

fun readElectionInfo2JsonFile(filename: String): Result<ElectionInfo2, ErrorMessages> {
    val errs = ErrorMessages("readElectionInfo2JsonFile '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<ElectionInfo2Json>(inp)
            val auditConfig = json.import()
            if (errs.hasErrors()) Err(errs) else Ok(auditConfig)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}

fun readElection2InfoUnwrapped(filename: String): ElectionInfo2? {
    val result = readElectionInfo2JsonFile(filename)
    return if (result.isOk) result.unwrap() else null
}