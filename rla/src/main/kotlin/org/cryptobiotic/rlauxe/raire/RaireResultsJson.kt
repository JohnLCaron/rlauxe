package org.cryptobiotic.rlauxe.raire

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption


// reading RAIRE JSON assertion files
// TestReadRaireResultsJson reads "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/334_361_vbm.json"
// TestRaireWorkflowFromJson reads   "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheetsAssertions.json"

// The output of RAIRE assertion generator, read from JSON files
data class RaireResults(
    val overallExpectedPollsNumber : Int,  // what is this?
    val ballotsInvolvedInAuditNumber : Int, // what is this?
    val contests: List<RaireContestUnderAudit>,
) {
    fun show() = buildString {
        appendLine("RaireResults: overallExpectedPollsNumber=$overallExpectedPollsNumber ballotsInvolvedInAuditNumber=$ballotsInvolvedInAuditNumber")
        contests.forEach { append(it.showShort()) }
    }
}

@Serializable
data class RaireResultsJson(
    @SerialName("Overall Expected Polls (#)")
    val overallExpectedPollsNumber : String?,
    @SerialName("Ballots involved in audit (#)")
    val ballotsInvolvedInAuditNumber : String?,
    val audits: List<RaireResultsContestAuditJson>,
)

fun RaireResultsJson.import(ncs: Map<String, Int>, nps: Map<String, Int>) =
    RaireResults(
        this.overallExpectedPollsNumber?.toInt() ?: 0,
        this.ballotsInvolvedInAuditNumber?.toInt() ?: 0,
        this.audits.map { it.import(ncs[it.contest]!!, nps[it.contest]!!) },
    )

@Serializable
data class RaireResultsContestAuditJson(
    val contest: String,
    val winner: String,
    val eliminated: List<String>,
    @SerialName("Expected Polls (#)")
    val expectedPollsNumber : String,
    @SerialName("Expected Polls (%)")
    val expectedPollsPercent : String,
    val assertions: List<RaireResultsAssertionJson>,
)

fun RaireResultsContestAuditJson.import(Nc: Int, Np: Int) =
    RaireContestUnderAudit.make(
        this.contest,
        this.winner.toInt(),
        Nc = Nc,
        Np = Np,
        this.eliminated.map { it.toInt() }, // eliminated
        // this.expectedPollsNumber.toInt(),
        // this.expectedPollsPercent.toDouble(),
        this.assertions.map { it.import() },
    )

@Serializable
data class RaireResultsAssertionJson(
    val winner: String,
    val loser: String,
    val assertion_type: String,
    val already_eliminated: List<String>,
    val explanation: String?,
)

fun RaireResultsAssertionJson.import(): RaireAssertion {
    return RaireAssertion(
        this.winner.toInt(),
        this.loser.toInt(),
        0, // not available, calculate instead
        RaireAssertionType.fromString(this.assertion_type),
        this.already_eliminated.map { it.toInt() },  // must invert
        emptyMap(),
        this.explanation,
    )
}

@OptIn(ExperimentalSerializationApi::class)
fun readRaireResultsJson(filename: String): RaireResultsJson {
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    Files.newInputStream(Path.of(filename), StandardOpenOption.READ).use { inp ->
        val result =  jsonReader.decodeFromStream<RaireResultsJson>(inp)
        // println(Json.encodeToString(result))
        return result
    }
}
