package org.cryptobiotic.rlauxe.persist.csv

import org.cryptobiotic.rlauxe.audit.RegVotesImpl
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.oneaudit.AssortAvg
import org.cryptobiotic.rlauxe.oneaudit.BallotPool
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.unpooled
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.trunc
import java.io.*
import java.nio.file.Files
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.io.path.Path

// data class BallotPool(
//    val name: String,
//    val poolId: Int,
//    val contestId :Int,
//    val ncards: Int,          // ncards for this contest in this pool; TODO hasStyles = false?
//    val votes: Map<Int, Int>, // candid -> nvotes, for plurality. TODO add undervotes ??
//)

val BallotPoolCsvHeader = "PoolName, PoolId, ContestId, ncards, candidate:nvotes, ...\n"

fun writeBallotPoolCSV(pool: BallotPool) = buildString {
    append("${pool.name}, ${pool.poolId}, ${pool.contestId}, ${pool.ncards}, ")
    pool.votes.forEach { (cand, vote) -> append("$cand: $vote, ") }
    appendLine()
}

fun readBallotPoolCSV(line: String): BallotPool {
    val tokens = line.split(",")
    val ttokens = tokens.map { it.trim() }

    var idx = 0
    val name = ttokens[idx++]
    val id = ttokens[idx++].toInt()
    val contest = ttokens[idx++].toInt()
    val ncards = ttokens[idx++].toInt()
    val votes = mutableMapOf<Int, Int>()
    while (idx < ttokens.size) {
        val vtokens = ttokens[idx].split(":")
        val candStr = vtokens[0].trim()
        if (candStr.isEmpty()) break // trailing comma
        val cand = candStr.toInt()
        val voteStr = vtokens[1].trim()
        val vote = voteStr.toInt()
        votes[cand] = vote
        idx++
    }
    return BallotPool(name, id, contest, ncards, votes)
}

///////////////////////////////////////////////////////////////

fun writeBallotPoolCsvFile(pools: List<BallotPool>, filename: String) {
    val writer: OutputStreamWriter = FileOutputStream(filename).writer()
    writer.write(BallotPoolCsvHeader)
    pools.forEach {
        writer.write(writeBallotPoolCSV(it))
    }
    writer.close()
}

fun readBallotPoolCsvFile(filename: String): List<BallotPool> {
    if (!Files.exists(Path(filename))) return emptyList()

    val reader: BufferedReader = File(filename).bufferedReader()
    val header = reader.readLine() // get rid of header line

    val pools = mutableListOf<BallotPool>()
    while (true) {
        val line = reader.readLine() ?: break
        pools.add(readBallotPoolCSV(line))
    }
    reader.close()
    return pools
}

fun List<BallotPool>.poolNameToId(): Map<String, Int> {
    val pools = mutableMapOf<String, Int>()
    this.forEach { pools[it.name] = it.poolId }
    return pools
}

/////////////////////////////////////////////////

class CardPoolsFromBallotPools(
    val ballotPools: List<BallotPool>,
    val infos: Map<Int, ContestInfo>) {

    val cardPoolMap: Map<Int, CardPoolFromBallotPools> // poolId -> pool

    init {
        val reaggs = mutableMapOf<Int, MutableList<BallotPool>>()
        ballotPools.forEach { pool ->
            val reagg = reaggs.getOrPut(pool.poolId) { mutableListOf() }
            reagg.add(pool)
        }
        cardPoolMap = reaggs.mapValues { (poolId, ballotPools) ->
            val voteTotals = ballotPools.associate { Pair(it.contestId, it.votes) }
            CardPoolFromBallotPools(ballotPools[0].name, poolId, voteTotals, ballotPools[0].ncards)
        }
    }

    fun showPoolVotes(width: Int = 4) = buildString {
        val contestIds = infos.values.map { it.id }.sorted()
        appendLine("votes, undervotes")
        append("${trunc("poolName", 9)}:")
        contestIds.forEach { append("${nfn(it, width)}|") }
        appendLine()

        cardPoolMap.values.filter { it.poolName != unpooled}.forEach { cardpool ->
            appendLine(cardpool.showVotes(contestIds, width))
        }

        // TODO add sums
    }

    inner class CardPoolFromBallotPools(
        val poolName: String,
        override val poolId: Int,
        val voteTotals: Map<Int, Map<Int, Int>>,
        val ncards: Int,
    ) : CardPoolIF {
        // TODO fill this in from the margins?? or get rid of ??
        override val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average

        override fun regVotes() = voteTotals.mapValues { RegVotesImpl(it.value, ncards) }
        // override fun ncards() = ncards
        override fun contains(contestId: Int) = voteTotals.containsKey(contestId)

        fun showVotes(contestIds: Collection<Int>, width: Int = 4) = buildString {
            append("${trunc(poolName, 9)}:")
            contestIds.forEach { id ->
                val contestVote = voteTotals[id]
                if (contestVote == null)
                    append("    |")
                else {
                    val sum = contestVote.map { it.value }.sum()
                    append("${nfn(sum, width)}|")
                }
            }
            appendLine()

            val undervotes = undervotes()
            append("${trunc("", 9)}:")
            contestIds.forEach { id ->
                val contestVote = voteTotals[id]
                if (contestVote == null)
                    append("    |")
                else {
                    val undervote = undervotes[id]!!
                    append("${nfn(undervote, width)}|")
                }
            }
            appendLine()
        }

        fun undervotes(): Map<Int, Int> {  // contest -> undervote
            val undervote = voteTotals.map { (id, cands) ->
                val sum = cands.map { it.value }.sum()
                val info = infos[id]!!
                Pair(id, ncards * info.voteForN - sum)
            }
            return undervote.toMap().toSortedMap()
        }
    }
}

fun makeCardPoolsFromBallotPools(filename: String, infos: Map<Int, ContestInfo>): CardPoolsFromBallotPools {
    val ballotPools: List<BallotPool> = readBallotPoolCsvFile(filename)
    return CardPoolsFromBallotPools(ballotPools, infos)
}

fun makeCardPoolsFromAuditRecord(auditRecord: AuditRecord): CardPoolsFromBallotPools {
    val publisher = Publisher(auditRecord.location)
    val ballotPools: List<BallotPool> = readBallotPoolCsvFile(publisher.ballotPoolsFile())
    return CardPoolsFromBallotPools(ballotPools, auditRecord.contests.associate { it.id to it.contest.info() })
}
