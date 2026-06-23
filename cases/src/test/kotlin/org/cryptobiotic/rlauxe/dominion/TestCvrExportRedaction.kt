package org.cryptobiotic.rlauxe.dominion

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.auditcenter.Colorado2020General
import org.cryptobiotic.rlauxe.auditcenter.CountyContestBuilder
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.estimate.simulateCards
import org.cryptobiotic.rlauxe.util.CardTabulation
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.votedatabase.colorado2020
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.collections.sum
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertTrue

class TestCvrExportRedaction {
    val show = false

    @Test
    fun testBoulder20() {
        testRedactedBallots("/home/stormy/datadrive/votedatabase/cvr/Colorado/Boulder/cvr.csv")
    }

    @Test
    fun testDolores20() {
        testRedactedBallots("/home/stormy/datadrive/votedatabase/cvr/Colorado/Dolores/cvr.csv")
    }

    @Test
    fun testGarfield20() {
        testRedactedBallots("/home/stormy/datadrive/votedatabase/cvr/Colorado/Garfield/cvr.csv")
    }

    fun testRedactedBallots(exportFile: String) {
        val export: DominionCvrCsvSummary = if (exportFile.contains("Garfield")) GarfieldCsvReader(exportFile).read() else
            DominionCvrExportCsvReader(exportFile).read()

        val styles: Map<String, ExportCardStyle> = export.exportCardStyles.associateBy { it.name }
        var totalLines = 0
        var totalVotes = 0
        var totalMinCards = 0
        export.redactedGroups.forEach { group ->
            val minCards = group.minCards()
            totalMinCards += minCards
            print("  $group, minCards=$minCards")
            totalLines += group.nlines
            totalVotes += group.totalVotes()

            val groupIds = group.contestVotes.filter { (key, cands) -> cands.any { it.value > 0 } }.map { it.key }.toSet()
            val match = styles[group.ballotType]
            if (match != null) {
                // filter out where contests where no votes were seen
                val missingInStyle = groupIds - match.contests
                val missingInRedaction = match.contests - groupIds
                assertTrue(missingInStyle.isEmpty())
                assertTrue(missingInRedaction.isEmpty())
                print(" matching style has=${match.contests.size} contest")
            } else {
                print(" *** no match for ${group.ballotType} with ${groupIds.size} non-zero contests ")
            }
            // print("match = $match")
            println()
        }
        println("ngroups = ${export.redactedGroups.size}")
        println("totalLines = $totalLines")
        println("sum votes = $totalVotes")
        println("sum minCards = $totalMinCards")

        // all redactions have 49 contests, probably put in 0 instead of blank
        // but can match DS name, and use that as the card style. test if any contests are non-zero
        // then add minCards cards for each style to the county pool
    }

    @Test
    fun testMakeRedactedPools() {
        val filename = "/home/stormy/datadrive/votedatabase/cvr/Colorado/Boulder/cvr.csv"
        val export = DominionCvrExportCsvReader(filename).read()

        val input = Colorado2020General()
        val contestBuilder = CountyContestBuilder(input)
        val infos = contestBuilder.infos
        val infosByName = infos.mapKeys{ it.value.name }
        val dominionConverter = DominionConverter("Boulder", export, infosByName, input)

        /*
        val group1 = export.redactedGroups.first()
        println("${group1}")
        group1.contestVotes.forEach { it: Map.Entry<Int, MutableMap<Int, Int>> ->
            val sumVotes = it.value.values.sumOf { it }
            println("   $it sumVotes=$sumVotes")
        }
        val pool1 = dominionConverter.redactedPools.first()
        println("${pool1}")
        pool1.contestTabs.forEach { println("   $it") }
         */

        val poolMap = dominionConverter.redactedPools.associateBy { it.poolName.substring("Boulder-".length) }

        var totalNcards = 0
        var totalNvotes = 0
        export.redactedGroups.forEach { group ->
            val pool = poolMap[group.ballotType]!! // hmm maybe not unique?
            var gnzCount = 0
            var pnzCount = 0
            var gnvotes = 0
            var pnvotes = 0
            group.contestVotes.forEach { groupVoteMap: Map.Entry<Int, MutableMap<Int, Int>> ->
                val lookup: ExportToCanonLookup = dominionConverter.exportToCanonLookup[groupVoteMap.key]!!
                val poolTab = pool.contestTabs[lookup.canonContestId]
                if (poolTab != null) {
                    groupVoteMap.value.forEach { (rcandid, rvotes) ->
                        val canonCandId = lookup.candLookup[rcandid]
                        if (canonCandId >= 0) {
                            val pvotes = poolTab.votes[canonCandId]
                            // println("  $canonCandId: $rvotes == $pvotes")
                            assertEquals(rvotes, pvotes)
                        }
                    }
                    gnvotes += groupVoteMap.value.values.sum()
                    pnvotes += poolTab.nvotes()
                    if (groupVoteMap.value.values.sum() > 0) gnzCount++
                    if (poolTab.nvotes() > 0) pnzCount++
                }
            }
            // val styleCount = if (group.style != null) group.style!!.contests.size else 0
            println("group '${group.ballotType}' (${group.contestVotes.size} contests and pool '${pool.poolName}' (${pool.contestTabs.size} contests)")
            println("   group minCards=${group.minCards()} nvotes=${gnvotes} pool ncards=${pool.ncards()} nvotes=${pnvotes}")
            assertEquals(gnzCount, pnzCount)
            assertEquals(gnzCount, pool.contestTabs.size)
            assertEquals(group.minCards(), pool.ncards())
            assertEquals(gnvotes, pnvotes)
            totalNcards += pool.ncards()
            totalNvotes += pnvotes
        }
        println("totalNcards = $totalNcards")
        println("totalNvotes = $totalNvotes")
    }

