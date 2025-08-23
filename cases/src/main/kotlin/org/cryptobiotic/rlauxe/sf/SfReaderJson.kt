package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.ZipReader
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

data class ContestManifest(val contests: Map<Int, ContestMJson>, val irvContests: Set<Int>)


@Serializable
data class ContestManifestJson(
    val Version: String,
    val List: List<ContestMJson>,
) {
    override fun toString() = buildString {
        appendLine("ContestManifestJson(Version='$Version')")
        List.forEach() { appendLine("  $it") }
    }
}

@Serializable
data class ContestMJson(
    val Description: String,
    val Id: Int,
    val ExternalId: Int,
    val DistrictId: Int,
    val VoteFor: Int,
    val NumOfRanks: Int,
    val Disabled: Int,
)

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

fun readContestManifestJson(input: InputStream, filename: String = "InputStream"): Result<ContestManifestJson, ErrorMessages> {
    val errs = ErrorMessages("readContestManifestJson from '${filename}'")
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        val json = jsonReader.decodeFromStream<ContestManifestJson>(input)
        if (errs.hasErrors()) Err(errs) else Ok(json)
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}

fun readContestManifestJsonFromZip(zipFilename: String, contestManifestFilename: String): Result<ContestManifestJson, ErrorMessages> {
    val reader = ZipReader(zipFilename)
    val input = reader.inputStream(contestManifestFilename)
    return readContestManifestJson(input, contestManifestFilename)
}

fun readContestManifest(filename: String): ContestManifest {
    val result: Result<ContestManifestJson, ErrorMessages> = readContestManifestJson(filename)
    val contestManifestJson = if (result is Ok) result.unwrap()
        else throw RuntimeException("Cannot read ContestManifestJson from ${filename} err = $result")

    val contests = mutableMapOf<Int, ContestMJson>()
    val irvIds = mutableSetOf<Int>()
    contestManifestJson.List.forEach {
        contests[it.Id] = it
        if (it.NumOfRanks > 1) irvIds.add(it.Id)
    }
    return ContestManifest(contests, irvIds)
}

fun readContestManifestFromZip(zipFilename: String, contestManifestFilename: String): ContestManifest {
    val reader = ZipReader(zipFilename)
    val input = reader.inputStream(contestManifestFilename)
    val result: Result<ContestManifestJson, ErrorMessages> = readContestManifestJson(input, contestManifestFilename)
    val contestManifestJson = if (result is Ok) result.unwrap()
        else throw RuntimeException("Cannot read ContestManifestJson from inputStream err = $result")

    val contests = mutableMapOf<Int, ContestMJson>()
    val irvIds = mutableSetOf<Int>()
    contestManifestJson.List.forEach {
        contests[it.Id] = it
        if (it.NumOfRanks > 1) irvIds.add(it.Id)
    }
    return ContestManifest(contests, irvIds)
}

//////////////////////////////////////////////////////////////////////////////////////////////

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

fun readCandidateManifestJson(input: InputStream, filename: String): Result<CandidateManifestJson, ErrorMessages> {
    val errs = ErrorMessages("readCandidateManifestJson '${filename}'")
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        val json = jsonReader.decodeFromStream<CandidateManifestJson>(input)
        if (errs.hasErrors()) Err(errs) else Ok(json)
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}

fun readCandidateManifestJsonFromZip(zipFilename: String, filename: String): Result<CandidateManifestJson, ErrorMessages> {
    val reader = ZipReader(zipFilename)
    val input = reader.inputStream(filename)
    return readCandidateManifestJson(input, filename)
}

//////////////////////////////////////////////////////////////////////////////////////////////

@Serializable
data class BallotTypeContestManifestJson(
    val List: List<BallotTypeContestJson>,
)

@Serializable
data class BallotTypeContestJson(
    val BallotTypeId: Int,
    val ContestId: Int,
)

data class BallotTypeContestManifest (
    val ballotStyles: Map<Int, IntArray> // ballot style id -> contest Ids
) {
    override fun toString() = buildString {
        ballotStyles.forEach { (key, value) -> appendLine("  $key == ${value.contentToString()}") }
    }
}

fun BallotTypeContestManifestJson.import(): BallotTypeContestManifest {
    val contestMap = mutableMapOf<Int, MutableList<Int>>()

    this.List.forEach {
        val contestIds = contestMap[it.BallotTypeId] ?: mutableListOf<Int>()
        contestIds.add(it.ContestId)
        contestMap[it.BallotTypeId] = contestIds
    }

    // TODO should we sort them?
    return BallotTypeContestManifest( contestMap.mapValues { it.value.toIntArray()} )
}


fun readBallotTypeContestManifestJson(filename: String): Result<BallotTypeContestManifest, ErrorMessages> {
    val errs = ErrorMessages("readBallotTypeContestManifestJson '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<BallotTypeContestManifestJson>(inp)
            if (errs.hasErrors()) Err(errs) else Ok(json.import() )
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}

fun readBallotTypeContestManifestJson(input: InputStream, filename: String): Result<BallotTypeContestManifest, ErrorMessages> {
    val errs = ErrorMessages("readCandidateManifestJson '${filename}'")
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        val json = jsonReader.decodeFromStream<BallotTypeContestManifestJson>(input)
        if (errs.hasErrors()) Err(errs) else Ok(json.import() )
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}

fun readBallotTypeContestManifestJsonFromZip(zipFilename: String, filename: String): Result<BallotTypeContestManifest, ErrorMessages> {
    val reader = ZipReader(zipFilename)
    val input = reader.inputStream(filename)
    return readBallotTypeContestManifestJson(input, filename)
}

