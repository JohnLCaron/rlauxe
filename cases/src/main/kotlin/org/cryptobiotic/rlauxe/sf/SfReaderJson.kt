package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

@Serializable
data class ContestManifestJson(
    val Version: String,
    val List: List<ContestM>,
) {
    override fun toString() = buildString {
        appendLine("ContestManifestJson(Version='$Version')")
        List.forEach() { appendLine("  $it") }
    }
}

@Serializable
data class ContestM(
    val Description: String,
    val Id: Int,
    val ExternalId: Int,
    val DistrictId: Int,
    val VoteFor: Int,
    val NumOfRanks: Int,
    val Disabled: Int,
)

///////////////////////////////////////////////////////////////

enum class CandidateMType { Regular, WriteIn, QualifiedWriteIn }

@Serializable
data class CandidateManifestJson(
    val Version: String,
    val List: List<CandidateM>,
) {
    override fun toString() = buildString {
        appendLine("CandidateManifestJson(Version='$Version')")
        List.forEach() { appendLine("  $it") }
    }
}

@Serializable
data class CandidateM(
    val Description: String,
    val Id: Int,
    val ExternalId: String,
    val ContestId: Int,
    val Type: CandidateMType,
    val Disabled: Int,
)

//////////////////////////////////////////////////////////////////////////////////////////////

fun readContestManifestJson(filename: String): Result<ContestManifestJson, ErrorMessages> {
    val errs = ErrorMessages("readContestManifestJsonFile '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<ContestManifestJson>(inp)
            if (errs.hasErrors()) Err(errs) else Ok(json)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}

fun readCandidateManifestJson(filename: String): Result<CandidateManifestJson, ErrorMessages> {
    val errs = ErrorMessages("readCandidateManifestJson '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<CandidateManifestJson>(inp)
            if (errs.hasErrors()) Err(errs) else Ok(json)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}

fun readContestManifestForIRV(filename: String): Set<Int>{
    val result: Result<ContestManifestJson, ErrorMessages> = readContestManifestJson(filename)
    val contestManifest = if (result is Ok) result.unwrap()
    else throw RuntimeException("Cannot read ContestManifestJson from ${filename} err = $result")
    var irvIds = mutableSetOf<Int>()
    contestManifest.List.forEach {
        if (it.NumOfRanks > 1) irvIds.add(it.Id)
    }
    return irvIds
}
