package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.core.CvrExport
import org.cryptobiotic.rlauxe.persist.csv.IteratorCvrExportFile
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import kotlin.test.Test
import kotlin.test.assertEquals

class TestTreeReader {

    // @Test slow
    fun testTreeReaderIterator() {
        val topDir = "/home/stormy/rla/cases/corla/cards"
        // class TreeReaderIterator <T> (
        //    topDir: String,
        //    val fileFilter: (Path) -> Boolean,
        //    val reader: (Path) -> Iterator<T>
        //)
        var countFiles = 0
        val treeIter = TreeReaderIterator(topDir,
            fileFilter = { it.toString().endsWith(".csv") },
            reader = {  path -> countFiles++; IteratorCvrExportFile(path.toString()) },
        )
        var summVotes = 0
        var countCards = 0
        while (treeIter.hasNext()) {
            val cvrExport: CvrExport = treeIter.next()
            countCards++
            if (cvrExport.votes != null) {
                cvrExport.votes.values.forEach { summVotes += it.sum() }
            }
        }
        // TODO anything smaller to test on ?
        assertEquals(3199, countFiles)
        assertEquals(3191197, countCards)
        assertEquals(38968771, summVotes)
    }

    // @Test TODO get smaller test data
    fun testTreeReaderTour() {
        val topDir = "/home/stormy/rla/cases/corla"
        var countFiles = 0
        val tour = TreeReaderTour("$topDir/cvrexport", visitor = { countFiles++ })
        tour.tourFiles()
        assertEquals(3199, countFiles)
    }
}