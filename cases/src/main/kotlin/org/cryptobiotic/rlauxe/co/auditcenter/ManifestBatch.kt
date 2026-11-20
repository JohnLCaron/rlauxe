package org.cryptobiotic.rlauxe.co.auditcenter

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.cryptobiotic.rlauxe.corlaInput.defaultCharset
import java.io.File

// County,Tabulator ID,Batch #,# of Ballots Cards,Location
//  Morgan,102,1,50,Box 1
//  Morgan,102,2,50,Box 1
data class ManifestBatch(
    val countyName: String,
    val tabulatorNum: Int,
    val batchId: String,
    val nballotCards: Int,
    val location: String,
) {

    companion object {

        fun readFromFile(filename: String): List<ManifestBatch> {
            val results = mutableListOf<ManifestBatch>()

            val file = File(filename)
            val format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true) // Automatically trims leading and trailing spaces
                .get()

            CSVParser.parse(file, defaultCharset, format).use { parser ->
                for (record in parser) {
                    val contest = ManifestBatch(
                        countyName=record.get(0),
                        tabulatorNum=record.get(1).toInt(),
                        batchId=record.get(2),
                        nballotCards=record.get(3).toInt(),
                        location=record.get(4),
                    )
                    results.add(contest)
                }
            }
            return results
        }
    }
}