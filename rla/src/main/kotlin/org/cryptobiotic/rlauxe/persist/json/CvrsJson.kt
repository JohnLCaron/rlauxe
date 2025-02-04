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
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.safeEnumValueOf

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

@Serializable
data class VoteJson(
    val contestId: Int,
    val candidateIds: List<Int>,
)

@Serializable
data class CvrJson(
    val id: String,
    val votes: List<VoteJson>,
    val phantom: Boolean,
    val sampleNumber: Long,
)

fun CvrUnderAudit.publishJson() : CvrJson {
    val votes = this.votes.entries.map { VoteJson(it.key, it.value.toList()) }
    return CvrJson(
        this.id,
        votes,
        this.cvr.phantom,
        this.sampleNum,
    )
}

fun CvrJson.import(): CvrUnderAudit {
    val votes = this.votes.map { Pair(it.contestId, it.candidateIds.toIntArray()) }.toMap()
    return CvrUnderAudit(
        Cvr(
            this.id,
            votes,
            this.phantom,
        ),
        this.sampleNumber)
}

@Serializable
data class CvrsJson(
    val cvrs: List<CvrJson>,
)

fun List<CvrUnderAudit>.publishJson() = CvrsJson(
    this.map { it.publishJson() },
)

fun CvrsJson.import(): List<CvrUnderAudit> {
    return this.cvrs.map { it.import() }
}

///////////////////////////////////////////////////////////////

fun writeCvrsJsonFile(cvrs: List<CvrUnderAudit>, filename: String) {
    val cvrsj = cvrs.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(cvrsj, out)
        out.close()
    }
}

fun readCvrsJsonFile(filename: String): Result<List<CvrUnderAudit>, ErrorMessages> {
    val errs = ErrorMessages("readCvrsJsonFile '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val cvrs = jsonReader.decodeFromStream<CvrsJson>(inp)
            if (errs.hasErrors()) Err(errs) else Ok(cvrs.import())
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}