package org.cryptobiotic.rlauxe.reader

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.File

import org.cryptobiotic.rlauxe.*


fun readDominionJsonFromFileOld(filename : String ) : DominionJsonOld {
    println("DominionJsonOld filename = ${filename}")

    //gulp the entire file to a string
    val file = File(filename)
    val text = file.readText(Charsets.UTF_8)

    val serializer = serializer<DominionJsonOld>() // use the default serializer

    // Create the configuration for (de)serialization
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }

    val dominionJson : DominionJsonOld = jsonReader.decodeFromString(serializer, text)
    println("$dominionJson")
    return dominionJson
}

@Serializable
data class DominionJsonOld(
    val Version: String,
    val ElectionId: String,
    val Sessions: List<SessionJsonOld>,
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
data class SessionJsonOld(
    val TabulatorId: Int,
    val BatchId: Int,
    val RecordId: Int,
    val CountingGroupId: Int,
    val ImageMask: String,
    val Original: RecordJsonOld,
    val Modified: RecordJsonOld?,
) {
    fun show(indent: Indent) = buildString {
        appendLine("Session")
        appendLine("${indent}TabulatorId='$TabulatorId'")
        appendLine("${indent}BatchId='$BatchId'")
        appendLine("${indent}RecordId='$RecordId'")
        appendLine("${indent}CountingGroupId='$CountingGroupId'")
        appendLine("${indent}ImageMask='$ImageMask'")
        appendLine("${indent}${Original.show("Original", indent.incr())}")
        if (Modified != null) append("${indent}${Modified.show("Modified", indent.incr())}")
    }
}

@Serializable
data class RecordJsonOld(
    val PrecinctPortionId: Int,
    val BallotTypeId: Int,
    val IsCurrent: Boolean,
    val Contests: List<ContestJsonOld>,
) {
    fun show(name: String, indent: Indent) = buildString {
        appendLine(name)
        appendLine("${indent}PrecinctPortionId='$PrecinctPortionId'")
        appendLine("${indent}BallotTypeId='$BallotTypeId'")
        appendLine("${indent}IsCurrent='$IsCurrent'")
        Contests.forEach{ append("${indent}${it.show(indent.incr())}") }
    }
}

@Serializable
data class ContestJsonOld(
    val Id: Int,
    val Marks: List<MarkJsonOld>
) {
    fun show(indent: Indent) = buildString {
        appendLine("Contest")
        appendLine("${indent}Id='$Id'")
        Marks.forEach{ append("${indent}${it.show(indent.incr())}") }
    }
}

@Serializable
data class MarkJsonOld(
    val CandidateId: Int,
    val PartyId: Int?,
    val Rank: Int,
    val MarkDensity: Int,
    val IsAmbiguous: Boolean,
    val IsVote: Boolean,
) {
    fun show(indent: Indent) = buildString {
        appendLine("Mark")
        appendLine("${indent}CandidateId='$CandidateId'")
        appendLine("${indent}PartyId='$PartyId'")
        appendLine("${indent}Rank='$Rank'")
        appendLine("${indent}IsAmbiguous='$IsAmbiguous'")
        appendLine("${indent}IsVote='$IsVote'")
        appendLine("${indent}IsAmbiguous='$IsAmbiguous'")
    }
}