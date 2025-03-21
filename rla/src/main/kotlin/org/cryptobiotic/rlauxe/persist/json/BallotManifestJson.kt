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
import org.cryptobiotic.rlauxe.audit.Ballot
import org.cryptobiotic.rlauxe.audit.BallotManifestUnderAudit
import org.cryptobiotic.rlauxe.audit.BallotStyle
import org.cryptobiotic.rlauxe.audit.BallotUnderAudit
import org.cryptobiotic.rlauxe.util.ErrorMessages

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

// data class BallotStyle(
//    val name: String,
//    val id: Int,
//    val contestNames: List<String>,
//    val contestIds: List<Int>,
//    val numberOfBallots: Int?,
//)
@Serializable
data class BallotStyleJson(
    val name: String,
    val id: Int,
    val contestNames: List<String>,
    val contestIds: List<Int>,
    val numberOfBallots: Int?
)

fun BallotStyle.publishJson() = BallotStyleJson(
        this.name,
        this.id,
        this.contestNames,
        this.contestIds,
        this.numberOfBallots,
    )

fun BallotStyleJson.import() = BallotStyle(
        this.name,
        this.id,
        this.contestNames,
        this.contestIds,
        this.numberOfBallots,
    )

// data class Ballot(
//    val id: String,
//    val phantom: Boolean = false,
//    val ballotStyle: BallotStyle?, // if hasStyles
//    val contestIds: List<Int>? = null, // if hasStyles, instead of BallotStyles
//)
@Serializable
data class BallotJson(
    val id: String,
    val phantom: Boolean,
    val ballotStyle: BallotStyleJson?,
    val contestIds: List<Int>?,
    val index: Int,
    val sampleNumber: Long,
)

fun BallotUnderAudit.publishJson() : BallotJson {
    return BallotJson(
        this.id,
        this.phantom,
        this.ballot.ballotStyle?.publishJson(),
        this.ballot.contestIds,
        this.index,
        this.sampleNum,
    )
}

fun BallotJson.import(): BallotUnderAudit {
    return BallotUnderAudit(
        Ballot(
            this.id,
            this.phantom,
            this.ballotStyle?.import(),
            this.contestIds,
        ),
        this.index,
        this.sampleNumber)
}

// data class BallotManifest(
//    val ballots: List<Ballot>,
//    val ballotStyles: List<BallotStyle> // empty if style info not available
//)
@Serializable
data class BallotManifestJson(
    val ballots: List<BallotJson>,
    val ballotStyles: List<BallotStyleJson>,
)

fun BallotManifestUnderAudit.publishJson() = BallotManifestJson (
        this.ballots.map { it.publishJson() },
        this.ballotStyles.map { it.publishJson() },
    )

fun BallotManifestJson.import() = BallotManifestUnderAudit(
        this.ballots.map { it.import() },
        this.ballotStyles.map { it.import() }
    )

///////////////////////////////////////////////////////////////

fun writeBallotManifestJsonFile(ballotManifest: BallotManifestUnderAudit, filename: String) {
    val json = ballotManifest.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(json, out)
        out.close()
    }
}

fun readBallotManifestJsonFile(filename: String): Result<BallotManifestUnderAudit, ErrorMessages> {
    val errs = ErrorMessages("readBallotManifestJsonFile '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val cvrs = jsonReader.decodeFromStream<BallotManifestJson>(inp)
            if (errs.hasErrors()) Err(errs) else Ok(cvrs.import())
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}