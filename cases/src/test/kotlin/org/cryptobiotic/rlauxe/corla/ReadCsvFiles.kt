package org.cryptobiotic.rlauxe.corla

import com.jsoizo.kotlincsv.csvReader
import com.jsoizo.kotlincsv.reader.readFromFile
import com.jsoizo.kotlincsv.reader.withHeader

// 2026/primary/finalReports/CountyBallotManifestToCVRcomparison.csv
// county_name,scanner_id,batch_id,count_per_manifest,count_per_cvr_file,difference
data class ManifestCompare(
    val countyName: String,
    val scannerId: Int,
    val batchId: String,
    val countPerManifest: Int,
    val countPerCvrFile: Int,
    val difference: Int,
) {

    companion object {
        fun readFromFile(filename: String): List<ManifestCompare> {
            var result: List<ManifestCompare> = emptyList()
            val reader = csvReader()
            reader.readFromFile(filename) { rows ->
                result = rows.withHeader().map { row ->
                    ManifestCompare(
                        countyName = row["county_name"]!!,
                        scannerId = row["scanner_id"]!!.toInt(),
                        batchId = row["batch_id"]!!,
                        countPerManifest = row["count_per_manifest"]!!.toInt(),
                        countPerCvrFile = row["count_per_cvr_file"]!!.toInt(),
                        difference = row["difference"]!!.toInt(),
                    )
                }.toList()
            }
            return result
        }
    }
}

