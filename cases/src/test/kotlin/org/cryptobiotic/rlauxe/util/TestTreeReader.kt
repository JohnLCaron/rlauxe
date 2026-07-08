package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.dominion.CvrExport
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvIterator
import kotlin.test.Test
import kotlin.test.assertEquals

class TestTreeReader {

    // @Test
    fun testTreeReaderIterator() {
        val stopwatch = Stopwatch()
        val topDir = "$cases/sf/sf2024/CVR_Export_20241202143051"
        var countFiles = 0
        var summVotes = 0
        var countCards = 0

        TreeReaderIterator(
            topDir,
            fileFilter = { it.toString().endsWith(".csv") },
            reader = { path -> countFiles++; cvrExportCsvIterator(path.toString()) },
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
        val topDir = "$cases/sf/sf2024/CVR_Export_20241202143051"
        var countFiles = 0
        val tour = TreeReaderTour(topDir, visitor = { countFiles++ })
        tour.tourFiles()
        assertEquals(27569, countFiles)
        println("that took $stopwatch")
    }
}