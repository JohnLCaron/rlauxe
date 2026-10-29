package org.cryptobiotic.rlauxe.util

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

// name,filename,type,approx_count,size,status,timestamp,computed_hash,submitted_hash
// name,filename,type,approx_count,size,status,timestamp,computed_hash,submitted_hash
//Phillips,CVR_Export_20251113104134.csv,manifest,1344,122251,FAILED,2025-11-13 11:37:15.550688,E2A147F186657EF952C5A9198696505CABC7A1EAC75E7662C85863EFCE076CC2,E2A147F186657EF952C5A9198696505CABC7A1EAC75E7662C85863EFCE076CC2
//Arapahoe,Modified_CVR_Export_20251113144743.csv,manifest,174303,36186451,FAILED,2025-11-13 15:36:27.380832,4EAF3AC21EFD2D5695D91BDBAF9D99529CAF26D8B48620C0678471713D7ADC49,4EAF3AC21EFD2D5695D91BDBAF9D99529CAF26D8B48620C0678471713D7ADC49

data class UploadStatus(
    val name: String,
    val filename: String,
    val type:String,
    val approx_count: Int,
    val size:Int,
    val status:String,
    val timestamp: String,
    val computed_hash: String,
    val submitted_hash: String,
) {
    companion object {
        fun readFromFile(filename: String): List<UploadStatus> {
            var result: List<UploadStatus> = emptyList()
            val reader = csvReader()
            reader.readFromFile(filename) { rows ->
                result = rows.withHeader().map { row ->
                    UploadStatus(
                        name = row["name"]!!.trim(),
                        filename = row["filename"]!!.trim(),
                        type = row["type"]!!.trim(),
                        approx_count = row["approx_count"]!!.trim().toInt(),
                        size = row["size"]!!.trim().toInt(),
                        status = row["status"]!!.trim(),
                        timestamp = row["timestamp"]!!.trim(),
                        computed_hash = row["computed_hash"]!!.trim(),
                        submitted_hash = row["submitted_hash"]!!.trim(),
                    )
                }.toList()
            }
            return result
        }
    }
}

