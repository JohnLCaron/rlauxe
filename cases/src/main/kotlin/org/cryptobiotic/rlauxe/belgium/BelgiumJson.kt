package org.cryptobiotic.rlauxe.belgium

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

@Serializable
data class BelgiumElectionJson(
    val NrOfEligibleVoters: Int,
    val NrOfValidVotes: Int,
    val NrOfBlankVotes: Int,
    val NisCode: Int,
    val ElectionDate: String,
    val ElectionLists: List<ElectionList>,
) {
    override fun toString() = buildString {
        appendLine("BelgiumElectionJson(NrOfEligibleVoters=$NrOfEligibleVoters, NrOfValidVotes=$NrOfValidVotes, NrOfBlankVotes=$NrOfBlankVotes, NisCode=$NisCode, ElectionDate='$ElectionDate')")
        ElectionLists.forEachIndexed { idx, it ->
            append("${idx+1} $it")
        }
    }
}

@Serializable
data class ElectionList(
    val NrOfSeats: Int,
    val NrOfVotes: Int,
    val PartyLabel: String,
    val Candidates: List<Candidate>,
) {
    override fun toString() = buildString {
        appendLine("  ElectionList(NrOfSeats=$NrOfSeats, NrOfVotes=$NrOfVotes, PartyLabel='$PartyLabel'")
        // Candidates.forEach { appendLine("    $it") }
    }
}

@Serializable
data class Candidate(
    val FullName: String,
    val NrOfVotes: Int,
)

//////////////////////////////////////////////////////////////////////////////////////////////

fun readBelgiumElectionJson(filename: String): Result<BelgiumElectionJson, ErrorMessages> {
    val errs = ErrorMessages("readBelgiumElectionJson '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<BelgiumElectionJson>(inp)
            if (errs.hasErrors()) Err(errs) else Ok(json)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}