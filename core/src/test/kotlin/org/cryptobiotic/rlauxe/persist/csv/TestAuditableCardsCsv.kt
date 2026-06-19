package org.cryptobiotic.rlauxe.persist.csv

import kotlin.test.Test
import kotlin.test.assertEquals

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.util.createZipFile

class TestAuditableCardsCsv {

    @Test
    fun testRoundtrip() {
        val target = AuditableCard.fromVotes (
            "id to find card",
            "location to find card",
            42,
            43L,
            true,
            "pool11",
            11,
            votes=mapOf(19 to intArrayOf(1,2,3), 23 to intArrayOf(), 99 to intArrayOf(1,2,3,4,5,6,7,8,9,0), 123456 to intArrayOf(23498724)),
        )

        val csv = writeCardCsv(target)
        print(CardHeader)
        println(csv)

        val roundtrip = readCardCsvM(csv)
        assertEquals(target, roundtrip)
    }

    @Test
    fun testRoundtripNoVotes() {
        val target = AuditableCard.fromVotes (
            "deets",
            "dots",
            42,
            43L,
            false,
            styleName = "all",
            null,
            null,
        )

        val csv = writeCardCsv(target)
        print(CardHeader)
        println(csv)

        val roundtrip = readCardCsvM(csv)
        assertEquals(target, roundtrip)
    }

    @Test
    fun testRoundtripIO() {
        val target = listOf(
            AuditableCard.fromVotes ("deets", "dots", 42, 43L, false, "pool111", 111, null),
            AuditableCard.fromVotes ("deeks","docs",  42, 43L, false, styleName="all", null, null),
            AuditableCard.fromVotes ("id", "info to find card", 42, 43L, true,
                votes=mapOf(19 to intArrayOf(1,2,3), 23 to intArrayOf(), 99 to intArrayOf(1,2,3,4,5,6,7,8,9,0), 123456 to intArrayOf(23498724)), poolId=11, styleName="pool11"),
            AuditableCard.fromVotes ("id1", "info2 to find card", 42, 43L, true,
                votes=mapOf(19 to intArrayOf(1,2,3), 23 to intArrayOf(), 99 to intArrayOf(1,2,3,4,5,6,7,8,9,0), 123456 to intArrayOf(23498724)), poolId=null, styleName="cvr"),
        )

        val scratchFile = kotlin.io.path.createTempFile().toFile()
        writeCardCsvFile(target, scratchFile.toString())

        val roundtrip = readCardsAndMergeToList(scratchFile.toString(), null)
        assertEquals(target, roundtrip)

        readCardsCsvIteratorM(scratchFile.toString(), null).use { cardIter ->
            var count = 0
            while (cardIter.hasNext()) {
                val roundtrip = cardIter.next()
                assertEquals(target[count], roundtrip)
                count++
            }
        }

        val zipFile = createZipFile(scratchFile.toString(), delete = true)
        readCardsCsvIteratorM(zipFile.toString(), null).use { cardIter ->
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