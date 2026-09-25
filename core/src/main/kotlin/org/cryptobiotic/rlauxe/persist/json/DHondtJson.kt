@file:OptIn(ExperimentalSerializationApi::class)
package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.core.AboveThreshold
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.BelowThreshold
import org.cryptobiotic.rlauxe.dhondt.AllSeats
import org.cryptobiotic.rlauxe.dhondt.CandSeatRangeBuilder
import org.cryptobiotic.rlauxe.dhondt.CandidateSeats
import org.cryptobiotic.rlauxe.dhondt.DHondtAssorter
import org.cryptobiotic.rlauxe.dhondt.DHondtContest
import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path

// [
//  {"assertion": {"type": "AT", "party": 0}, "difficulty": 3.4, "margin": 4000},
//  {"assertion": {"type": "BT", "party": 1}, "difficulty": 3.0, "margin": 4500},
//  {"assertion": {"type": "DH", "winner": 0, "loser": 1, "winnerLowestWinner": 2, "loserHighestLoser": 2}, "difficulty": 5.1, "margin": 2100},
//  {"assertion": {"type": "DH", "winner": 0, "loser": 2, "winnerLowestWinner": 2, "loserHighestLoser": 1}, "difficulty": 2.2, "margin": 9000},
//  {"assertion": {"type": "DH", "winner": 1, "loser": 0, "winnerLowestWinner": 1, "loserHighestLoser": 4}, "difficulty": 2.5, "margin": 8000},
//  {"assertion": {"type": "DH", "winner": 1, "loser": 2, "winnerLowestWinner": 1, "loserHighestLoser": 1}, "difficulty": 2.0, "margin": 9500},
//  {"assertion": {"type": "DH", "winner": 0, "loser": 1, "winnerLowestWinner": 3, "loserHighestLoser": 3}, "difficulty": 6.0, "margin": 1500},
//  {"assertion": {"type": "DH", "winner": 1, "loser": 0, "winnerLowestWinner": 2, "loserHighestLoser": 4}, "difficulty": 4.0, "margin": 3000},
//  {"assertion": {"type": "DH", "winner": 0, "loser": 2, "winnerLowestWinner": 3, "loserHighestLoser": 2}, "difficulty": 2.8, "margin": 7000},
//  {"assertion": {"type": "DH", "winner": 1, "loser": 2, "winnerLowestWinner": 2, "loserHighestLoser": 2}, "difficulty": 3.1, "margin": 6000}
//]

@Serializable
data class RelaxedAssertionContestsJson(
    val contests: List<RelaxedAssertionsJson>,
) {
    override fun toString()= buildString {
        appendLine("RelaxedAssertionContestsJson")
        contests.forEach { appendLine(it)}
    }
}

fun List<RelaxedAssertions>.publishJson() = RelaxedAssertionContestsJson(
        this.map { it.publishJson() }
    )

@Serializable
data class Bound(
    val party: Int,
    val name: String,
    val minSeats: Int,
    val maxSeats: Int,
)

data class RelaxedAssertions(
    val dcontest: DHondtContest,
    val assorters: List<AssorterIF>,
    val candidates: List<CandidateSeats>,
)

@Serializable
data class RelaxedAssertionsJson(
    val contest: String,
    val assertions: List<DAssertionWithMarginJson>,
    val seats: Int,
    val bounds: List<Bound>,
) {
    override fun toString()= buildString {
        appendLine("RelaxedAssertionsJson contest=$contest nseats=$seats")
        assertions.forEach { appendLine(it)}
        bounds.forEach { appendLine(it)}
    }
}

