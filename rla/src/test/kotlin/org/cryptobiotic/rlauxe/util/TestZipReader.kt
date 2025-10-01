package org.cryptobiotic.rlauxe.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TestZipReader {

    @Test
    fun testRead() {
        val filename = "../cases/src/test/data/2024election/2024GeneralPrecinctLevelResults.zip"
        val reader = ZipReader(filename)
        val input = reader.inputStream("2024GeneralPrecinctLevelResults.csv")
        val ba = ByteArray(1000)
        input.read(ba)
        val first1000 = String(ba) // probably 88901 charset
        println(first1000)
        input.close()
        assertTrue(first1000.startsWith("\"County\",\"Precinct\",\"Contest\",\"Choice\",\"Party\",\"Total Votes\""))
    }

    @Test
    fun testErrors() {
        assertFailsWith<RuntimeException> {
            ZipReader("../my/bad")
        }.message!!.contains("does not exist")

        assertFailsWith<RuntimeException> {
            ZipReader("../cases/src/test/data/2024election/2024GeneralPrecinctLevelResults.csv")
        }.message!!.contains("must end in zip")
    }

    @Test
    fun testZipReaderTour() {
        val filename = "/home/stormy/rla/cases/sf2024P/CVR_Export_20240322103409.zip"
        var countFiles = 0
        // ZipReaderTour(zipFile: String, val silent: Boolean = true, val sort: Boolean = true,
        //    val filter: (Path) -> Boolean, val visitor: (InputStream) -> Unit)
        val tour = ZipReaderTour(filename,
            filter = { it.toString().contains("CvrExport_") },
            visitor = { countFiles++ },
        )
        tour.tourFiles()
        assertEquals(8957, countFiles)
    }
}