package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.persist.csv.cvrExportCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.cvrExportCsvFile
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCardSortMerge {
    val tempDir = createTempDirectory()
    val cvrExportFile = "src/test/data/$cvrExportCsvFile"

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun runAfter() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testSortMergeRun() {
        val outputFile = "$tempDir/testSortMergeRun/testSortMergeRun.csv"
        SortMerge(
            "$tempDir/testSortMergeRun",
            outputFile = outputFile,
            seed = Random.Default.nextLong(),
            poolNameToId = null,
            maxChunk = 100,
            showPoolVotes = true,
            ).run(cvrExportCsvIterator(cvrExportFile), emptyList())

        val cardIter = readCardsCsvIterator(outputFile)
        var count = 0
        cardIter.forEach { count++ }
        assertEquals(1377, count)
    }

    @Test
    fun testSortMergeRunZip() {
        val outputFile = "$tempDir/testSortMergeRunZip/testSortMergeRunZip.csv"
        SortMerge(
            "$tempDir/testSortMergeRunZip",
            outputFile = outputFile,
            seed = Random.Default.nextLong(),
            poolNameToId = null,
            maxChunk = 100,
            showPoolVotes = false,
            ).run(cvrExportCsvIterator("${cvrExportFile}.zip"), emptyList())

        val cardIter = readCardsCsvIterator(outputFile)
        var count = 0
        cardIter.forEach { count++ }
        assertEquals(1377, count)
    }

}