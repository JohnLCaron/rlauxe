package org.cryptobiotic.rlauxe.persist.csv


import org.cryptobiotic.rlauxe.oneaudit.BallotPool
import org.cryptobiotic.rlauxe.oneaudit.CardPoolsFromBallotPools
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
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

fun makeCardPoolsFromAuditRecord(auditRecord: AuditRecord): CardPoolsFromBallotPools {
    val publisher = Publisher(auditRecord.location)
    val ballotPools: List<BallotPool> = readBallotPoolCsvFile(publisher.ballotPoolsFile())
    return CardPoolsFromBallotPools(ballotPools, auditRecord.contests.associate { it.id to it.contest.info() })
}
