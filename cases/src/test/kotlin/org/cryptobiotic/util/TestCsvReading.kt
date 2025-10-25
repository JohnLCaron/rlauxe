package org.cryptobiotic.util

import org.cryptobiotic.rlauxe.persist.csv.AuditableCardCsvReader
import org.cryptobiotic.rlauxe.persist.csv.cvrExportCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeCvrExportCsvFile
import org.cryptobiotic.rlauxe.persist.cvrExportCsvFile
import org.junit.jupiter.api.Assertions
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCsvReading {
    val tempDir = createTempDirectory()

    @Test
    fun testAuditableCardCsvFile() {
        val filenameIn = "/home/stormy/rla/cases/sf2024/oa/audit/sortedCards.csv"
        val original = AuditableCardCsvReader(filenameIn).iterator().asSequence().toList()
        val filenameOut = "$tempDir/sfCards.csv"

        writeAuditableCardCsvFile(original, filenameOut)
        val roundtrip = readAuditableCardCsvFile(filenameOut)
        assertEquals(original, roundtrip)
    }

    // @Test // slow TODO get smaller test file
    fun testCvrExportCsvFile() {
        val filenameIn = "/home/stormy/rla/cases/sf2024/${cvrExportCsvFile}"
        val filenameOut = "$tempDir/${cvrExportCsvFile}"
        writeCvrExportCsvFile(cvrExportCsvIterator(filenameIn), filenameOut)
        Assertions.assertTrue(areIteratorsEqual(cvrExportCsvIterator(filenameIn), cvrExportCsvIterator(filenameOut)))
    }

    fun <T> areIteratorsEqual(iterator1: Iterator<T>, iterator2: Iterator<T>): Boolean {
        while (iterator1.hasNext() && iterator2.hasNext()) {
            if (iterator1.next() != iterator2.next()) {
                return false // Elements differ
            }
        }
        // Check if both iterators have reached their end simultaneously
        return !iterator1.hasNext() && !iterator2.hasNext()
    }

    /*
    @Test
    fun testMakeCardPoolsFromAuditRecord() {
        val topDir = "/home/stormy/rla/cases/boulder24oa/audit"
        val auditRecord = readFrom(topDir)

        val cardPools = makeCardPoolsFromAuditRecord(auditRecord)
        println(cardPools.showPoolVotes())
    } */

}