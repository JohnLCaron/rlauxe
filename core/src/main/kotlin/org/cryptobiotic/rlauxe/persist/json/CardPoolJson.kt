package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.Int

@Serializable
data class CardPoolsJson(
    val cardsPools: List<CardPoolJson>,
)

fun List<CardPoolIF>.publishJson() = CardPoolsJson(
    this.map { it.publishJson() },
)

fun CardPoolsJson.import(infos: Map<Int, ContestInfo>): List<CardPoolIF> {
    return cardsPools.map { it.import(infos) }
}

@Serializable
data class CardPoolJson(
    val type: String,
    val cardPoolWithBallotStyleJson: CardPoolWithBallotStyleJson?,
    val cardPoolFromCvrs: CardPoolFromCvrsJson?,
)

fun CardPoolIF.publishJson(): CardPoolJson {
    return if (this is CardPoolWithBallotStyle)
        CardPoolJson(
            "CardPoolWithBallotStyle",
            this.publishJson(),
            null,
        ) else if (this is CardPoolFromCvrs)
        CardPoolJson(
            "CardPoolFromCvrs",
            null,
            this.publishJson(),
        ) else throw IllegalArgumentException("serializing ${this.javaClass.getName()} is not supported")
}

fun CardPoolJson.import(infos: Map<Int, ContestInfo>): CardPoolIF {
    return if (this.type == "CardPoolWithBallotStyle")
        this.cardPoolWithBallotStyleJson!!.import(infos)
    else if (this.type == "CardPoolFromCvrs")
        this.cardPoolFromCvrs!!.import(infos)
    else throw IllegalArgumentException("serializing ${this.type} is not supported")
}

// class CardPoolWithBallotStyle(
//    override val poolName: String,
//    override val poolId: Int,
//    val voteTotals: Map<Int, Map<Int, Int>>, // contestId -> candidateId -> nvotes
//) : CardPoolIF
//{
//    val minCardsNeeded = mutableMapOf<Int, Int>() // contestId -> minCardsNeeded
//    val maxMinCardsNeeded: Int
//    private var adjustCards = 0
@Serializable
class CardPoolWithBallotStyleJson(
    val poolName: String,
    val poolId: Int,
    val voteTotals: Map<Int, ContestTabulationJson>, // contestId -> candidateId -> nvotes
    val adjustCards: Int
)

fun CardPoolWithBallotStyle.publishJson() = CardPoolWithBallotStyleJson(
    this.poolName,
    this.poolId,
    this.voteTotals.mapValues { it.value.publishJson() },
    this.adjustCards,
)

fun CardPoolWithBallotStyleJson.import(infos: Map<Int, ContestInfo>): CardPoolWithBallotStyle {
    val cardPool = CardPoolWithBallotStyle(
        this.poolName,
        this.poolId,
        this.voteTotals.mapValues { it.value.import(infos[it.key]!!) },
        infos
    )
    cardPool.adjustCards = this.adjustCards
    return cardPool
}

// open class CardPoolFromCvrs(
//    override val poolName: String,
//    override val poolId: Int,
//    val infos: Map<Int, ContestInfo>) : CardPoolIF
//{
//    val contestTabs = mutableMapOf<Int, ContestTabulation>()  // contestId -> ContestTabulation
//    var totalCards = 0

@Serializable
class CardPoolFromCvrsJson(
    val poolName: String,
    val poolId: Int,
    val contestTabs: Map<Int, ContestTabulationJson>, // contestId -> candidateId -> nvotes //
    val totalCards: Int // ??
)

fun CardPoolFromCvrs.publishJson() = CardPoolFromCvrsJson(
    this.poolName,
    this.poolId,
    this.contestTabs.mapValues { it.value.publishJson() },
    this.totalCards,
)

fun CardPoolFromCvrsJson.import(infos: Map<Int, ContestInfo>): CardPoolFromCvrs {
    val cardPool = CardPoolFromCvrs(
        this.poolName,
        this.poolId,
        infos,
    )

    this.contestTabs.forEach { (key, value) ->
        val info = infos[key]
        if (info != null)
            cardPool.contestTabs[key] = value.import(info)
        else
            cardPool.contestTabs[key] = value.import(ContestInfo(key))

    }
    cardPool.totalCards = this.totalCards
    return cardPool
}

// class ContestTabulation(val info: ContestInfo): RegVotes {
//    val isIrv = info.isIrv
//    val voteForN = if (isIrv) 1 else info.voteForN
//
//    override val votes = mutableMapOf<Int, Int>() // cand -> votes
//    val irvVotes = VoteConsolidator() // candidate indexes
//    val notfound = mutableMapOf<Int, Int>() // candidate -> nvotes; track candidates on the cvr but not in the contestInfo, for debugging
//    val candidateIdToIndex: Map<Int, Int>
//
//    var ncards = 0
//    var novote = 0  // how many cards had no vote for this contest?
//    var undervotes = 0  // how many undervotes = voteForN - nvotes
//    var overvotes = 0

@Serializable
class ContestTabulationJson(
    val votes: Map<Int, Int> ,// cand -> votes
    // val irvVotes: VoteConsolidatorJson, // TODO needed?

    val ncards: Int,
    val novote: Int,
    val undervotes: Int,
    val overvotes: Int,
)

fun ContestTabulation.publishJson() = ContestTabulationJson(
    this.votes,
    // this.irvVotes,
    this.ncards,
    this.novote,
    this.undervotes,
    this.overvotes
)

fun ContestTabulationJson.import(info: ContestInfo): ContestTabulation {
    val cardPool = ContestTabulation(info)
    cardPool.votes.putAll(this.votes)
    cardPool.ncards = this.ncards
    cardPool.novote = this.novote
    cardPool.undervotes = this.undervotes
    cardPool.overvotes = this.overvotes
    return cardPool
}

/////////////////////////////////////////////////////////////////////////////////////////////

fun writeCardPoolsJsonFile(cardPools: List<CardPoolIF>, filename: String) {
    val json = cardPools.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(json, out)
        out.close()
    }
}

fun readCardPoolsJsonFile(filename: String, infos: Map<Int, ContestInfo>): Result<List<CardPoolIF>, ErrorMessages> {
    val errs = ErrorMessages("readCardPoolsJsonFile '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<CardPoolsJson>(inp)
            val contests = json.import(infos)
            if (errs.hasErrors()) Err(errs) else Ok(contests)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}