fun RelaxedAssertions.publishJson(): RelaxedAssertionsJson {

    val dasm = assorters.map {
        val da = when {
            it is AboveThreshold -> DAssorter("AT", it.winner())
            it is BelowThreshold -> DAssorter("BT", it.winner())
            it is DHondtAssorter -> DAssorter("DH", it.winner(), it.loser(), it.lastSeatWon, it.firstSeatLost)
            else -> throw RuntimeException("unknown assorter $it")
        }
        DAssertionWithMargin(da, dcontest.difficulty(it), dcontest.marginInVotes(it))
    }

    val bounds = candidates.map {
        val name = dcontest.info().candidateIdToName[it.candId]
        Bound(it.candId, name!!, it.minSeats, it.maxSeats)
    }
    return RelaxedAssertionsJson(dcontest.name, dasm.publishJson(), dcontest.nseats, bounds)
}

fun List<DAssertionWithMargin>.publishJson(): List<DAssertionWithMarginJson> =
    this.map { it.publishJson() }

fun List<DAssertionWithMarginJson>.import(): List<DAssertionWithMargin> =
    this.map { it.import() }


data class DAssertionWithMargin(
    val assertion: DAssorter,
    val difficulty: Double,
    val margin: Int,
)

@Serializable
data class DAssertionWithMarginJson(
    val assertion: DAssorterJson,
    val difficulty: Double,
    val margin: Int,
)

fun DAssertionWithMargin.publishJson() = DAssertionWithMarginJson(
    this.assertion.publishJson(),
    this.difficulty,
    this.margin,
)

fun DAssertionWithMarginJson.import() = DAssertionWithMargin(
    this.assertion.import(),
    this.difficulty,
    this.margin,
)

///////////////////////////////////////

data class DAssorter(
    val type: String,
    val winner: Int,
    val loser: Int? = null,
    val winnerLowestWinner: Int?  = null,
    val loserHighestLoser: Int?  = null,
)

@Serializable
data class DAssorterJson(
    val type: String,
    val party: Int?,
    val winner: Int?,
    val loser: Int?,
    val winnerLowestWinner: Int?,
    val loserHighestLoser: Int?,
)

fun DAssorter.publishJson(): DAssorterJson {
    return if (type == "DH")
        DAssorterJson(
            type = this.type,
            party = null,
            winner = this.winner,
            loser = this.loser!!,
            winnerLowestWinner = this.winnerLowestWinner!!,
            loserHighestLoser = this.loserHighestLoser!!)
    else
        DAssorterJson(
            type = this.type,
            party = this.winner,
            winner = null,
            loser = null,
            winnerLowestWinner = null,
            loserHighestLoser = null)
}


fun DAssorterJson.import(): DAssorter {
    return if (type == "DH")
        DAssorter(
            type = this.type,
            winner = this.winner!!,
            loser = this.loser!!,
            winnerLowestWinner = this.winnerLowestWinner!!,
            loserHighestLoser = this.loserHighestLoser!!
        )
    else
        DAssorter(
            type = this.type,
            winner = this.party!!,
            loser = null,
            winnerLowestWinner = null,
            loserHighestLoser = null
        )
}

////////////////////////////////////////////

val testData = listOf(
    DAssertionWithMargin(DAssorter("AT", 0), 3.4, 4000),
    DAssertionWithMargin(DAssorter("AT", 1), 3.0, 4500),
    DAssertionWithMargin(DAssorter("DH", 0, 1, 2, 2), 5.1, 2100),
    DAssertionWithMargin(DAssorter("DH", 0, 2, 2, 1), 2.2, 9000),
    DAssertionWithMargin(DAssorter("DH", 1, 0, 1, 4), 2.5, 8000),
    DAssertionWithMargin(DAssorter("DH", 1, 2, 1, 1), 2.0, 9500),
    DAssertionWithMargin(DAssorter("DH", 0, 1, 3, 3), 6.0, 1500),
    DAssertionWithMargin(DAssorter("DH", 1, 0, 2, 4), 4.0, 3000),
    DAssertionWithMargin(DAssorter("DH", 0, 2, 3, 2), 2.8, 7000),
    DAssertionWithMargin(DAssorter("DH", 1, 2, 2, 2), 3.1, 6000),
)

object TestOne {

    @JvmStatic
    fun main(args: Array<String>) {
        val jsonList = testData.publishJson()
        val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true} // ; prettyPrint = true }

