package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCardSortMerge {

    @Test
    fun testCardSortMerge() {
        val topDir = "/home/stormy/temp/cases/sf2024Poa"
        sortMergeCards("$topDir/audit",
            "$topDir/cards.csv",
            "$topDir/sortChunkTest",
            "$topDir/sortChunkTest/sortedCards.csv",
            )
        val cardIter = readCardsCsvIterator("$topDir/sortChunkTest/sortedCards.csv")
        var count = 0
        cardIter.forEach { count++ }
        assertEquals(467063, count)
    }

}