package org.cryptobiotic.rlauxe.persist.csv

import kotlin.test.Test
import kotlin.test.assertEquals

import org.cryptobiotic.rlauxe.audit.AuditableCard

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

}