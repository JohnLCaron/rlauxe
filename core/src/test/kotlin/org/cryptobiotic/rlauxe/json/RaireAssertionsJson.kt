package org.cryptobiotic.rlauxe.json


import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

@Serializable
data class RaireResultsJson(
    @SerialName("Overall Expected Polls (#)")
    val overallExpectedPollsNumber : String,
    @SerialName("Ballots involved in audit (#)")
    val ballotsInvolvedInAuditNumber : String,
    val audits: List<RaireAuditJson>,
)

@Serializable
data class RaireAuditJson(
    val contest: String,
    val winner: String,
    val eliminated: List<String>,
    @SerialName("Expected Polls (#)")
    val expectedPollsNumber : String,
    @SerialName("Expected Polls (%)")
    val expectedPollsPercent : String,
    val assertions: List<RaireAssertionJson>,
)

@Serializable
data class RaireAssertionJson(
    val winner: String,
    val loser: String,
    val already_eliminated: List<String>,
    val assertion_type: String,
    val explanation: String,
)

@OptIn(ExperimentalSerializationApi::class)
fun readRaireResults(filename: String): RaireResultsJson {
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    Files.newInputStream(Path.of(filename), StandardOpenOption.READ).use { inp ->
        val result =  jsonReader.decodeFromStream<RaireResultsJson>(inp)
        // println(Json.encodeToString(result))
        return result
    }
}

data class RaireResults(
    val overallExpectedPollsNumber : Int,
    val ballotsInvolvedInAuditNumber : Int,
    val audits: List<RaireAudit>,
)

fun RaireResultsJson.import() =
    RaireResults(
        this.overallExpectedPollsNumber.toInt(),
        this.ballotsInvolvedInAuditNumber.toInt(),
        this.audits.map { it.import() },
    )

data class RaireAudit(
    val contest: String,
    val winner: Int,
    val eliminated: List<Int>,
    val expectedPollsNumber : Int,
    val expectedPollsPercent : Double,
    val assertions: List<RaireAssertion>,
)

fun RaireAuditJson.import() =
    RaireAudit(
        this.contest,
        this.winner.toInt(),
        this.eliminated .map { it.toInt() },
        this.expectedPollsNumber.toInt(),
        this.expectedPollsPercent.toDouble(),
        this.assertions.map { it.import() },
    )

data class RaireAssertion(
    val winner: Int,
    val loser: Int,
    val alreadyEliminated: List<Int>,
    val assertionType: String,
    val explanation: String,
)

fun RaireAssertionJson.import() =
    RaireAssertion(
        this.winner.toInt(),
        this.loser.toInt(),
        this.already_eliminated .map { it.toInt() },
        this.assertion_type,
        this.explanation,
    )
