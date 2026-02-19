package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.dominion.CvrExport
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvFile
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvIterator
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
    val cvrExportFile = "src/test/data/${cvrExportCsvFile}"
    val pools = mapOf("3065846003" to 1)

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun runAfter() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testSortMergeRun() {
        val outputFile = "$tempDir/testSortMergeRun/testSortMergeRun.csv"
        val sorter = SortMerge<CvrExport>(
            "$tempDir/testSortMergeRun",
            outputFile = outputFile,
            seed = Random.Default.nextLong(),
            maxChunk = 100
        )
        sorter.run(
            cardIter = cvrExportCsvIterator(filename = cvrExportFile),
            toAuditableCard = { from: CvrExport, index: Int, prn: Long -> toAuditableCard(from, index, prn, pools) }
        )

        val cardIter = readCardsCsvIterator(outputFile)
        var count = 0
        var countPools = 0
        cardIter.forEach {
            if (it.poolId != null) countPools++
            count++
        }
        assertEquals(1377, count)
        assertEquals(3, countPools) // 3 records have group = 1
    }

    @Test
    fun testSortMergeRunZip() {
        val outputFile = "$tempDir/testSortMergeRunZip/testSortMergeRunZip.csv"
        val sorter = SortMerge<CvrExport>(
            "$tempDir/testSortMergeRunZip",
            outputFile = outputFile,
            seed = Random.Default.nextLong(),
            maxChunk = 100,
        )

        sorter.run(
            cardIter = cvrExportCsvIterator(filename = "$cvrExportFile.zip"),
            toAuditableCard = { from: CvrExport, index: Int, prn: Long -> toAuditableCard(from, index, prn, pools) }
        )

        val cardIter = readCardsCsvIterator(outputFile)
        var count = 0
        var countPools = 0
        cardIter.forEach {
            if (it.poolId != null) countPools++
            count++
        }
        assertEquals(1377, count)
        assertEquals(0, countPools) // the zip file has no pooled data
    }

    @Test
    fun testSortMergeRunZipTree() {
        val outputFile = "$tempDir/testSortMergeRunZipTree/testSortMergeRunZipTree.csv"
        val sorter = SortMerge<CvrExport>(
            "$tempDir/testSortMergeRunZipTree",
            outputFile = outputFile,
            seed = Random.Default.nextLong(),
            maxChunk = 100,
        )

        sorter.run(
            cardIter = cvrExportCsvIterator(filename = "$cvrExportFile.zip"),
            toAuditableCard = { from: CvrExport, index: Int, prn: Long -> toAuditableCard(from, index, prn, pools) }
        )

        val cardIter = readCardsCsvIterator(outputFile)
        var count = 0
        var countPools = 0
        cardIter.forEach {
            if (it.poolId != null) countPools++
            count++
        }
        assertEquals(1377, count)
        assertEquals(0, countPools) // the zip file has no pooled data
    }

    fun toAuditableCard(from: CvrExport, index: Int, prn: Long, pools: Map<String, Int>? = null, showPoolVotes: Boolean = true): AuditableCard {
        return from.toAuditableCard(index=index, prn=prn, false, pools = pools, showPoolVotes)
    }

}