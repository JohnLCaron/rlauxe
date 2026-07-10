package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.dominion.CastVoteRecord
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsv
import org.cryptobiotic.rlauxe.dominion.DominionRedactedGroup
import org.cryptobiotic.rlauxe.dominion.readCvrExportsFromResource
import org.cryptobiotic.rlauxe.util.compareMaps
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestBoulderVsDominionCvrExport {
    val cvrResource = "/resources/data/cases/boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip"
    val export: DominionCvrExportCsv = readCvrExportsFromResource(cvrResource)
    val exportOld: BoulderCvrExportCsv = readBoulderCvrExportsFromResource(cvrResource, "Boulder")

    val sovoResource = "/resources/data/cases/boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv"
    val sovo = readBoulderSOVfromResourcePath(sovoResource, "Boulder2024")

    @Test
    fun testSchema() {
        val schema = export.schema
        val schemaOld = exportOld.schema

        assertEquals(schemaOld.votesForN, schema.voteForNs)
        println("voteForNs ok")

        val infos = makeContestInfo()
        val infoOld = makeContestInfoOld()
        assertEquals(infoOld, infos)
        println("infos ok")
    }

    fun makeContestInfo(): List<ContestInfo> {
        val columns = export.schema.columns

        return sovo.contests.map { sovoContest ->
            val exportContest = export.schema.contests.find { it.contestName.startsWith(sovoContest.contestTitle) }!!
            val candidateMap = if (!exportContest.isIRV) {
                val candidateMap1 = mutableMapOf<String, Int>()
                var candIdx = 0
                for (col in exportContest.startCol..exportContest.startCol + exportContest.ncols - 1) {
                    if (columns[col].choice != "Write-in") { // remove write-ins
                        candidateMap1[columns[col].choice] = candIdx
                    }
                    candIdx++
                }
                candidateMap1

            } else { // there are ncand x ncand columns, so need something different here
                val candidates = mutableListOf<String>()
                for (col in exportContest.startCol..exportContest.startCol + exportContest.ncols - 1) {
                    candidates.add(columns[col].choice)
                }
                val pairs = mutableListOf<Pair<String, Int>>()
                repeat(exportContest.nchoices) { idx ->
                    pairs.add(Pair(candidates[idx], idx))
                }
                pairs.toMap()
            }

            val choiceFunction = if (exportContest.isIRV) SocialChoiceFunction.IRV else SocialChoiceFunction.PLURALITY
            val (name, nwinners) = if (exportContest.isIRV) parseIrvContestName(exportContest.contestName) else parseContestNameAndVoteFor(exportContest.contestName)
            ContestInfo( name, exportContest.contestIdx, candidateMap, choiceFunction, nwinners)
        }
    }

    fun makeContestInfoOld(): List<ContestInfo> {
        val columns = export.schema.columns

        return sovo.contests.map { sovoContest ->
            val exportContest = exportOld.schema.contests.find { it.contestName.startsWith(sovoContest.contestTitle) }!!
            val candidateMap = if (!exportContest.isIRV) {
                val candidateMap1 = mutableMapOf<String, Int>()
                var candIdx = 0
                for (col in exportContest.startCol..exportContest.startCol + exportContest.ncols - 1) {
                    if (columns[col].choice != "Write-in") { // remove write-ins
                        candidateMap1[columns[col].choice] = candIdx
                    }
                    candIdx++
                }
                candidateMap1

            } else { // there are ncand x ncand columns, so need something different here
                val candidates = mutableListOf<String>()
                for (col in exportContest.startCol..exportContest.startCol + exportContest.ncols - 1) {
                    candidates.add(columns[col].choice)
                }
                val pairs = mutableListOf<Pair<String, Int>>()
                repeat(exportContest.nchoices) { idx ->
                    pairs.add(Pair(candidates[idx], idx))
                }
                pairs.toMap()
            }

            val choiceFunction = if (exportContest.isIRV) SocialChoiceFunction.IRV else SocialChoiceFunction.PLURALITY
            val (name, nwinners) = if (exportContest.isIRV) parseIrvContestName(exportContest.contestName) else parseContestNameAndVoteFor(exportContest.contestName)
            ContestInfo( name, exportContest.contestIdx, candidateMap, choiceFunction, nwinners)
        }
    }

    @Test
    fun testBallotType() {
        var count = 0
        val ballotTypes = mutableMapOf<String, MutableList<List<Int>>>()
        export.cvrs.forEach { cvr: CastVoteRecord ->
            val contestIds = cvr.contestVotes.map { it.contestId }
            val prevContestIds = ballotTypes.getOrPut(cvr.ballotType) { mutableListOf() }
            if (!prevContestIds.contains(contestIds)) {
                prevContestIds.add(contestIds)
            }
            count++
        }
        println("\nballotTypes")
        ballotTypes.toSortedMap().forEach { println(it) }

        val ballotTypesOld = mutableMapOf<String, MutableList<List<Int>>>()
        exportOld.cvrs.forEach { cvr: org.cryptobiotic.rlauxe.boulder.CastVoteRecord ->
            val contestIds = cvr.contestVotes.map { it.contestId }
            val prevContestIds = ballotTypesOld.getOrPut(cvr.ballotType) { mutableListOf() }
            if (!prevContestIds.contains(contestIds)) {
                prevContestIds.add(contestIds)
            }
            count++
        }
        println("\nballotTypesOld")
        ballotTypesOld.toSortedMap().forEach { println(it) }
        var allOk = compareMaps(ballotTypesOld, ballotTypes)
        assertTrue(allOk)

        // trying to pair the A and the B ballotTypes
        var styleId = 1
        val cardStyles = mutableMapOf<String, CardStyle>()
        ballotTypes.toSortedMap().forEach { (key, value) ->
            if (value.size == 2) {
                val (styleA, styleB) = if (value[0].size > value[1].size) {
                    Pair(value[0], value[1])
                } else {
                    Pair(value[1], value[0])
                }
                cardStyles[key + "-A"] = CardStyle(key + "-A", styleId, styleA.toIntArray(), true)
                cardStyles[key + "-B"] = CardStyle(key + "-B", styleId + 1, styleB.toIntArray(), true)
                styleId += 2
            } else {
                value.forEach { contestIds ->
                    cardStyles[key] = CardStyle(key, styleId, contestIds.toIntArray(), true)
                    styleId++
                }
            }
        }
        println("\ncardStyles")
        cardStyles.toSortedMap().forEach { println(it) }

        styleId = 1
        val cardStylesOld = mutableMapOf<String, CardStyle>()
        ballotTypesOld.toSortedMap().forEach { (key, value) ->
            if (value.size == 2) {
                val (styleA, styleB) = if (value[0].size > value[1].size) {
                    Pair(value[0], value[1])
                } else {
                    Pair(value[1], value[0])
                }
                cardStylesOld[key + "-A"] = CardStyle(key + "-A", styleId, styleA.toIntArray(), true)
                cardStylesOld[key + "-B"] = CardStyle(key + "-B", styleId + 1, styleB.toIntArray(), true)
                styleId += 2
            } else {
                value.forEach { contestIds ->
                    cardStylesOld[key] = CardStyle(key, styleId, contestIds.toIntArray(), true)
                    styleId++
                }
            }
        }
        println("\ncardStylesOld")
        cardStylesOld.toSortedMap().forEach { println(it) }
        allOk = compareMaps(cardStylesOld, cardStyles)
        assertTrue(allOk)
    }

    @Test
    fun testRedacted() {
        val redactedNlines = export.redactedGroups.sumOf { it.nlines }
        val redactedMinCards = export.redactedGroups.sumOf { it.minCards() }
        val redactedVotes = export.redactedGroups.sumOf { it.totalVotes() }
        println("\nredacted votes=$redactedVotes minCards=$redactedMinCards nlines=$redactedNlines")
        export.redactedGroups.forEach { println("  $it") }

        val votesForN = exportOld.schema.votesForN
        val redactedNlinesOld = exportOld.redacted.sumOf { it.ncards }
        val redactedMinCardsOld = exportOld.redacted.sumOf { it.minCards(votesForN) }
        val redactedVotesOld = exportOld.redacted.sumOf { it.totalVotes() }
        println("\nredactedOld votes=$redactedVotesOld minCards=$redactedMinCardsOld nlines=$redactedNlinesOld")

        exportOld.redacted.forEach { println("  $it") }

        //println("\ncompare old to new redacted groups by name")
        val allOk = compareRedactedMaps(exportOld.redacted.associateBy { it.ballotType }, export.redactedGroups.associateBy { it.ballotType }, false)
        println("\nagree = $allOk")
    }

    fun compareRedactedMaps(map1: Map<String, RedactedGroup>, map2: Map<String, DominionRedactedGroup>, show: Boolean = false, name1: String = "Boulder", name2: String = "Dominion"): Boolean {
        var allOk = true
        map1.forEach { (id1, val1) ->
            val val2 = map2[id1]
            if (val2 == null) println(" $name2 doesnt have '${id1}' from $name1")
            else if (!compareRedactedGroup(val1, val2)) println(" for $id1 values differ")
            else if (show) println("$val1 == $val2")
            allOk = allOk && compareRedactedGroup(val1, val2)
        }
        map2.forEach { (id2, val2) ->
            val val1 = map1[id2]
            if (val1 == null) println(" $name1 doesnt have '${id2}' from $name2")
            else if (!compareRedactedGroup(val1, val2)) println(" for $id2 values differ")
            else if (show) println("$val1 == $val2")
            allOk = allOk && compareRedactedGroup(val1, val2)
        }
        return allOk
    }

    fun compareRedactedGroup(g1: RedactedGroup?, g2: DominionRedactedGroup?): Boolean {
        if ((g1 == null) == (g2 == null)) return true
        if ((g1 == null) || (g2 == null)) return false
        if (g1.ballotType != g2.ballotType ) return false
        if (g1.ncards != g2.nlines ) return false
        if (g1.contests() != g2.contests() ) return false
        if (g1.totalVotes() != g2.totalVotes() ) return false
        // if (g1.minCards() != g2.minCards() ) return false
        if (g1.contestVotes != g2.contestVotes ) return false
        return true
    }

    // with this exception, redacted groups match existing CardStyle:
    // RedactedGroup '06, 33, & 36-A', contestIds=[0, 1, 2, 3, 5, 10, 11, 12, 13, 14, 15, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42], totalVotes=8012
    //*** rgroup '06, 33, & 36-A'
    // [0, 1, 2, 3, 5, 10, 11, 12, 13, 14, 15, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42] !=
    // [0, 1, 2, 3, 5, 10, 11, 13, 14, 15, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42] (6-A)
    //
    // still, we will assume that all ballots in a group have the same CardStyle, which makes it easier to generate accurate simulated CVRs.
    // this wrongly includes contest 12,

    fun extractBallotType(s: String): String {
        val btoke = s.split(" ", "-", ",")[0]
        try {
            return btoke.toInt().toString()
        } catch (e: NumberFormatException) {
            println("extractBallotType $btoke")
            return ""
        }
    }
}