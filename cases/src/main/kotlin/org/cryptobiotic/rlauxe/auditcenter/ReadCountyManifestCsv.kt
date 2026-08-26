package org.cryptobiotic.rlauxe.auditcenter

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.File
import java.nio.charset.Charset
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries

// County,Tabulator ID,Batch #,# of Ballots Cards,Location
//Morgan,102,1,50,Box 1
//Morgan,102,2,50,Box 1
//Morgan,102,3,13,Box 1
//Morgan,102,4,50,Box 1
//Morgan,102,5,50,Box 1
//Morgan,102,6,50,Box 1
// ...

private val logger = KotlinLogging.logger("ReadCountyManifestCsv")

data class ManifestBatch(
    val countyName: String,
    val tabulatorId: Int,
    val batch: String,
    val nballotCards: Int,
    val location: String,
)

fun readCountyManifestCsv(filename: String): List<ManifestBatch> {
    val file = File(filename)
    val parser = CSVParser.parse(file, Charset.forName("ISO-8859-1"), CSVFormat.DEFAULT) // TODO
    val records = parser.iterator()

    // we expect the first line to be the headers
    val headerRecord = records.next()
    val header = headerRecord.toList().joinToString(", ")
    // println("readContestComparisonCsv from $filename")

    val batches = mutableListOf<ManifestBatch>()
    var count = 0
    var line: CSVRecord? = null
    try {
        while (records.hasNext()) {
            line = records.next()!!
            val line = ManifestBatch(
                line.get(0).trim(),
                line.get(1).trim().toInt(),
                line.get(2).trim(),
                line.get(3).trim().toInt(),
                line.get(4).trim(), // choice_per_voting_computer
                )
            batches.add(line)
            count++
        }
    } catch (ex: Exception) {
        logger.error(ex) { "line = '$line' file = $filename" }
    }
    return batches
}

fun readAuditcenterManifests(dir: String, counties: Set<String>): Map<String, List<ManifestBatch>> {
    val path = Path(dir) // or does votedatabase include

    val countyManifests = mutableListOf<Pair<String, List<ManifestBatch>>>()
    path.listDirectoryEntries().sorted().filter { it.fileName.toString().endsWith(".csv")}.forEach { subdir ->
        var countyFilename = subdir.fileName.toString().split(".")[0]
        if (counties.contains(mungeCountyName(countyFilename))) {
            countyManifests.add(Pair(mungeCountyName(countyFilename), readCountyManifestCsv(subdir.toString())))
        } else {
            println("${subdir.fileName} not in county list")
        }
    }
    return countyManifests.toMap()
}

fun mungeCountyName(countyFilename: String): String {
    if (countyFilename == "ClearCreek") return "Clear Creek"
    if (countyFilename == "ElPaso") return "El Paso"
    if (countyFilename == "KitCarson") return "Kit Carson"
    if (countyFilename == "LaPlata") return "La Plata"
    if (countyFilename == "LasAnimas") return "Las Animas"
    if (countyFilename == "RioBlanco") return "Rio Blanco"
    if (countyFilename == "RioGrande") return "Rio Grande"
    if (countyFilename == "SanMiguel") return "San Miguel"
    return countyFilename
}
