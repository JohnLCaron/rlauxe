package org.cryptobiotic.rlauxe.raire


import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

// reading RAIRE JSON result files
@Serializable
data class RaireResultsJson(
    @SerialName("Overall Expected Polls (#)")
    val overallExpectedPollsNumber : String,
    @SerialName("Ballots involved in audit (#)")
    val ballotsInvolvedInAuditNumber : String,
    val audits: List<RaireContestAuditJson>,
)

@Serializable
data class RaireContestAuditJson(
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

fun RaireResultsJson.import() =
    RaireResults(
        this.overallExpectedPollsNumber.toInt(),
        this.ballotsInvolvedInAuditNumber.toInt(),
        this.audits.map { it.import() },
    )

fun RaireContestAuditJson.import() =
    RaireContestAudit(
        this.contest,
        this.winner.toInt(),
        this.eliminated .map { it.toInt() },
        this.expectedPollsNumber.toInt(),
        this.expectedPollsPercent.toDouble(),
        this.assertions.map { it.import() },
    )

fun RaireAssertionJson.import() =
    RaireAssertion(
        this.winner.toInt(),
        this.loser.toInt(),
        this.already_eliminated .map { it.toInt() },
        this.assertion_type,
        this.explanation,
    )