    @Test
    fun testMakeCvrs() {
        val filename = "/home/stormy/datadrive/votedatabase/cvr/Colorado/Boulder/cvr.csv"
        val export = DominionCvrExportCsvReader(filename).read()

        val input = Colorado2020General()
        val contestBuilder = CountyContestBuilder(input)
        val infos = contestBuilder.infos
        val infosByName = infos.mapKeys{ it.value.name }

        val dominionConverter = DominionConverter("Boulder", export, infosByName, input)

        val redactedCards = mutableListOf<AuditableCard>()
        dominionConverter.redactedPools.forEach { pool ->
            println(pool)
            val rcvrs = simulateCards(pool)
            redactedCards.addAll(rcvrs)
            println("  made ${rcvrs.size} cards")

            val votes: Map<Int, IntArray> = rcvrs.first().votes()!!
            val sum = votes.values.map { it.sum() }.sum()
            println("    ${rcvrs.first()}")
            println("    nvotes=${sum}")
        }

        var nvotes = 0
        val tabulate = CardTabulation(Closer(redactedCards.iterator()), infos) { card ->
            // nvotes += card.sumVotes()
        }

        val tabNvotes = tabulate.tabs.values.sumOf { it.nvotes() }

        println("\nTotal:  ${redactedCards.size} cards  ${nvotes} votes tabNvotes = $tabNvotes")
    }

    @Test
    fun testBoulder22Primary() {
        val filename = "/home/stormy/datadrive/votedatabase/cvr/2022Primaries/Colorado/Boulder CO '22 Primary.csv"
        val export: DominionCvrCsvSummary = DominionCvrExportCsvReader(filename).read()
        export.exportCardStyles.forEach { type ->
            println("  $type")
        }
        // doesnt seem to have redactions
        export.redactedGroups.forEach { group ->
            println("  $group")
        }
    }

    @Test
    fun testBoulder24() {
        val filename = "src/test/data/Boulder2024/2024-Boulder-County-General-Recount-Redacted-Cast-Vote-Record.csv"
        val export: DominionCvrCsvSummary = DominionCvrExportCsvReader(filename).read()
        export.exportCardStyles.forEach { type ->
            println("  $type")
        }
        export.redactedGroups.forEach { group ->
            println("  $group")
        }
    }

    @Test
    fun testBoulder25() {
        val filename = "src/test/data/Boulder2025/Redacted-CVR-PUBLIC.csv"
        val export: DominionCvrCsvSummary = DominionCvrExportCsvReader(filename).read()
        export.exportCardStyles.forEach { type ->
            println("  $type")
        }
        export.redactedGroups.forEach { group ->
            println("  $group")
        }
    }

    @Test
    fun testEagle() { // redaction
        var filename = "$colorado2020/Eagle/cvr.csv"
        println(filename)
        val export: DominionCvrCsvSummary = DominionCvrExportCsvReader(filename).read()
        export.exportCardStyles.forEach { type ->
            println("  $type")
        }
        export.redactedGroups.forEach { group ->
            println("  $group")
        }
    }

    @Test
    fun testElPaso() { // redaction
        var filename = "/home/stormy/datadrive/votedatabase/cvr/Colorado/Jefferson/JeffCO_2020_CVR_Redacted.csv"
        println(filename)
        val export: DominionCvrCsvSummary = DominionCvrExportCsvReader(filename).read()
        export.exportCardStyles.forEach { type ->
            println("  $type")
        }
        export.redactedGroups.forEach { group ->
            println("  $group")
        }
    }

    // Boulder, Doloros, Pitkin, possibly Jefferson
    // /home/stormy/datadrive/votedatabase/cvr/Colorado/Jefferson/JeffCO_2020_CVR_Redacted.csv has columns shifted by 1
    // /home/stormy/datadrive/votedatabase/cvr/Colorado/Phillips/cvr.csv
    ///home/stormy/datadrive/votedatabase/cvr/Colorado/Phillips/Phillips 2020  cvr phillips county.csv
    //  RedactedGroup('r', ncards=1, contests=[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29] totalVotes=62726)
    @Test
    fun allColorado2020Counties() {
        val path = Path(colorado2020)
        path.listDirectoryEntries().sorted().filter { it.isDirectory() && !it.fileName.toString().startsWith("202") }
            .forEach { subdir ->
                val county = subdir.fileName.toString()
                if (county !in listOf("Monroe", "Roosevelt", "Garfield")) {
                    subdir.listDirectoryEntries().filter {
                        !it.isDirectory() && it.fileName.toString().endsWith(".csv")
                                && it.fileName.toString() != "summary.csv"
                                && !it.fileName.toString().contains("Manifest")
                    }.forEach { entry ->
                        try {
                            val filename = entry.toString()
                            println(filename)
                            val export: DominionCvrCsvSummary = DominionCvrExportCsvReader(filename).read()
                            export.redactedGroups.forEach { group -> println("  $group") }

                        } catch (e: Exception) {
                            println(e.message)
                            throw e
                        }
                    }
                }
            }
    }
}