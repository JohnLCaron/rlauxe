package org.cryptobiotic.rlauxe.csv

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class ColoradoBallotManifest(
    val countyId: Long,
    val batches: List<ColoradoBallotBatch>,
    val header: String,
    val filename: String,
) {
    val nballots = batches.sumOf { it.batchSize }
}

// Tray #,Tabulator Number,Batch Number,Total Ballots,VBMCart.Cart number
//1,99808,78,116,3
data class ColoradoBallotBatch(
    val trayNumber: Int,
    val tabulator: Int,
    val batchNumber: Int,
    val batchSize: Int,
    val cartNumber: Int,
    val sequenceStart: Int, // The first sequence number (of all ballots) in this batch
)

fun readColoradoBallotManifest(filename: String, countyId: Long): ColoradoBallotManifest {
    val path: Path = Paths.get(filename)
    val reader: Reader = Files.newBufferedReader(path)
    val parser = CSVParser(reader, CSVFormat.DEFAULT)

    val records = parser.iterator()

    var sequenceStart = 0

    // we expect the first line to be the headers
    val headerRecord = records.next()
    val header = headerRecord.toList().joinToString(", ")
    println(header)
    require("Tray #, Tabulator Number, Batch Number, Total Ballots, VBMCart.Cart number".equals(header))

    // subsequent lines contain ballot manifest info
    val batches = mutableListOf<ColoradoBallotBatch>()

    while (records.hasNext()) {
        val line = records.next()
        val bmi = ColoradoBallotBatch(
            line.get(0).toInt(),
            line.get(1).toInt(),
            line.get(2).toInt(),
            line.get(3).toInt(),
            line.get(4).toInt(),
            sequenceStart,
        )
        batches.add(bmi)
        sequenceStart += bmi.batchSize
    }

    return ColoradoBallotManifest(countyId, batches, header, filename)
}