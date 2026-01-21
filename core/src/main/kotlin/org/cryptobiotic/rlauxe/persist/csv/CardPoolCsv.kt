package org.cryptobiotic.rlauxe.persist.csv

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.util.CloseableIterator
import java.io.*

private val logger = KotlinLogging.logger("CardPoolCsv")

// data class OneAuditPoolFromCvrs(
//    override val poolName: String,
//    override val poolId: Int,
//    val hasSingleCardStyle: Boolean,
//    val infos: Map<Int, ContestInfo>,
//): OneAuditPoolIF {
//
//    val contestTabs = mutableMapOf<Int, ContestTabulation>()  // contestId -> ContestTabulation
//    var totalCards = 0
//
//    // a convenient place to keep this, calculated in addOAClcaAssorters()
//    val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average

val CardPoolHeader = "poolId, poolName, hasSingleCardStyle, totalCards, contestId, voteForN, cands, ncards, novote, undervotes, overvotes, nphantoms, isIrv, votes:count ... \n"

fun writeCardPoolCsv(pool: OneAuditPoolFromCvrs) = buildString {
    append("${pool.poolId}, ${pool.poolName}, ${pool.hasSingleCardStyle}, ${pool.totalCards}, ")
    pool.contestTabs.values.forEachIndexed { index, contestTab ->
        if (index > 0) { append("${pool.poolId},,,, ") }
        append(writeContestTabulationCsv(contestTab))
    }
}

fun writeCardPoolCsvFile(pool: List<OneAuditPoolFromCvrs>, outputFilename: String) {
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    writer.write(CardPoolHeader)
    pool.forEach {
        writer.write(writeCardPoolCsv(it))
    }
    writer.close()
}

fun readCardPoolCsv(line: String, infos: Map<Int, ContestInfo>): OneAuditPoolFromCvrs {
    val tokens = line.split(",")
    val ttokens = tokens.map { it.trim() }

    // var popId : String? = null
    // var pcontests = intArrayOf()

    var idx = 0
    val poolId = ttokens[idx++].toInt()
    val poolName = ttokens[idx++]
    val hasSingleCardStyle = ttokens[idx++] == "true"
    val totalCards = ttokens[idx++].toInt()

    val pool = OneAuditPoolFromCvrs(poolName, poolId, hasSingleCardStyle, infos)
    pool.totalCards = totalCards

    return pool
}

fun readCardPoolContinuation(line: String, current: OneAuditPoolFromCvrs): Boolean {
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

fun readCardPoolCsvFile(filename: String, infos: Map<Int, ContestInfo>): List<OneAuditPoolFromCvrs> {
    val reader: BufferedReader = File(filename).bufferedReader()
    reader.readLine() // get rid of header line

    val pools = mutableListOf<OneAuditPoolFromCvrs>()
    var line = reader.readLine()

    outerLoop@
    while (true) {
        val currentPool = readCardPoolCsv(line, infos)
        pools.add(currentPool)

        // read more contestTabs for current pool
        while (readCardPoolContinuation(line, currentPool)) {
            line = reader.readLine() ?: break@outerLoop
        }
    }

    reader.close()
    return pools
}

