package org.cryptobiotic.rlauxe.util

import kotlin.test.Test
import kotlin.test.assertEquals

class TestZipWriter {

    @Test
    fun testWriteAndReadImplicit() {
        val filename = "src/test/data/ballotPools.csv"
        createZipFile(filename, delete = false)

        val zipFilename = "src/test/data/ballotPools.csv.zip"
        val reader = ZipReader(zipFilename) // implicit name
        reader.inputStream().use { input ->
            val ba = ByteArray(100)
            input.read(ba)
            val first100 = String(ba)
            println(first100)
            val expected = """Pool, PoolId, ContestId, ncards, candidate:nvotes, ...
1-A, 0, 0, 154, 0: 101, 1: 45, 2: 0, 3: 1, 4:"""
            assertEquals(expected, first100)
        }
    }

}