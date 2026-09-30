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
import org.cryptobiotic.rlauxe.dhondt.DhondtAssorter
import org.cryptobiotic.rlauxe.dhondt.DhondtContest
import org.cryptobiotic.rlauxe.dhondt.PartyRange
import org.cryptobiotic.rlauxe.dhondt.RelaxedAssertionsIF
import org.cryptobiotic.rlauxe.dhondt.makeRelaxedAssertions
import org.cryptobiotic.rlauxe.persist.SampleLimit
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

private fun List<RelaxedAssertionsIF>.publishJson() = RelaxedAssertionContestsJson(
        this.map { publishRAJson(it) }
    )

@Serializable
data class Bound(
    val party: Int,
    val name: String,
    val minSeats: Int,
    val maxSeats: Int,
)

data class RelaxedAssertionData(
    val dcontest: DhondtContest,
    val assorters: List<AssorterIF>,
    val partyRanges: List<PartyRange>,
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

fun RelaxedAssertionData.publishJson(): RelaxedAssertionsJson {

    val dasm = this.assorters.map {
        val da = when (it) {
            is AboveThreshold -> DAssorter("AT", it.winner())
            is BelowThreshold -> DAssorter("BT", it.winner())
            is DhondtAssorter -> DAssorter("DH", it.winner(), it.loser(), it.winnerDivisor, it.loserDivisor)
            else -> throw RuntimeException("unknown assorter $it")
        }
        DAssertionWithMargin(da, this.dcontest.difficulty(it), this.dcontest.marginInVotes(it))
    }

    val bounds = this.partyRanges.map {
        val name = this.dcontest.info().candidateIdToName[it.partyId]
        Bound(it.partyId, name!!, it.minSeats, it.maxSeats)
    }
    return RelaxedAssertionsJson(this.dcontest.name, dasm.publishJson(), this.dcontest.nseats, bounds)
}

fun publishRAJson(ra: RelaxedAssertionsIF): RelaxedAssertionsJson {
    val dcontest = ra.altContest()

    val dasm = ra.assortersForProof().map {
        val da = when (it) {
            is AboveThreshold -> DAssorter("AT", it.winner())
            is BelowThreshold -> DAssorter("BT", it.winner())
            is DhondtAssorter -> DAssorter("DH", it.winner(), it.loser(), it.winnerDivisor, it.loserDivisor)
            else -> throw RuntimeException("unknown assorter $it")
        }
        DAssertionWithMargin(da, dcontest.difficulty(it), dcontest.marginInVotes(it))
    }

    val bounds = ra.contestRanges().partyRanges.values.map { party ->
        val name = dcontest.info().candidateIdToName[party.partyId]
        Bound(party.partyId, name!!, party.minSeats, party.maxSeats)
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
) {
    init {
        if (difficulty.isInfinite())
            println("difficulty - $difficulty")
    }
}

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

// still used ??
fun writeDHondtAssertionsJson(dcontest: DhondtContest, assorters: List<AssorterIF>, candidates: List<PartyRange>) {
    val relaxData = RelaxedAssertionData(dcontest, assorters, candidates)
    val json = relaxData.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    val bos = ByteArrayOutputStream()
    jsonReader.encodeToStream(json, bos)
    println(bos)
}

fun writeOneContestToJsonFile(contestRound: ContestRound, relax: RelaxedAssertionsIF, filename: String,
                              pretty: Boolean = false): RelaxedAssertionsJson {
    val failedAssorters = relax.failures().map { it.assorter }
    val assorters = contestRound.contestUA.clcaAssertions.map { it.assorter }.filter { !failedAssorters.contains(it) }

    val dcontest = contestRound.contestUA.contest as DhondtContest
    val relaxData = RelaxedAssertionData(dcontest, assorters, relax.contestRanges().partyRanges.values.toList())

    val json = relaxData.publishJson()
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

fun writeAllContestsToJsonFile(contestRounds: List<ContestRound>, filename: String, alpha: Double, pretty: Boolean = false,
                               mvrLimit: Map<Int, SampleLimit>, useV: Boolean = false): RelaxedAssertionContestsJson {

    val relaxed = mutableListOf<RelaxedAssertionsIF>()
    contestRounds.forEach { contestRound ->
        relaxed.add(makeRelaxedAssertions(contestRound, alpha, mvrLimit[contestRound.id]?.limit, useV))
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