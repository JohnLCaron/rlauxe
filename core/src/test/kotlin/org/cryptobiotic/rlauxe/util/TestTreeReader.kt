package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.core.CvrExport
import org.cryptobiotic.rlauxe.persist.csv.cvrExportCsvIterator
import kotlin.test.Test
import kotlin.test.assertEquals

class TestTreeReader {

    @Test
    fun testTreeReaderIterator() {
        val stopwatch = Stopwatch()
        val topDir = "src/test/data/cvrexport"
        var countFiles = 0
        var summVotes = 0
        var countCards = 0

        TreeReaderIterator(topDir,
            fileFilter = { it.toString().endsWith(".csv") },
            reader = {  path -> countFiles++; cvrExportCsvIterator(path.toString()) },
        ).use { treeIter ->
            while (treeIter.hasNext()) {
                val cvrExport: CvrExport = treeIter.next()
                countCards++
                cvrExport.votes.values.forEach { summVotes += it.sum() }
            }
        }
        println("that took $stopwatch")

        assertEquals(10, countFiles)
        assertEquals(4252, countCards)
        assertEquals(54382, summVotes)
    }

    @Test
    fun testTreeReaderTour() {
        val stopwatch = Stopwatch()
        val topDir = "src/test/data/cvrexport"
        var countFiles = 0
        val tour = TreeReaderTour(topDir, visitor = { countFiles++ })
        tour.tourFiles()
        assertEquals(10, countFiles)
        println("that took $stopwatch")
    }
}