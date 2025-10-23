package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.persist.csv.IteratorCvrExportStream
import java.io.InputStream
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
    fun testZipReaderTour() {
        val filename = "src/test/data/cvrExport.zip"
        var countFiles = 0
        var countCards = 0
        var countPoolCards = 0
        // ZipReaderTour(zipFile: String, val silent: Boolean = true, val sort: Boolean = true,
        //    val filter: (Path) -> Boolean, val visitor: (InputStream) -> Unit)
        val tour = ZipReaderTour(
            zipFile = filename,
            filter = { it.toString().endsWith(".csv") },
            visitor = { input ->
                countFiles++
                val (c1, c2) = readCvrExport(input)
                countCards += c1
                countPoolCards += c2
            },
        )
        tour.tourFiles()
        println("file count: $countFiles")
        println("card Count: $countCards")
        println("countPoolCards: $countPoolCards")

        assertEquals(10, countFiles)
        assertEquals(4252, countCards)
    }

    fun readCvrExport(input: InputStream): Pair<Int, Int> {
        var count = 0
        var countPoolData = 0
        IteratorCvrExportStream(input).use { cvrIter ->
            while (cvrIter.hasNext()) {
                val cvr = cvrIter.next()
                if (cvr.group == 1) countPoolData++
                count++
            }
        }
        return Pair(count, countPoolData)
    }

    @Test
    fun testZipReaderIterator() {
        val filename = "src/test/data/cvrExport.zip"
        var countCards = 0
        var countPoolCards = 0
        // ZipReaderTour(zipFile: String, val silent: Boolean = true, val sort: Boolean = true,
        //    val filter: (Path) -> Boolean, val visitor: (InputStream) -> Unit)
        val zipper = ZipReaderIterator(
            zipFile = filename,
            filter = { it.toString().endsWith(".csv") },
            reader = { input -> IteratorCvrExportStream(input) }
        )

        zipper.use { iter ->
            while (iter.hasNext()) {
                val cvr = iter.next()
                countCards++
                if (cvr.group == 1) countPoolCards++
            }
        }
        println("card Count: $countCards")
        println("countPoolCards: $countPoolCards")
        assertEquals(4252, countCards)
        assertEquals(0, countPoolCards)
    }
}
