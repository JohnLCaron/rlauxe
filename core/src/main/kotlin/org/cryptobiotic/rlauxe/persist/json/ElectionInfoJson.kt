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

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/*
data class ElectionInfo(
    val electionName: String,
    val auditType: AuditType,
    val totalCardCount: Int,    // total cards in the election
    val contestCount: Int,

    val cvrsContainUndervotes: Boolean = true,
    val poolsHaveOneCardStyle: Boolean? = null,
    val pollingMode: PollingMode? = null,

    val mvrSource: MvrSource =
        if (auditType.isClca()) MvrSource.testClcaSimulated else MvrSource.testPrivateMvrs,

    val other: Map<String, Any> = emptyMap(),    // soft parameters to ease migration
) */
@Serializable
data class ElectionInfoJson(
    val electionName: String?,
    val auditType: AuditType,
    val ncards: Int,
    val ncontests: Int,
    val cvrsContainUndervotes: Boolean,
    val poolsHaveOneCardStyle: Boolean? = null,
    val pollingMode: PollingMode? = null,
    val mvrSource: MvrSource? = null,
    val other: Map<String, String>?
)

fun ElectionInfo.publishJson() = ElectionInfoJson(
    this.electionName,
    this.auditType,
    ncards = this.totalCardCount,
    ncontests = this.contestCount,
    cvrsContainUndervotes = this.cvrsContainUndervotes,
    // poolsHaveOneCardStyle = this.poolsHaveOneCardStyle,
    pollingMode = this.pollingMode,
    mvrSource = this.mvrSource,
    other = if (this.other.isEmpty()) null else this.other.publishJson(),
)

fun ElectionInfoJson.import() = ElectionInfo(
    this.electionName ?: "unknown",
    auditType = this.auditType,
    totalCardCount = this.ncards,
    contestCount = this.ncontests,
    cvrsContainUndervotes = this.cvrsContainUndervotes,
    // poolsHaveOneCardStyle = this.poolsHaveOneCardStyle,
    pollingMode = this.pollingMode,
    mvrSource = this.mvrSource ?: if (auditType.isClca()) MvrSource.testClcaSimulated else MvrSource.testPrivateMvrs,
    other = if (this.other == null) emptyMap() else this.other.import(),
)

fun Map<String, Any>.publishJson(): Map<String, String> {
    return this.mapValues { it.value.toString() }
}

fun Map<String, String>.import(): Map<String, Double> {
    return this.mapValues { it.value.toDouble() } // TODO type info ??
}

/////////////////////////////////////////////////////////////////////////////////

fun writeElectionInfoJsonFile(electionInfo: ElectionInfo, filename: String) {
    val json = electionInfo.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(json, out)
        out.close()
    }
}

fun readElectionInfoJsonFile(filename: String): Result<ElectionInfo, ErrorMessages> {
    val errs = ErrorMessages("readElectionInfoJsonFile '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<ElectionInfoJson>(inp)
            val auditConfig = json.import()
            if (errs.hasErrors()) Err(errs) else Ok(auditConfig)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}

fun readElectionInfoUnwrapped(filename: String): ElectionInfo? {
    val result = readElectionInfoJsonFile(filename)
    return if (result.isOk) result.unwrap() else null
}