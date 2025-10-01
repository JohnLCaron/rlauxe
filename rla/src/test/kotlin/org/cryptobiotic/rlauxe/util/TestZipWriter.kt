package org.cryptobiotic.rlauxe.util

import kotlin.test.Test
import kotlin.test.assertTrue

class TestZipWriter {


    @Test
    fun testWriteAndReadImplicit() {
        val filename = "../cases/src/test/data/2024election/summary.csv"
        createZipFile(filename, delete = false)

        val zipFilename = "../cases/src/test/data/2024election/summary.csv.zip"
        val reader = ZipReader(zipFilename)
        val input = reader.inputStream() // implicit name
        val ba = ByteArray(100)
        input.read(ba)
        val first100 = String(ba) // probably 88901 charset
        println(first100)
        input.close()
        assertTrue(first100.startsWith("\"line number\",\"contest name\",\"choice name\",\"party name\","))
    }

}