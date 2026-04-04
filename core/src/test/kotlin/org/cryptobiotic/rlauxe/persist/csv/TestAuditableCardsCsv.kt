package org.cryptobiotic.rlauxe.persist.csv

import kotlin.test.Test
import kotlin.test.assertEquals

import org.cryptobiotic.rlauxe.audit.CardWithBatchName
import org.cryptobiotic.rlauxe.util.createZipFile

class TestAuditableCardsCsv {

    @Test
    fun testRoundtrip() {
        val target = CardWithBatchName (
            "info to find card",
            42,
            43L,
            true,
            //intArrayOf(19, 23, 99, 123456),
            mapOf(19 to intArrayOf(1,2,3), 23 to intArrayOf(), 99 to intArrayOf(1,2,3,4,5,6,7,8,9,0), 123456 to intArrayOf(23498724)),
            11,
            "pool11"
        )

        val csv = writeCardCsv(target)
        print(CardHeader)
        println(csv)

        val roundtrip = readCardCsv(csv)
        assertEquals(target, roundtrip)
    }

    @Test
    fun testRoundtripNoVotes() {
        val target = CardWithBatchName (
            "deets",
            42,
            43L,
            false,
            // intArrayOf(19, 23, 99, 123456),
            null,
            null,
            styleName = "all",
        )

        val csv = writeCardCsv(target)
        print(CardHeader)
        println(csv)

        val roundtrip = readCardCsv(csv)
        assertEquals(target, roundtrip)
    }

    @Test
    fun testRoundtripIO() {
        val target = listOf(
            CardWithBatchName ("deets", 42, 43L, false, null, 111, "pool111"),
            CardWithBatchName ("deets", 42, 43L, false, null, null, styleName="all"),
            CardWithBatchName ("info to find card", 42, 43L, true,
                mapOf(19 to intArrayOf(1,2,3), 23 to intArrayOf(), 99 to intArrayOf(1,2,3,4,5,6,7,8,9,0), 123456 to intArrayOf(23498724)), 11, "pool11"),
            CardWithBatchName ("info to find card", 42, 43L, true,
                mapOf(19 to intArrayOf(1,2,3), 23 to intArrayOf(), 99 to intArrayOf(1,2,3,4,5,6,7,8,9,0), 123456 to intArrayOf(23498724)), null, "cvr"),
        )

        val scratchFile = kotlin.io.path.createTempFile().toFile()
        writeCardCsvFile(target, scratchFile.toString())

        val roundtrip = readCardCsvFile(scratchFile.toString())
        assertEquals(target, roundtrip)

        readCardsCsvIterator(scratchFile.toString()).use { cardIter ->
            var count = 0
            while (cardIter.hasNext()) {
                val roundtrip = cardIter.next()
                assertEquals(target[count], roundtrip)
                count++
            }
        }

        val zipFile = createZipFile(scratchFile.toString(), delete = true)
        readCardsCsvIterator(zipFile.toString()).use { cardIter ->
            var count = 0
            while (cardIter.hasNext()) {
                val roundtrip = cardIter.next()
                assertEquals(target[count], roundtrip)
                count++
            }
        }

        zipFile.delete()
        scratchFile.delete()
    }

}