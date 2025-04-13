package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import kotlin.test.Test
import kotlin.test.assertEquals

class TestTreeReader {

    @Test
    fun testTreeReaderIterator() {
        val topDir = "/home/stormy/temp/cases/corla/cards"
        // class TreeReaderIterator <T> (
        //    topDir: String,
        //    val fileFilter: (Path) -> Boolean,
        //    val reader: (Path) -> Iterator<T>
        //)
        var countFiles = 0
        val treeIter = TreeReaderIterator(topDir,
            fileFilter = { it.toString().endsWith(".csv") },
            reader = {  path -> countFiles++; readCardsCsvIterator(path.toString()) },
        )
        var summVotes = 0
        var countCards = 0
        while (treeIter.hasNext()) {
            val card = treeIter.next()
            countCards++
            if (card.votes != null) {
                card.votes!!.forEach { summVotes += it.sum() }
            }
        }
        // TODO anything smaller to test on ?
        assertEquals(3199, countFiles)
        assertEquals(3191197, countCards)
        assertEquals(38968771, summVotes)
    }

    @Test
    fun testTreeReaderTour() {
        val dirname = "/home/stormy/temp/cases/sf2024P/CVR_Export_20240322103409"
        var countFiles = 0
        val tour = TreeReaderTour(dirname, visitor = { countFiles++ })
        tour.tourFiles()
        assertEquals(8972, countFiles)
    }
}