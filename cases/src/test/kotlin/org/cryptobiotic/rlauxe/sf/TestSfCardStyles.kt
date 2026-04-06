package org.cryptobiotic.rlauxe.sf

import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.audit.CardStyleIF
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.dominion.CvrExport
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvFile
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvIterator
import org.cryptobiotic.rlauxe.sf.TestSfCardStyles.StylesCount
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.text.buildString
import kotlin.use

class TestSfCardStyles {
    val sfDir = "$testdataDir/cases/sf2024"
    val cvrExportCsv = "$sfDir/$cvrExportCsvFile"
    val ballotTypesContestManifest: BallotTypesContestManifest =
        readBallotTypeContestManifestUnwrapped("src/test/data/SF2024/manifests/BallotTypeContestManifest.json")!!

    @Test
    fun showPoolCardStyles() {
        val poolMap: MutableMap<String, PoolCardStyles> = mutableMapOf()
        var cardCount = 0
        cvrExportCsvIterator(cvrExportCsv).use { cvrIter ->
            while (cvrIter.hasNext()) {
                cardCount++
                val cvrExport: CvrExport = cvrIter.next()
                if (cvrExport.group == 1) { // only want the pools
                    val poolName = cvrExport.poolKey()
                    val poolStyles = poolMap.getOrPut(poolName) { PoolCardStyles(poolName) }
                    poolStyles.countInPool++

                    val contests: Set<Int> = cvrExport.votes.keys // the card style = list of contests
                    val styleInPool = poolStyles.styles.getOrPut(contests.hashCode()) { StylesCount(contests, 0) }
                    styleInPool.countHasStyle++
                    poolStyles.precincts.add(cvrExport.precinctPortionId)
                }
            }
        }
        println("count cards = ${cardCount}\n")

        var allOverlap = 0
        var allSave = 0
        var poolCount = 0
        poolMap.toSortedMap().forEach { (_, pool) ->
            poolCount++
            if (pool.name.startsWith("24-")) {
                val allContests = mutableSetOf<Int>()
                println("Pool $poolCount ${pool.name}  countInPool ${pool.countInPool} precincts=${pool.precincts}")
                var ncontests = 0
                pool.styles.forEach { (_, style) ->
                    println("   countHasStyle=${style.countHasStyle} #contests=${style.contests.size} contests=${style.contests}")
                    allContests.addAll(style.contests)
                    ncontests += style.contests.size
                }
                var save = 0
                pool.styles.forEach { (_, style) ->
                    save += allContests.size - style.contests.size
                }
                val overlap = ncontests - allContests.size
                println("save=$save overlap=${ncontests - allContests.size} #allContests=${allContests.size}")
                println()

                allOverlap += overlap
                allSave += save
            }
        }

        println("allSave=$allSave allOverlap=${allOverlap}")
    }

    data class PoolCardStyles(val name: String) {
        val styles = mutableMapOf<Int, StylesCount>()
        val precincts = mutableSetOf<Int>()
        var countInPool = 0
    }

    data class StylesCount(val contests: Set<Int>, var countHasStyle: Int)

    @Test
    fun countAllCardStyles() {
        val stylesMap: MutableMap<Int, StylesCount> = mutableMapOf()
        var cardCount = 0
        cvrExportCsvIterator(cvrExportCsv).use { cvrIter ->
            while (cvrIter.hasNext()) {
                cardCount++
                val cvrExport: CvrExport = cvrIter.next()
                val contests: Set<Int> = cvrExport.votes.keys // the card style = list of contests
                val styleInPool = stylesMap.getOrPut(contests.hashCode()) { StylesCount(contests, 0) }
                styleInPool.countHasStyle++
            }
        }
        println("count cards = ${cardCount}\n")

        val sortedPairs: List<Pair<Int, StylesCount>> = stylesMap.toList().sortedBy { it.second.countHasStyle }
        sortedPairs.forEach { (_, sc) ->
            val contests = sc.contests.toList().sorted()
            println("sc=${sc.countHasStyle} contests=${contests} #contests=${contests.size}")
        }
    }

