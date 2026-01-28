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
import org.cryptobiotic.rlauxe.util.ContestVotes
import org.cryptobiotic.rlauxe.util.ContestVotesIF
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.Int

/*
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
} */

@Serializable
data class PopulationsJson(
    val populations: List<PopulationJson>,
)

fun List<PopulationIF>.publishJson() = PopulationsJson(
    this.map { it.publishJson() },
)

fun PopulationsJson.import(): List<Population> {
    return this.populations.map { it.import() }
}

@Serializable
class PopulationJson(
    val name: String,
    val id: Int,
    val ncards: Int,
    val possibleContests: IntArray,
    val hasSingleCardStyle: Boolean
)

fun PopulationIF.publishJson() = PopulationJson(
    this.name(),
    this.id(),
    this.ncards(),
    this.contests(),
    this.hasSingleCardStyle()
)

fun PopulationJson.import(): Population {
    val cardPool = Population(
        this.name,
        this.id,
        this.possibleContests,
        this.hasSingleCardStyle,
    )
    cardPool.ncards = this.ncards
    return cardPool
}

// data class OneAuditPool(override val poolName: String, override val poolId: Int, val exactContests: Boolean,
//                        val ncards: Int, val regVotes: Map<Int, RegVotesIF

/*
@Serializable
class OneAuditPoolJson(
    val poolName: String,
    val poolId: Int,
    val hasSingleCardStyle: Boolean,
    val ncards: Int,
    val regVotes: Map<Int, ContestVotesJson>,
)

fun OneAuditPoolIF.publishJson() = OneAuditPoolJson(
    this.poolName,
    this.poolId,
    this.hasSingleCardStyle(),
    this.ncards(),
    this.regVotes().mapValues { it.value.publishJson() },
)

// note that we publish OneAuditPoolIF, then turn that into OneAuditPool, losing original information
fun OneAuditPoolJson.import() = OneAuditPool(
        this.poolName,
        this.poolId,
        this.hasSingleCardStyle,
        this.ncards,
        this.regVotes.mapValues { it.value.import() },
    )

// data class RegVotes(override val votes: Map<Int, Int>, val ncards: Int, val undervotes: Int): RegVotesIF {
@Serializable
class ContestVotesJson(
    val contestId: Int,
    val voteForN: Int,
    val votes: Map<Int, Int>, // cand -> votes
    val ncards: Int,
    val undervotes: Int,
)

fun ContestVotesIF.publishJson() = ContestVotesJson(
    this.contestId,
    this.voteForN,
    this.votes,
    this.ncards(),
    this.undervotes(),
)

fun ContestVotesJson.import() = ContestVotes(
    this.contestId,
    this.voteForN,
    this.votes,
    this.ncards,
    this.undervotes,
) */

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

