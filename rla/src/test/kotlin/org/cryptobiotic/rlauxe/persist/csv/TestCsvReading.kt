package org.cryptobiotic.rlauxe.persist.csv

import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.readFrom
import kotlin.test.Test
import kotlin.test.assertEquals

import org.junit.jupiter.api.Assertions.assertTrue

class TestCsvReading {
    val tempDir = kotlin.io.path.createTempDirectory()

    @Test
    fun testAuditableCardCsvFile() {
        val filenameIn = "/home/stormy/rla/cases/sf2024/oa/audit/sortedCards.csv"
        val original = readAuditableCardCsvFile(filenameIn)
        val filenameOut = "$tempDir/sfCards.csv"

        writeAuditableCardCsvFile(original, filenameOut)
        val roundtrip = readAuditableCardCsvFile(filenameOut)
        assertEquals(original, roundtrip)
    }

    // @Test // slow
    fun testCvrExportCsvFile() {
        val filenameIn = "/home/stormy/rla/cases/sf2024/cvrExport.csv"
        val filenameOut = "$tempDir/cvrExport.csv"
        writeCvrExportCsvFile(IteratorCvrExportFile(filenameIn), filenameOut)
        assertTrue(areIteratorsEqual(IteratorCvrExportFile(filenameIn), IteratorCvrExportFile(filenameOut)))
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