        val jsonString = buildString {
            appendLine("[")
            jsonList.forEach {
                val bos = ByteArrayOutputStream()
                jsonReader.encodeToStream(it, bos)
                appendLine(" $bos,")
            }
            appendLine("]")
        }

        println()
        println(jsonString)
    }
}

/////////////////////////////////////////////////////////////////////////////////

fun writeDHondtAssertionsJson(dcontest: DHondtContest, assorters: List<AssorterIF>, candidates: List<CandidateSeats>) {
    val relax = RelaxedAssertions(dcontest, assorters, candidates)
    val json = relax.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    val bos = ByteArrayOutputStream()
    jsonReader.encodeToStream(json, bos)
    println(bos)

    /*
    val jsonString = buildString {
        appendLine("[")
        json.forEach {
            val bos = ByteArrayOutputStream()
            jsonReader.encodeToStream(it, bos)
            appendLine(" $bos,")
        }
        appendLine("]")
    }
    println()
    println(jsonString) */
}

fun writeDHondtAssertionsJsonFile(contestRound: ContestRound, builder: CandSeatRangeBuilder, filename: String,
                                  pretty: Boolean = false): RelaxedAssertionsJson {
    val failedAssorters = builder.failureNodes.children.map { it.value }.map { it.failure.assorter }
    val assorters = contestRound.contestUA.clcaAssertions.map { it.assorter }.filter { !failedAssorters.contains(it) }

    val dcontest = contestRound.contestUA.contest as DHondtContest
    val relax = RelaxedAssertions(dcontest, assorters, builder.partyRanges.candidates)

    val json = relax.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = pretty }
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(json, out)
        out.close()
    }
    return json
}

fun readDHondtAssertionsJsonFile(filename: String): Result<RelaxedAssertionsJson, ErrorMessages> {
    val errs = ErrorMessages("readDHondtAssertionsJsonFile '${filename}'")
    val filepath = Path(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<RelaxedAssertionsJson>(inp)
            if (errs.hasErrors()) Err(errs) else Ok(json)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}

fun readDHondtAssertionsJsonUnwrapped(filename: String): RelaxedAssertionsJson? {
    val result = readDHondtAssertionsJsonFile(filename)
    return if (result.isOk) result.unwrap() else null
}

//////////////////////////////////////////////////////////////////////////////

fun writeDHondtAssertionContestsJson(contestRounds: List<ContestRound>, allSeats: AllSeats, filename: String,
                                     pretty: Boolean = false): RelaxedAssertionContestsJson {

    val relaxed = mutableListOf<RelaxedAssertions>()
    contestRounds.forEach { contestRound ->
        val dcontest = contestRound.contestUA.contest as DHondtContest
        val candSeats = allSeats.contestSeats.find { it.contestId == dcontest.id }!!

        val failedAssorters = candSeats.failedAssertions
        val assorters = contestRound.contestUA.clcaAssertions.map { it.assorter }.filter { !failedAssorters.contains(it) }

        //     val dcontest: DHondtContest,
        //    val assorters: List<AssorterIF>,
        //    val candidates: List<CandidateSeats>,
        relaxed.add(RelaxedAssertions(dcontest, assorters, candSeats.candidates))
    }

    val json = relaxed.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = pretty }
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(json, out)
        out.close()
    }
    return json
}

fun readDHondtAssertionContestsJson(filename: String): Result<RelaxedAssertionContestsJson, ErrorMessages> {
    val errs = ErrorMessages("readDHondtAssertionContestsJson '${filename}'")
    val filepath = Path(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<RelaxedAssertionContestsJson>(inp)
            if (errs.hasErrors()) Err(errs) else Ok(json)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}

fun readDHondtAssertionContestsJsonUnwrapped(filename: String): RelaxedAssertionContestsJson? {
    val result = readDHondtAssertionContestsJson(filename)
    return if (result.isOk) result.unwrap() else null
}