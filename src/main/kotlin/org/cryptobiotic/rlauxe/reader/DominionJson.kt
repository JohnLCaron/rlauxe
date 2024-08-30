package org.cryptobiotic.rlauxe.reader

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.File

import org.cryptobiotic.rlauxe.*


fun readDominionJsonFromFile(filename : String ) : DominionJson {
    println("DominionJson filename = ${filename}")

    //gulp the entire file to a string
    val file = File(filename)
    val text = file.readText(Charsets.UTF_8)

    val serializer = serializer<DominionJson>() // use the default serializer

    // Create the configuration for (de)serialization
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }

    val dominionJson : DominionJson = jsonReader.decodeFromString(serializer, text)
    println("$dominionJson")
    return dominionJson
}

@Serializable
data class DominionJson(
    val Version: String,
    val ElectionId: String,
    val Sessions: List<SessionJson>,
) {
    override fun toString() = buildString {
        val indent = Indent(2)
        appendLine("DominionJson")
        appendLine("${indent}Version=$Version")
        appendLine("${indent}ElectionId=$ElectionId")
        Sessions.forEach{ append("${indent}${it.show(indent.incr())}") }
    }
}

@Serializable
data class SessionJson(
    val TabulatorId: Int,
    val BatchId: Int,
    val RecordId: Int, // TODO "RecordId": 0,
    val CountingGroupId: Int,
    val ImageMask: String,
    val SessionType: String,
    val VotingSessionIdentifier: String,
    val UniqueVotingIdentifier: String,
    val Original: RecordJson,
    val Modified: RecordJson?,
) {
    fun show(indent: Indent) = buildString {
        appendLine("Session")
        appendLine("${indent}TabulatorId='$TabulatorId'")
        appendLine("${indent}BatchId='$BatchId'")
        appendLine("${indent}RecordId='$RecordId'")
        appendLine("${indent}CountingGroupId='$CountingGroupId'")
        appendLine("${indent}ImageMask='$ImageMask'")
        appendLine("${indent}SessionType='$SessionType'")
        appendLine("${indent}VotingSessionIdentifier='$VotingSessionIdentifier'")
        appendLine("${indent}UniqueVotingIdentifier='$UniqueVotingIdentifier'")
        appendLine("${indent}${Original.show("Original", indent.incr())}")
        if (Modified != null) append("${indent}${Modified.show("Modified", indent.incr())}")
    }
}

@Serializable
data class RecordJson(
    val PrecinctPortionId: Int,
    val BallotTypeId: Int,
    val IsCurrent: Boolean,
    val Cards: List<CardJson>,
) {
    fun show(name: String, indent: Indent) = buildString {
        appendLine(name)
        appendLine("${indent}PrecinctPortionId='$PrecinctPortionId'")
        appendLine("${indent}BallotTypeId='$BallotTypeId'")
        appendLine("${indent}IsCurrent='$IsCurrent'")
        Cards.forEach{ append("${indent}${it.show(indent.incr())}") }
    }
}

@Serializable
data class CardJson(
    val Id: Int,
    val KeyInId: Int,
    val PaperIndex: Int,
    val Contests: List<ContestJson>,
    val OutstackConditionIds: List<Int>,
) {
    fun show(indent: Indent) = buildString {
        appendLine("Card")
        appendLine("${indent}Id='$Id'")
        appendLine("${indent}KeyInId='$KeyInId'")
        appendLine("${indent}PaperIndex='$PaperIndex'")
        Contests.forEach{ append("${indent}${it.show(indent.incr())}") }
        appendLine("${indent}OutstackConditionIds=$OutstackConditionIds")
    }
}

@Serializable
data class ContestJson(
    val Id: Int,
    val ManifestationId: Int,
    val Undervotes: Int,
    val Overvotes: Int,
    val OutstackConditionIds: List<Int>,
    val Marks: List<MarkJson>
) {
    fun show(indent: Indent) = buildString {
        appendLine("Contest")
        appendLine("${indent}Id='$Id'")
        appendLine("${indent}ManifestationId='$ManifestationId'")
        appendLine("${indent}Undervotes='$Undervotes'")
        appendLine("${indent}Overvotes='$Overvotes'")
        appendLine("${indent}OutstackConditionIds=$OutstackConditionIds")
        Marks.forEach{ append("${indent}${it.show(indent.incr())}") }
    }
}

@Serializable
data class MarkJson(
    val CandidateId: Int,
    val ManifestationId: Int,
    val PartyId: Int?,
    val Rank: Int,
    val WriteinIndex: Int?,
    val MarkDensity: Int,
    val WriteinDensity: Int?,
    val IsAmbiguous: Boolean,
    val IsVote: Boolean,
    val OutstackConditionIds: List<Int>,
) {
    fun show(indent: Indent) = buildString {
        appendLine("Mark")
        appendLine("${indent}CandidateId='$CandidateId'")
        appendLine("${indent}ManifestationId='$ManifestationId'")
        appendLine("${indent}PartyId='$PartyId'")
        appendLine("${indent}Rank='$Rank'")
        appendLine("${indent}WriteinIndex='$WriteinIndex'")
        appendLine("${indent}WriteinDensity='$WriteinDensity'")
        appendLine("${indent}IsAmbiguous='$IsAmbiguous'")
        appendLine("${indent}IsVote='$IsVote'")
        appendLine("${indent}IsAmbiguous='$IsAmbiguous'")
        appendLine("${indent}OutstackConditionIds=$OutstackConditionIds")
    }
}