    @Test
    fun showCardStyles() {
        val cardStyles1 = mutableMapOf<Set<Int>, CardStyleCounter>() // contests.hashCode -> contests
        val cardStylesAll = mutableMapOf<Set<Int>, CardStyleCounter>() // contests.hashCode -> contests
        var cardCount = 0
        cvrExportCsvIterator(cvrExportCsv).use { cvrIter ->
            while (cvrIter.hasNext()) {
                cardCount++
                val cvrExport: CvrExport = cvrIter.next()
                if (cvrExport.group == 1) { // only want the precinct voting
                    val cardStyle = cardStyles1.getOrPut(cvrExport.votes.keys) { CardStyleCounter(cardStyles1.size + 1, cvrExport.votes.keys) }
                    cardStyle.count++
                }
                val cardStyle = cardStylesAll.getOrPut(cvrExport.votes.keys) { CardStyleCounter(cardStylesAll.size + 1, cvrExport.votes.keys) }
                cardStyle.count++
            }
        }
        println("count cards = ${cardCount}\n")

        println("card styles in precincts (${cardStyles1.size})")
        val sorted1 = cardStyles1.toList().sortedBy { it.second.count }
        sorted1.forEach { (_, pv) ->
            println(pv)
        }
        println("\nall card styles (${cardStylesAll.size})")
        val sortedAll = cardStylesAll.toList().sortedBy { it.second.count }
        sortedAll.forEach { (_, pv) ->
            println(pv)
        }

        cardStylesAll.forEach { (key, fromAll) ->
            val from1 = cardStyles1[key]!!
            assertEquals(fromAll.contests, from1.contests)
        }
    }

    class CardStyleCounter(val id: Int, val contests: Set<Int>) {
        var count = 0
        override fun toString(): String {
            return "$count $contests)"
        }

    }

    @Test
    fun showPrecinctStyles() {
        val precinctMap: MutableMap<Int, PrecinctVote> = mutableMapOf()
        var cardCount = 0
        cvrExportCsvIterator(cvrExportCsv).use { cvrIter ->
            while (cvrIter.hasNext()) {
                cardCount++
                val cvrExport: CvrExport = cvrIter.next()
                if (cvrExport.group == 1) { // only want the precinct voting
                    val precinctVote =
                        precinctMap.getOrPut(cvrExport.precinctPortionId) { PrecinctVote(ballotTypesContestManifest,cvrExport.precinctPortionId) }
                    precinctVote.poolIds.add(cvrExport.poolKey())
                    precinctVote.styles.add(cvrExport.ballotStyleId)
                    precinctVote.addContests(cvrExport.votes.keys)
                }
            }
        }
        println("count cards = ${cardCount}\n")

        precinctMap.toSortedMap().forEach { (_, pv) ->
            println(pv)
        }
    }
}

class PrecinctVote(val ballotTypes: BallotTypesContestManifest, val precinct: Int) {
    val styles = mutableSetOf<Int>()
    val poolIds = mutableSetOf<String>()
    val stylesMap: MutableMap<Int, StylesCount> = mutableMapOf()

    fun addContests(contests: Set<Int>) {
        val styleInPool = stylesMap.getOrPut(contests.hashCode()) { StylesCount(contests, 0) }
        styleInPool.countHasStyle++
    }

    override fun toString() = buildString {
        appendLine("precinct=$precinct, styles=$styles, poolIds=$poolIds")
        val sortedPairs: List<Pair<Int, StylesCount>> = stylesMap.toList().sortedBy { it.second.countHasStyle }
        sortedPairs.forEach { (_, sc) ->
            val contests = sc.contests.toList().sorted()
            appendLine("  sc=${sc.countHasStyle} contests=${contests} #contests=${contests.size}")
        }
        val style = styles.first()
        val styleTypeContests = ballotTypes.ballotStyles[style]!!.toSet()
        appendLine("    style=${style} contests=${styleTypeContests} #contests=${styleTypeContests.size}")

        val match = stylesMap[styleTypeContests.hashCode()]
        if (match == null) {
            appendLine("    NO MATCH")

            var count = 0
            val others = mutableSetOf<Int>()
            stylesMap.forEach {
                others.addAll(it.value.contests)
                count += it.value.contests.size
            }
            if (others == styleTypeContests) appendLine("    all others agree with styleTypeContests")
                else appendLine("    NO MATCH with others diff = ${others - styleTypeContests}")
            if (count == styleTypeContests.size) appendLine("    others are disjoint")
                else appendLine("    NO others are not disjoint")

        } else {
            appendLine("    agrees with sc=${match.countHasStyle}")
            if (stylesMap.size > 1) {
                var count = 0
                val others = mutableSetOf<Int>()
                stylesMap.filter { it.value != match }.forEach {
                    others.addAll(it.value.contests)
                    count += it.value.contests.size
                }
                if (others == styleTypeContests) appendLine("    cards agree with styleTypeContests")
                    else appendLine("    NO MATCH with others diff = ${others - styleTypeContests}")
                if (count == styleTypeContests.size) appendLine("    cards are disjoint")
                    else appendLine("    NO others are not disjoint")
            }
        }
    }
}





