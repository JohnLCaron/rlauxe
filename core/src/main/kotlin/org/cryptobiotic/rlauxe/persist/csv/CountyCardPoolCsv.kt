package org.cryptobiotic.rlauxe.persist.csv

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.CountyPools
import org.cryptobiotic.rlauxe.audit.CountyPoolsIF
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.util.ContestTabulation
import java.io.*

private val logger = KotlinLogging.logger("CountyCardPoolCsv")

// // CountyPool: pool with multiple CardStyles
//data class CountyPools (
//    val countyName: String,
//    val countyPoolId: Int,
//    val contestTabs: List<ContestTabulation>,  // contestId -> ContestTabulation
//    val totalCards: Int,
//    val cardStyles: List<StyleIF>,
//    // val cardStylesCount: List<Int>, // or CardStyleWithNCards ??
//)

val CountyCardPoolHeader = "countyPoolId, countyName, totalCards, cardStyles, contestId, voteForN, cands, ncards, novote, undervotes, overvotes, nphantoms, isIrv, votes:count ... \n"

fun writeCountyCardPoolCsv(pool: CountyPoolsIF) = buildString {
    val styleIds = pool.styles.map{ it.id() }.joinToString(" ")
    append("${pool.countyPoolId}, ${pool.countyName}, ${pool.cardCount}, $styleIds, ")
    pool.contestTabs.forEachIndexed { index, contestTab ->
        if (index > 0) { append("${pool.countyPoolId},,,, ") }
        append(writeContestTabulationCsv(contestTab))
    }
}

fun writeCountyCardPoolCsvFile(pools: List<CountyPoolsIF>, outputFilename: String) {
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    writer.write(CountyCardPoolHeader)
    pools.forEach {
        writer.write(writeCountyCardPoolCsv(it))
    }
    writer.close()
}

fun readCountyCardPoolCsv(line: String): CountyPoolBuilder {
    val tokens = line.split(",")
    val ttokens = tokens.map { it.trim() }

    // var popId : String? = null
    // var pcontests = intArrayOf()

    var idx = 0
    val poolId = ttokens[idx++].toInt()
    val poolName = ttokens[idx++]
    val totalCards = ttokens[idx++].toInt()
    val cardStyles = ttokens[idx++]

    try {
        return CountyPoolBuilder(poolName, poolId, totalCards, cardStyles)
    } catch (e:Throwable) {
        println("whu")
        throw e
    }
}

fun readCountyCardPoolContinuation(line: String, current: CountyPoolBuilder): Boolean {
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

fun readCountyPoolsCsvFile(filename: String, styles: List<StyleIF>): List<CountyPools> {
    val styleMap = styles.associateBy { it.id() }
    val reader: BufferedReader = File(filename).bufferedReader()
    reader.readLine() // get rid of header line

    val pools = mutableListOf<CountyPools>()
    var line = reader.readLine()
    var currentBuilder: CountyPoolBuilder?

    outerLoop@
    while (true) {
        currentBuilder = readCountyCardPoolCsv(line)
        // read more contestTabs for current pool
        while (readCountyCardPoolContinuation(line, currentBuilder)) {
            line = reader.readLine() ?: break@outerLoop
        }
        pools.add(currentBuilder.build(styleMap))
    }
    pools.add(currentBuilder.build(styleMap))

    reader.close()
    return pools
}

class CountyPoolBuilder(
    val poolName: String,
    val poolId: Int,
    val totalCards: Int,
    val cardStyless: String
) {
    val contestTabs = mutableMapOf<Int, ContestTabulation>()

    // data class CountyPools (
    //    val countyName: String,
    //    val countyPoolId: Int,
    //    val contestTabs: List<ContestTabulation>,  // contestId -> ContestTabulation
    //    val totalCards: Int,
    //    val cardStyles: List<StyleIF>,
    //    // val cardStylesCount: List<Int>, // or CardStyleWithNCards ??
    //)

    fun build(styleMap: Map<Int, StyleIF>): CountyPools {
        val cardStyleIds =
            if (cardStyless.isEmpty()) emptyList() else cardStyless.split(" ").map { it.trim().toInt() }
        val cardStyles = cardStyleIds.map { styleMap[it]!! }
        return CountyPools(poolName, poolId, contestTabs.values.toList(), totalCards, cardStyles, )
    }
}


