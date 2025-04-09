package org.cryptobiotic.rlauxe.persist.csv

import kotlin.test.Test
import kotlin.test.assertEquals

import org.cryptobiotic.rlauxe.audit.AuditableCard

class TestAuditableCardsCsv {

    @Test
    fun testRoundtrip() {
        val target = AuditableCard (
            "info to find card",
            42,
            43L,
            true,
            intArrayOf(19, 23, 99, 123456),
            listOf(intArrayOf(1,2,3), intArrayOf(), intArrayOf(1,2,3,4,5,6,7,8,9,0), intArrayOf(23498724)),
            11
        )

        val csv = writeAuditableCardCsv(target)
        print(AuditableCardHeader)
        println(csv)

        val roundtrip = readAuditableCardCsv(csv)
        assertEquals(target, roundtrip)
    }

    @Test
    fun testRoundtripNoVotes() {
        val target = AuditableCard (
            "deets",
            42,
            43L,
            false,
            intArrayOf(19, 23, 99, 123456),
            null,
            null,
        )

        val csv = writeAuditableCardCsv(target)
        print(AuditableCardHeader)
        println(csv)

        val roundtrip = readAuditableCardCsv(csv)
        assertEquals(target, roundtrip)
    }

    @Test
    fun testRoundtripIO() {
        val target = listOf(
            AuditableCard ("deets", 42, 43L, false, intArrayOf(19, 23, 99, 123456), null, 111),
            AuditableCard ("deets", 42, 43L, false, intArrayOf(19, 23, 99, 123456), null, null),
            AuditableCard ("info to find card", 42, 43L, true, intArrayOf(19, 23, 99, 123456), listOf(intArrayOf(1,2,3), intArrayOf(), intArrayOf(1,2,3,4,5,6,7,8,9,0), intArrayOf(23498724)), 11),
            AuditableCard ("info to find card", 42, 43L, true, intArrayOf(19, 23, 99, 123456), listOf(intArrayOf(1,2,3), intArrayOf(), intArrayOf(1,2,3,4,5,6,7,8,9,0), intArrayOf(23498724)), null),
        )

        val filenameOut = "/home/stormy/temp/tests/writeAuditableCardCsvFile.csv"
        writeAuditableCardCsvFile(target, filenameOut)

        val roundtrip = readAuditableCardCsvFile(filenameOut)
        assertEquals(target, roundtrip)
    }

    @Test
    fun testSf2024Poa() {
        val filenameIn = "/home/stormy/temp/cases/sf2024Poa/cards.csv"
        val original = readAuditableCardCsvFile(filenameIn)
        val filenameOut = "/home/stormy/temp/tests/sfCards.csv"

        writeAuditableCardCsvFile(original, filenameOut)
        val roundtrip = readAuditableCardCsvFile(filenameOut)
        assertEquals(original, roundtrip)
    }

}