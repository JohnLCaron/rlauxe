package org.cryptobiotic.rlauxe.persist.csv

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPool
import org.cryptobiotic.rlauxe.util.ContestTabulation
import java.io.*

private val logger = KotlinLogging.logger("CardPoolCsv")

// data class OneAuditPool(
//    override val poolName: String,
//    override val poolId: Int,
//    val hasSingleCardStyle: Boolean,
//    val infos: Map<Int, ContestInfo>,
//    val contestTabs: Map<Int, ContestTabulation>,  // contestId -> ContestTabulation
//    val totalCards: Int,
//): OneAuditPoolIF {

val CardPoolHeader = "poolId, poolName, hasSingleCardStyle, totalCards, contestId, voteForN, cands, ncards, novote, undervotes, overvotes, nphantoms, isIrv, votes:count ... \n"

fun writeCardPoolCsv(pool: OneAuditPool) = buildString {
    append("${pool.poolId}, ${pool.poolName}, ${pool.hasSingleCardStyle}, ${pool.totalCards}, ")
    pool.contestTabs.values.forEachIndexed { index, contestTab ->
        if (index > 0) { append("${pool.poolId},,,, ") }
        append(writeContestTabulationCsv(contestTab))
    }
}

fun writeCardPoolCsvFile(pool: List<OneAuditPool>, outputFilename: String) {
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    writer.write(CardPoolHeader)
    pool.forEach {
        writer.write(writeCardPoolCsv(it))
    }
    writer.close()
}

fun readCardPoolCsv(line: String, infos: Map<Int, ContestInfo>): OneAuditPoolBuilder {
    val tokens = line.split(",")
    val ttokens = tokens.map { it.trim() }

    // var popId : String? = null
    // var pcontests = intArrayOf()

        var idx = 0
        val poolId = ttokens[idx++].toInt()
        val poolName = ttokens[idx++]
        val hasSingleCardStyle = ttokens[idx++] == "true"

    try {
        val totalCards = ttokens[idx].toInt()
        return OneAuditPoolBuilder(poolName, poolId, hasSingleCardStyle, infos, totalCards)
    } catch (e:Throwable) {
        println("whu")
        throw e
    }
}

fun readCardPoolContinuation(line: String, current: OneAuditPoolBuilder): Boolean {
    val tokens = line.split(",")
    val ttokens = tokens.map { it.trim() }

    var idx = 0
    val poolId = ttokens[idx++].toInt()
    if (poolId != current.poolId) return false
    idx += 3

    val tab = readContestTabulationCsv(ttokens.subList(idx, ttokens.size))
    current.contestTabs[tab.contestId] = tab

    return true
}

fun readCardPoolCsvFile(filename: String, infos: Map<Int, ContestInfo>): List<OneAuditPool> {
    val reader: BufferedReader = File(filename).bufferedReader()
    reader.readLine() // get rid of header line

    val pools = mutableListOf<OneAuditPool>()
    var line = reader.readLine()
    var currentPool: OneAuditPoolBuilder? = null

    outerLoop@
    while (true) {
        currentPool = readCardPoolCsv(line, infos)
        // read more contestTabs for current pool
        while (readCardPoolContinuation(line, currentPool)) {
            line = reader.readLine() ?: break@outerLoop
        }
        pools.add(currentPool.build())
    }
    pools.add(currentPool.build())

    reader.close()
    return pools
}

class OneAuditPoolBuilder(
    val poolName: String,
    val poolId: Int,
    val hasSingleCardStyle: Boolean,
    val infos: Map<Int, ContestInfo>,
    val totalCards: Int,
) {
    val contestTabs = mutableMapOf<Int, ContestTabulation>()

    fun build() = OneAuditPool(poolName, poolId, hasSingleCardStyle, infos, contestTabs, totalCards)
}

