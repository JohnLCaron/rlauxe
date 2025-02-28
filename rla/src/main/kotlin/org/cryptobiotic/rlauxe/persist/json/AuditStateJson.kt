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
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.workflow.*

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

// per round
//data class AuditState(
//    val name: String,
//    val nmvrs: Int,
//    val contests: List<ContestUnderAudit>,
//    val done: Boolean,
//)

// one in each roundXX subdirectory
@Serializable
data class AuditStateJson(
    val name: String,
    val roundIdx: Int,
    val nmvrs: Int,
    val auditWasDone: Boolean,
    val auditIsComplete: Boolean,
    val contests: List<ContestUnderAuditJson>,
)

fun AuditState.publishJson() : AuditStateJson {
    return AuditStateJson(
        this.name,
        this.roundIdx,
        this.nmvrs,
        this.auditWasDone,
        this.auditIsComplete,
        this.contests.map { it.publishJson() },
    )
}

fun AuditStateJson.import(): AuditState {
    return AuditState(
        this.name,
        this.roundIdx,
        this.nmvrs,
        this.auditWasDone,
        this.auditIsComplete,
        this.contests.map { it.import() },
    )
}

/////////////////////////////////////////////////////////////////////////////////

fun writeAuditStateJsonFile(auditState: AuditState, filename: String) {
    val json = auditState.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(json, out)
        out.close()
    }
}

fun readAuditStateJsonFile(filename: String): Result<AuditState, ErrorMessages> {
    val errs = ErrorMessages("readAuditConfigJsonFile '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<AuditStateJson>(inp)
            val auditState = json.import()
            if (errs.hasErrors()) Err(errs) else Ok(auditState)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}