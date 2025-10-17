package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.persist.csv.IteratorCvrExportFile
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCardSortMerge {
    val tempDir = createTempDirectory()
    val cvrExportFile = "src/test/data/cvrExport.csv"
    val cvrExportTree = "src/test/data/cvrexport"

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
            ).run(cvrExportFile)

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
        ).run("${cvrExportFile}.zip")

        val cardIter = readCardsCsvIterator(outputFile)
        var count = 0
        cardIter.forEach { count++ }
        assertEquals(1377, count)
    }

    @Test
    fun testSortMergeRun2() {
        val outputFile = "$tempDir/testSortMergeRun2/testSortMergeRun2.csv"
        val cvrIter = TreeReaderIterator(cvrExportTree,
            fileFilter = { it.toString().endsWith(".csv") },
            reader = {  path -> IteratorCvrExportFile(path.toString()) },
        )
        SortMerge(
            "$tempDir/testSortMergeRun2",
            outputFile = outputFile,
            seed = Random.Default.nextLong(),
            poolNameToId = null,
            maxChunk = 100,
        ).run2(cvrIter, emptyList())

        val cardIter = readCardsCsvIterator(outputFile)
        var count = 0
        cardIter.forEach { count++ }
        assertEquals(4252, count)
    }

}