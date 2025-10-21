package org.cryptobiotic.rlauxe.util

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
        // ZipReaderTour(zipFile: String, val silent: Boolean = true, val sort: Boolean = true,
        //    val filter: (Path) -> Boolean, val visitor: (InputStream) -> Unit)
        val tour = ZipReaderTour(filename,
            filter = { it.toString().endsWith(".csv") },
            visitor = { countFiles++ },
        )
        tour.tourFiles()
        assertEquals(10, countFiles)
    }
}