package org.cryptobiotic.rlauxe.dominion

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.persist.csv.publishCsv
import org.cryptobiotic.rlauxe.persist.csv.writeCSV
import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption


@Serializable
data class DominionCvrExportJson(
    val Version: String,
    val ElectionId: String,
    val Sessions: List<Session>,
) {
    override fun toString() = buildString {
        appendLine("DominionCvrJson(Version='$Version', ElectionId='$ElectionId')")
        Sessions.forEach() { append("  $it") }
    }
}

@Serializable
data class Session(
    val TabulatorId: Int,
    val BatchId: Int,
    val RecordId: String,
    val CountingGroupId: Int,
    val ImageMask: String,
    val SessionType: String,
    val VotingSessionIdentifier: String,
    val UniqueVotingIdentifier: String,
    val Original: Original,
) {
    override fun toString() = buildString {
        appendLine("Session(TabulatorId=$TabulatorId, BatchId=$BatchId, RecordId='$RecordId', CountingGroupId=$CountingGroupId, ImageMask='$ImageMask', SessionType='$SessionType', VotingSessionIdentifier='$VotingSessionIdentifier', UniqueVotingIdentifier='$UniqueVotingIdentifier')")
        appendLine("    $Original")
    }
}

@Serializable
data class Original(
    val PrecinctPortionId: Int,
    val BallotTypeId: Int,
    val IsCurrent: Boolean,
    val Cards: List<Card>,
) {
    override fun toString() = buildString {
        appendLine("Original(PrecinctPortionId=$PrecinctPortionId, BallotTypeId=$BallotTypeId, IsCurrent=$IsCurrent)")
        Cards.forEach() { append("    $it") }
    }
}

@Serializable
data class Card(
    val Id: Int,
    val KeyInId: Int,
    val PaperIndex: Int,
    val Contests: List<Contest>,
) {
    override fun toString() = buildString {
        appendLine("Card(Id=$Id, KeyInId=$KeyInId, PaperIndex=$PaperIndex)")
        Contests.forEach() { append("      $it") }
    }}

@Serializable
data class Contest(
    val Id: Int,
    val ManifestationId: Int,
    val Undervotes: Int,
    val Overvotes: Int,
    val OutstackConditionIds: List<Int>,
    val Marks: List<Mark>
) {
    override fun toString() = buildString {
        appendLine("Contest(Id=$Id, ManifestationId=$ManifestationId, Undervotes=$Undervotes, Overvotes=$Overvotes)")
        Marks.forEach() { append("        $it") }
    }
}

@Serializable
data class Mark(
    val CandidateId: Int,
    val ManifestationId: Int,
    val PartyId: Int?,
    val Rank: Int,
    val MarkDensity: Int,
    val IsAmbiguous: Boolean,
    val IsVote: Boolean,
    val OutstackConditionIds: List<Int>,
) {
    override fun toString() = buildString {
        appendLine("Mark(CandidateId=$CandidateId, ManifestationId=$ManifestationId, PartyId=$PartyId, Rank=$Rank, MarkDensity=$MarkDensity, IsAmbiguous=$IsAmbiguous, IsVote=$IsVote)")
    }
}

fun DominionCvrExportJson.import(irvContests:Set<Int>) : List<Cvr> {
    val result = mutableListOf<Cvr>()
    Sessions.forEach { session ->
        session.Original.Cards.forEach { card ->
            val votes = mutableMapOf<Int, IntArray>()
            card.Contests.forEach { contest ->
                if (irvContests.contains(contest.Id)) {
                    val contestVoteAndRank = mutableListOf<Pair<Int, Int>>()
                    contest.Marks.forEach { mark ->
                        contestVoteAndRank.add(Pair(mark.Rank, mark.CandidateId))
                    }
                    val sortedVotes = contestVoteAndRank.sortedBy { it.first }.map { it.second }
                    votes[contest.Id] = sortedVotes.toIntArray()
                } else {
                    val contestVotes = mutableListOf<Int>()
                    contest.Marks.forEach { mark ->
                        contestVotes.add(mark.CandidateId)
                    }
                    votes[contest.Id] = contestVotes.toIntArray()
                }
            }
            result.add( Cvr("${session.TabulatorId}-${session.BatchId}-${card.Id}", votes, false))
        }
    }
    return result
}

fun Session.import(irvContests: Set<Int>): List<Cvr> {
    val result = mutableListOf<Cvr>()
    this.Original.Cards.forEach { card ->
        val votes = mutableMapOf<Int, IntArray>()
        card.Contests.forEach { contest ->
            if (irvContests.contains(contest.Id)) {
                val contestVoteAndRank = mutableListOf<Pair<Int, Int>>()
                contest.Marks.forEach { mark ->
                    contestVoteAndRank.add(Pair(mark.Rank, mark.CandidateId))
                }
                val sortedVotes = contestVoteAndRank.sortedBy { it.first }.map { it.second }
                votes[contest.Id] = sortedVotes.toIntArray()
            } else {
                val contestVotes = mutableListOf<Int>()
                contest.Marks.forEach { mark ->
                    contestVotes.add(mark.CandidateId)
                }
                votes[contest.Id] = contestVotes.toIntArray()
            }
        }
        result.add(Cvr("${this.TabulatorId}-${this.BatchId}-${card.Id}", votes, false))
    }
    return result
}

fun convertCvrExportToCvr(inputStream: InputStream, outputStream: OutputStream, irvIds: Set<Int>): Int {
    val result: Result<DominionCvrExportJson, ErrorMessages> = readDominionCvrJsonStream(inputStream)
    val dominionCvrs = if (result is Ok) result.unwrap()
    else throw RuntimeException("Cannot read DominionCvrJson err = $result")
    // println(dominionCvrs)

    val cvrs = dominionCvrs.import(irvIds)
    // println("ncvrs = ${cvrs.size}")
    // cvrs.forEach { println(it) }

    //println("==================================================")
    // print(CvrCsv.header)
    cvrs.forEach {
        val cvrUA = CvrUnderAudit(it, 0, 0)
        outputStream.write(writeCSV(cvrUA.publishCsv()).toByteArray()) // UTF-8
    }
    return cvrs.size
}

//////////////////////////////////////////////////////////////////////////////////////////////

fun readDominionCvrJsonFile(filename: String): Result<DominionCvrExportJson, ErrorMessages> {
    val errs = ErrorMessages("readDominionCvrJsonFile '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<DominionCvrExportJson>(inp)
            if (errs.hasErrors()) Err(errs) else Ok(json)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}

fun readDominionCvrJsonStream(inputStream: InputStream): Result<DominionCvrExportJson, ErrorMessages> {
    val errs = ErrorMessages("readDominionCvrJsonFile from inputStream")
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        val json = jsonReader.decodeFromStream<DominionCvrExportJson>(inputStream)
        if (errs.hasErrors()) Err(errs) else Ok(json)
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}