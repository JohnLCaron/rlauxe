package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.cryptobiotic.rlauxe.audit.Population
import org.cryptobiotic.rlauxe.audit.PopulationIF
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.Int

@Serializable
data class PopulationsJson(
    val populations: List<PopulationIFJson>,
)

fun List<PopulationIF>.publishJson() = PopulationsJson(
    this.map { it.publishJson() },
)

fun PopulationsJson.import(): List<PopulationIF> {
    return this.populations.map { it.import() }
}

@Serializable
data class PopulationIFJson(
    val type: String,
    val oneauditPool: OneAuditPoolJson?,
    val population: PopulationJson?,
)

fun PopulationIF.publishJson(): PopulationIFJson {
    return if (this is OneAuditPoolIF)
        PopulationIFJson(
            "OneAuditPoolIF",
            this.toOneAuditPool().publishJson(),
            null)
        else if (this is Population)
            PopulationIFJson(
            "Population",
            null,
            this.publishJson())
        else throw IllegalArgumentException("serializing ${this.javaClass.getName()} is not supported")
}

fun PopulationIFJson.import(): PopulationIF {
    return if (this.type == "OneAuditPoolIF")
        this.oneauditPool!!.import()
    else if (this.type == "Population")
        this.population!!.import()
    else throw IllegalArgumentException("serializing ${this.type} is not supported")
}

// data class Population(
//    val name: String,
//    val id: Int,
//    val possibleContests: IntArray, // the list of possible contests.
//    val exactContests: Boolean,     // aka hasStyle: if all cards have exactly the contests in possibleContests
//)
@Serializable
class PopulationJson(
    val name: String,
    val id: Int,
    val ncards: Int,
    val possibleContests: IntArray,
    val exactContests: Boolean
)

fun Population.publishJson() = PopulationJson(
    this.name,
    this.id,
    this.ncards,
    this.possibleContests,
    this.hasSingleCardStyle
)

fun PopulationJson.import(): Population {
    val cardPool = Population(
        this.name,
        this.id,
        this.possibleContests,
        this.exactContests,
    )
    cardPool.ncards = this.ncards
    return cardPool
}

// data class OneAuditPoolWithBallotStyle(
//    override val poolName: String,
//    override val poolId: Int,
//    val population: PopulationIF,
//    val voteTotals: Map<Int, ContestTabulation>, // contestId -> candidateId -> nvotes; must include contests with no votes
//    val infos: Map<Int, ContestInfo>, // all infos
// }
//    val minCardsNeeded = mutableMapOf<Int, Int>() // contestId -> minCardsNeeded
//    val maxMinCardsNeeded: Int
/*    private var adjustCards = 0
@Serializable
class OneAuditPoolWithBallotStyleJson(
    val poolName: String,
    val poolId: Int,
    val exactContests: Boolean,
    val voteTotals: Map<Int, ContestTabulationJson>, // contestId -> candidateId -> nvotes
    val adjustCards: Int
)

fun OneAuditPoolWithBallotStyle.publishJson() = OneAuditPoolWithBallotStyleJson(
    this.poolName,
    this.poolId,
    this.exactContests,
    this.voteTotals.mapValues { it.value.publishJson() },
    this.adjustCards,
)

fun OneAuditPoolWithBallotStyleJson.import(infos: Map<Int, ContestInfo>): OneAuditPoolWithBallotStyle {
    val cardPool = OneAuditPoolWithBallotStyle(
        this.poolName,
        this.poolId,
        this.exactContests,
        this.voteTotals.mapValues { it.value.import(infos[it.key]!!) },
        infos
    )
    cardPool.adjustCards = this.adjustCards
    return cardPool
} */

// data class OneAuditPool(override val poolName: String, override val poolId: Int, val exactContests: Boolean,
//                        val ncards: Int, val regVotes: Map<Int, RegVotesIF

@Serializable
class OneAuditPoolJson(
    val poolName: String,
    val poolId: Int,
    val exactContests: Boolean,
    val ncards: Int,
    val regVotes: Map<Int, RegVotesJson>,
)

fun OneAuditPool.publishJson() = OneAuditPoolJson(
    this.poolName,
    this.poolId,
    this.hasSingleCardStyle,
    this.ncards,
    this.regVotes().mapValues { it.value.publishJson() },
)

fun OneAuditPoolJson.import() = OneAuditPool(
        this.poolName,
        this.poolId,
        this.exactContests,
        this.ncards,
        this.regVotes.mapValues { it.value.import() },
    )

/////////////////////////////////////////////////////////////////////////////////////////////

@OptIn(ExperimentalSerializationApi::class)
fun writePopulationsJsonFile(populations: List<PopulationIF>, filename: String) {
    val json = populations.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(json, out)
        out.close()
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun readPopulationsJsonFile(filename: String): Result<List<PopulationIF>, ErrorMessages> {
    val errs = ErrorMessages("readPopulationsJsonFile '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<PopulationsJson>(inp)
            val contests = json.import()
            if (errs.hasErrors()) Err(errs) else Ok(contests)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}

fun readPopulationsJsonFileUnwrapped(filename: String): List<PopulationIF> {
    return readPopulationsJsonFile(filename).unwrap()
}

