package org.cryptobiotic.rlauxe.persist.csv

import kotlin.test.Test
import kotlin.test.assertEquals

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.CvrExport
import org.cryptobiotic.rlauxe.util.createZipFile

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

        val tempFile = kotlin.io.path.createTempFile().toString()
        writeCvrExportCsvFile(listOf(target).iterator(), tempFile)

        cvrExportCsvIterator(tempFile).use { csvIter ->
            while (csvIter.hasNext()) {
                val roundtrip = csvIter.next()
                assertEquals(target, roundtrip)
            }
        }

        val zipFile = createZipFile(tempFile, delete = true)
        cvrExportCsvIterator(zipFile.toString()).use { csvIter ->
            while (csvIter.hasNext()) {
                val roundtrip = csvIter.next()
                assertEquals(target, roundtrip)
            }
        }
        val ok = zipFile.delete()
        println("delete file $zipFile was successful = $ok")
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

}