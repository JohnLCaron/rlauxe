package org.cryptobiotic.rlauxe.util

import java.io.File
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals

class TestZipWriter {

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun testWriteAndReadImplicit() {
        val tempDir = createTempDirectory()
        val sourceFile = File("src/test/data/ballotPools.csv")
        val copiedFile = kotlin.io.path.createTempFile(tempDir, prefix = "ballotPools", suffix = ".csv").toFile()
        sourceFile.copyTo(copiedFile, overwrite = true)

        createZipFile(copiedFile.toString(), delete = false)

        val zipFilename = "${copiedFile}.zip"
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

        tempDir.deleteRecursively()
    }

}