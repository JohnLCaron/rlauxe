package org.cryptobiotic.rlauxe.co.auditcenter

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.poi.openxml4j.util.ZipSecureFile
import java.io.File
import java.io.IOException

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import kotlin.String

private val logger = KotlinLogging.logger("ResultsReport")
private val show = false

// one line for each target mvr ; ~5K

// resultsReport.xlsx
// county, imprinted id, scanner id, batch id, record id, db id, round, audit board, record type, discrepancy, consensus, comment, random number, random number sequence position, multiplicity, time of submission
// county, imprinted id, scanner id, batch id, record id, db id, round, audit board, record type, discrepancy, consensus, comment, revision, re-audit ballot comment, time of submission
data class ResultsReport(
    val county: String,
    val imprintedId: String,
    val scannerId: String,
    val batchId: String,
    val recordId: String,
    val dbId: Int,
    val round: Int,
    val auditBoard: Int,
    val recordType: String,
    val discrepency: String,
    val consensus: String,
    val comment: String,
    val randomNumber: Int,
    val sequence: Int,
    val multiplicity: Int,
    val timeOfSubmission: String,
) {

    // I dont trust that this format wont change
    companion object {

        fun readFromFile(filename: String): Map<String, List<ResultsReport>> { // target contest name -> mvr
            val result = mutableMapOf<String, MutableList<ResultsReport>>()

            // "$ac/2024/general/round3/ResultsReport3.xlsx"
            // org.apache.poi.ooxml.POIXMLException: Zip bomb detected! The file would exceed the max. ratio of compressed file size to the size of the expanded data.
            //This may indicate that the file is used to inflate memory usage and thus could pose a security risk.
            //You can adjust this limit via ZipSecureFile.setMinInflateRatio() if you need to work with files which exceed this limit.
            //Uncompressed size: 206203, Raw/compressed size: 2048, ratio: 0.009932
            //Limits: MIN_INFLATE_RATIO: 0.010000, Entry: xl/styles.xml
            ZipSecureFile.setMinInflateRatio(.001)

            val excelFile = File(filename)
            val formatter = DataFormatter()

            // Passing a File directly uses less memory than FileInputStream
            try {
                WorkbookFactory.create(excelFile).use { workbook ->
                    var firstHeader = emptyList<String>()
                    val contestNames = mutableListOf<String>()
                    var sheetno = 0

                    for (sheet in workbook) {
                        if (sheet.sheetName.startsWith("Contests to Sheets")) {
                            val rows = sheet.iterator()
                            rows.next() // skip first row - its the header
                            while( rows.hasNext() ) {
                                val row = rows.next()
                                if (row.first().getStringCellValue().startsWith("Contest Name")) continue
                                contestNames.add(row.first().getStringCellValue())
                            }
                            continue
                        }
                        if (sheet.sheetName.startsWith("Summary")) continue

                        var rowno = 0
                        // find the header
                        while (true) {
                            val cellValues = sheet.getRow(rowno).toList().map { it.getStringCellValue() }
                            if (cellValues.first().lowercase() == "county") break
                            rowno++
                        }
                        val header = sheet.getRow(rowno).toList().map { it.getStringCellValue() }
                        if (firstHeader.isEmpty()) firstHeader = header
                        else if (firstHeader != header) {
                            logger.warn { " changed = $header was ${firstHeader}" }
                        }
                        rowno++ // skip the header

                        val results = mutableListOf<ResultsReport>()
                        while (rowno < sheet.getLastRowNum()) {
                            val cellValues = sheet.getRow(rowno).toList()
                            // println(cellValues)
                            if (cellValues.size >= 15) {
                                var idx = 0
                                try {
                                    results.add(
                                        ResultsReport(
                                            county = cellValues[idx++].getStringCellValue(),
                                            imprintedId = cellValues[idx++].getStringCellValue(),
                                            scannerId = cellValues[idx++].getStringCellValue(),
                                            batchId = cellValues[idx++].getStringCellValue(),
                                            recordId = cellValues[idx++].getStringCellValue(),
                                            dbId = cellValues[idx++].getStringCellValue().trim().toInt(),
                                            round = cellValues[idx++].getStringCellValue().trim().toInt(),
                                            auditBoard = cellValues[idx++].getStringCellValue().trim().toInt(),
                                            recordType = cellValues[idx++].getStringCellValue(),
                                            discrepency = removeNoData(cellValues[idx++].getStringCellValue()),
                                            consensus = removeNoData(cellValues[idx++].getStringCellValue()),
                                            comment = removeNoData(cellValues[idx++].getStringCellValue()),
                                            randomNumber = cellValues[idx++].getStringCellValue().trim().toInt(),
                                            sequence = cellValues[idx++].getStringCellValue().trim().toInt(),
                                            multiplicity = cellValues[idx++].getStringCellValue().trim().toInt(),
                                            timeOfSubmission = cellValues[idx++].getStringCellValue(),
                                        ))
                                    } catch (e: Exception) {
                                        println(cellValues)
                                    }
                            }
                            rowno++
                        }
                        result[contestNames[sheetno]] = results
                        if (show) println("ResultsReport sheet '${sheet.sheetName}' with ${results.size} results for contest '${contestNames[sheetno]}'")
                        sheetno++
                    }
                }

            } catch (e: IOException) {
                e.printStackTrace()
            }

            return result
        }

        fun removeNoData(s:String) = if (s == "No Data") "" else s.trim()
    }


}