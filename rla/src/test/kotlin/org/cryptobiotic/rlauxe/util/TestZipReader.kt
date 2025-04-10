package org.cryptobiotic.rlauxe.util

import kotlin.test.Test
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
}