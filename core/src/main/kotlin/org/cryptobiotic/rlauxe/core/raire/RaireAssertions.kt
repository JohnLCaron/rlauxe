package org.cryptobiotic.rlauxe.core.raire


import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.cryptobiotic.rlauxe.core.RaireAssorter
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

data class RaireResults(
    val overallExpectedPollsNumber : Int,
    val ballotsInvolvedInAuditNumber : Int,
    val contests: List<RaireContestAudit>,
) {
    fun show() = buildString {
        appendLine("overallExpectedPollsNumber=$overallExpectedPollsNumber ballotsInvolvedInAuditNumber=$ballotsInvolvedInAuditNumber")
        contests.forEach { append(it.show()) }
    }
}

fun RaireResultsJson.import() =
    RaireResults(
        this.overallExpectedPollsNumber.toInt(),
        this.ballotsInvolvedInAuditNumber.toInt(),
        this.audits.map { it.import() },
    )

data class RaireContestAudit(
    val contest: String,
    val winner: Int,  // the sum of winner and eliminated must be all the candiates
    val eliminated: List<Int>,
    val expectedPollsNumber : Int,
    val expectedPollsPercent : Double,
    val assertions: List<RaireAssertion>,
)  {
    val candidates =  listOf(winner) + eliminated // seems likely

    fun show() = buildString {
        appendLine("  contest $contest winner $winner eliminated $eliminated")
        assertions.forEach { append(it.show()) }
    }
}

fun RaireContestAuditJson.import() =
    RaireContestAudit(
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
    val alreadyEliminated: List<Int>, // already eliminated for the purpose of this assertion
    val assertionType: String,
    val explanation: String,
)  {
    var assort: RaireAssorter? = null

    fun show() = buildString {
        appendLine("    assertion type '$assertionType' winner $winner loser $loser alreadyEliminated $alreadyEliminated explanation: '$explanation'")
    }

    fun match(winner: Int, loser: Int, winnerType: Boolean, already: List<Int> = emptyList()): Boolean {
        if (this.winner != winner || this.loser != loser) return false
        if (winnerType && (assertionType != "WINNER_ONLY")) return false
        if (!winnerType && (assertionType == "WINNER_ONLY")) return false
        if (winnerType) return true
        return already == alreadyEliminated
    }
}

fun RaireAssertionJson.import() =
    RaireAssertion(
        this.winner.toInt(),
        this.loser.toInt(),
        this.already_eliminated .map { it.toInt() },
        this.assertion_type,
        this.explanation,
    )

// add assorters to the assertions
fun RaireContestAudit.addAssorters(): List<RaireAssorter> {
    return this.assertions.map {
        val assort = RaireAssorter(this, it)
        it.assort = assort
        assort
    }
}
