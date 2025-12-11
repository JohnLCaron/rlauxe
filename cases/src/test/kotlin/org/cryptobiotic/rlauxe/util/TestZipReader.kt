package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.dominion.CvrExport
import org.cryptobiotic.rlauxe.dominion.IteratorCvrExportStream
import java.io.InputStream
import kotlin.collections.mutableSetOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TestZipReader {

    @Test
    fun testRead() {
        val zipFilename = "src/test/data/ballotPools.csv.zip"
        val reader = ZipReader(zipFilename)
        reader.inputStream().use { input ->
            val ba = ByteArray(1000)
            input.read(ba)
            val first1000 = String(ba) // probably 88901 charset
            println(first1000)
            assertTrue(first1000.startsWith("Pool, PoolId, ContestId, ncards, candidate:nvotes, ..."))
        }
    }

    @Test
    fun testErrors() {
        assertFailsWith<RuntimeException> {
            ZipReader("../my/bad")
        }.message!!.contains("does not exist")

        assertFailsWith<RuntimeException> {
            ZipReader("src/test/data/cvrExport.csv")
        }.message!!.contains("must end in zip")
    }

    @Test
    fun testZipReaderTourAndIter() {
        val filename = "src/test/data/cvrExport.zip"
        var countFiles = 0
        var countCards = 0
        var countPoolCards = 0

        //// ZipReaderTour
        val cardSet = mutableSetOf<CvrExport>()
        val tour = ZipReaderTour(
            zipFile = filename,
            filter = { it.toString().endsWith(".csv") },
            visitor = { input ->
                countFiles++
                val (c1, c2) = readCvrExport(input, cardSet)
                countCards += c1
                countPoolCards += c2
            },
        )
        tour.tourFiles()
        println("file count: $countFiles")
        println("card Count: $countCards cardSet size ${cardSet.size}")
        println("countPoolCards: $countPoolCards")

        assertEquals(10, countFiles)
        assertEquals(4252, countCards)

        //// ZipReaderIterator
        val cardSet2 = mutableSetOf<CvrExport>()
        var countCards2 = 0
        var countPoolCards2 = 0
        val zipper = ZipReaderIterator(
            zipFile = filename,
            filter = { it.toString().endsWith(".csv") },
            reader = { input -> IteratorCvrExportStream(input) }
        )

        zipper.use { iter ->
            while (iter.hasNext()) {
                val cvr = iter.next()
                cardSet2.add(cvr)
                countCards2++
                if (cvr.group == 1) countPoolCards2++
            }
        }

        val diffSet = cardSet - cardSet2
        println()
        println("diffSet = $diffSet")
        println("cardIter cardCount: $countCards2 cardSet size ${cardSet2.size}")
        println("cardIter countPoolCards: $countPoolCards2")
        assertEquals(countCards, countCards2)
        assertEquals(countPoolCards, countPoolCards2)
    }

    fun readCvrExport(input: InputStream, cardsSet: MutableSet<CvrExport>): Pair<Int, Int> {
        var count = 0
        var countPoolData = 0
        IteratorCvrExportStream(input).use { cvrIter ->
            while (cvrIter.hasNext()) {
                val cvr = cvrIter.next()
                cardsSet.add(cvr)
                if (cvr.group == 1) countPoolData++
                count++
            }
        }
        return Pair(count, countPoolData)
    }

}
