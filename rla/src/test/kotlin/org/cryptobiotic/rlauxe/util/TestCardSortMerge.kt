package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.persist.csv.cvrExportCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCardSortMerge {

    @Test
    fun testCardSortMerge() {
        val topDir = "/home/stormy/rla/cases/sf2024"
        val tempDir = "/home/stormy/rla/cases/temp"
        SortMerge("$topDir/audit",
            "$topDir/cvrExport.csv",
            "$tempDir/sortChunkTest",
            "$tempDir/sortChunkTest/sortedCards.csv",
            pools = null,
            ).run()
        val cardIter = readCardsCsvIterator("$tempDir/sortChunkTest/sortedCards.csv")
        var count = 0
        cardIter.forEach { count++ }
        assertEquals(1603908, count)
    }

    @Test
    fun testCardSortMerge2() {
        val topDir = "/home/stormy/rla/cases/sf2024"
        val tempDir = "/home/stormy/rla/cases/temp"
        val cvrIter = cvrExportCsvIterator("$topDir/cvrExport.csv")
        SortMerge("$topDir/audit",
            "dummy",
            "$tempDir/sortChunkTest",
            "$tempDir/sortChunkTest/sortedCards.csv",
            pools = null,
        ).run2(cvrIter)
        val cardIter = readCardsCsvIterator("$tempDir/sortChunkTest/sortedCards.csv")
        var count = 0
        cardIter.forEach { count++ }
        assertEquals(1603908, count)
    }

}