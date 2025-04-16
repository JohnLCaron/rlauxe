package org.cryptobiotic.rlauxe.dominion

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.File
import java.nio.charset.Charset

data class DominionBallotManifest(
    val countyId: Long,
    val batches: List<DominionBallotBatch>,
    val header: String,
    val filename: String,
) {
    val nballots = batches.sumOf { it.batchSize }
}

// Tray #,Tabulator Number,Batch Number,Total Ballots,VBMCart.Cart number
//1,99808,78,116,3
data class DominionBallotBatch(
    val trayNumber: Int,
    val tabulator: Int,
    val batchNumber: Int,
    val batchSize: Int,
    val cartNumber: Int,
    val sequenceStart: Int, // The first sequence number (of all ballots) in this batch
)

fun readDominionBallotManifest(filename: String, countyId: Long): DominionBallotManifest {
    val parser = CSVParser.parse(File(filename), Charset.forName("ISO-8859-1"), CSVFormat.DEFAULT)
    val records = parser.iterator()

    var sequenceStart = 0

    // we expect the first line to be the headers
    val headerRecord = records.next()
    val header = headerRecord.toList().joinToString(", ")
    // println(header)
    require("Tray #, Tabulator Number, Batch Number, Total Ballots, VBMCart.Cart number".equals(header))

    // subsequent lines contain ballot manifest info
    val batches = mutableListOf<DominionBallotBatch>()

    while (records.hasNext()) {
        val line = records.next()
        val bmi = DominionBallotBatch(
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

    return DominionBallotManifest(countyId, batches, header, filename)
}