package org.cryptobiotic.rlauxe.persist.csv

import kotlin.test.Test
import kotlin.test.assertEquals

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.CvrExport
import org.junit.jupiter.api.Assertions.assertTrue

class TestCvrExportCsv {

    @Test
    fun testRoundtrip() {
        val target = CvrExport(
            "info to find card",
            42,
            mapOf(
                19 to intArrayOf(1, 2, 3),
                23 to intArrayOf(),
                99 to intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0),
                123456 to intArrayOf(23498724)
            ),
        )

        val csv = target.toCsv()
        print(CvrExportCsvHeader)
        println(csv)

        val roundtrip = readCvrExportCsv(csv)
        assertEquals(target, roundtrip)
    }

    @Test
    fun testToAuditableCard() {
        val cvr = CvrExport (
            "test1-2-3",
            1,
            mapOf(19 to intArrayOf(1,2,3), 23 to intArrayOf(), 99 to intArrayOf(1,2,3,4,5,6,7,8,9,0), 123456 to intArrayOf(23498724)),
        )
        val card = cvr.toAuditableCard(42, 43L, true, mapOf("test1-2" to 99))

        val target = AuditableCard (
            "test1-2-3",
            42,
            43L,
            true,
            intArrayOf(19, 23, 99, 123456),
            listOf(intArrayOf(1,2,3), intArrayOf(), intArrayOf(1,2,3,4,5,6,7,8,9,0), intArrayOf(23498724)),
            99,
        )

        assertEquals(target, card)
    }

    @Test // slow
    fun testSf2024Poa() {
        val filenameIn = "/home/stormy/rla/cases/sf2024/cvrExport.csv"
        val filenameOut = "/home/stormy/tmp/cvrExport.csv"
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