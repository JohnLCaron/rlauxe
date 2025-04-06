package org.cryptobiotic.rlauxe.persist.csv

import org.cryptobiotic.rlauxe.oneaudit.BallotPool
import java.io.*


// data class Cvr(
//    val id: String,
//    val votes: Map<Int, IntArray>, // contest -> list of candidates voted for; for IRV, ranked first to last
//    val phantom: Boolean = false,
//)
// CvrUnderAudit (val cvr: Cvr, val index: Int, val sampleNum: Long)

// data class BallotPool(val name: String, val id: Int, val contest:Int, val ncards: Int, val votes: Map<Int, Int>) {

val BallotPoolCsvHeader = "Pool, Id, Contest, ncards, candidate:nvotes, ...\n"

fun writeBallotPoolCSV(pool: BallotPool) = buildString {
    append("${pool.name}, ${pool.id}, ${pool.contest}, ${pool.ncards}, ")
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