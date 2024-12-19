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
// TestRcvAssorter reads "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/334_361_vbm.json"
// TestRaireAssertions,AssertionRLAipynb  reads "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SF2019Nov8Assertions.json"
// TestRaireWorkflow reads   "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheetsAssertions.json"

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

fun RaireResultsJson.import(ncs: Map<String, Int>) =
    RaireResults(
        this.overallExpectedPollsNumber.toInt(),
        this.ballotsInvolvedInAuditNumber.toInt(),
        this.audits.map { it.import(ncs[it.contest]!!) },
    )

fun RaireContestAuditJson.import(Nc: Int) =
    RaireContestUnderAudit.make(
        this.contest,
        this.winner.toInt(),
        Nc,
        this.eliminated .map { it.toInt() },
        this.expectedPollsNumber.toInt(),
        this.expectedPollsPercent.toDouble(),
        this.assertions.map { it.import() },
    )

fun RaireAssertionJson.import() =
    RaireAssertion(
        this.winner.toInt(),
        this.loser.toInt(),
        this.already_eliminated.map { it.toInt() },
        RaireAssertionType.fromString(this.assertion_type),
        this.explanation,
    )
