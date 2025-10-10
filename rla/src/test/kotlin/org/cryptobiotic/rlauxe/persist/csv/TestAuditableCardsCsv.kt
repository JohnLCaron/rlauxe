package org.cryptobiotic.rlauxe.persist.csv

import kotlin.test.Test
import kotlin.test.assertEquals

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.junit.jupiter.api.Assertions.assertTrue

class TestAuditableCardsCsv {

    @Test
    fun testSf2024Poa() {
        val filenameIn = "/home/stormy/rla/cases/sf2024Poa/cards.csv"
        val original = readAuditableCardCsvFile(filenameIn)
        val filenameOut = "/home/stormy/rla/tests/scratch/sfCards.csv"

        writeAuditableCardCsvFile(original, filenameOut)
        val roundtrip = readAuditableCardCsvFile(filenameOut)
        assertEquals(original, roundtrip)
    }

    @Test // slow
    fun testSf2024() {
        val filenameIn = "/home/stormy/rla/cases/sf2024/cvrExport.csv"
        val filenameOut = "/home/stormy/rla/scratch/cvrExport.csv"
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

}