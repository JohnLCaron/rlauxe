package org.cryptobiotic.rlauxe.co.auditcenter

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.io.IOException

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import kotlin.String

private val logger = KotlinLogging.logger("ActivityReport")
private val show = false

// one line for each target mvr ; ~5K

// activityReport.xlsx
// county, imprinted id, scanner id, batch id, record id, db id, round, audit board, record type, discrepancy, consensus, comment, revision, re-audit ballot comment, time of submission
data class ActivityReport(
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
    val revision: String,
    val reauditComment: String,
    val timeOfSubmission: String,
) {

    companion object {

        fun readFromFile(filename: String): Map<String, List<ActivityReport>> { // target contest name -> mvr
            val result = mutableMapOf<String, MutableList<ActivityReport>>()

            val excelFile = File(filename)
            val formatter = DataFormatter()

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

                        val results = mutableListOf<ActivityReport>()
                        while (rowno < sheet.getLastRowNum()) {
                            val cellValues = sheet.getRow(rowno).toList()
                            // println(cellValues)
                            if (cellValues.size >= 15) {
                                var idx = 0
                                try {
                                    results.add(
                                        ActivityReport(
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
                                            revision = removeNoData(cellValues[idx++].getStringCellValue()),
                                            reauditComment = removeNoData(cellValues[idx++].getStringCellValue()),
                                            timeOfSubmission = cellValues[idx++].getStringCellValue(),
                                        ))
                                } catch (e: Exception) {
                                    println(cellValues)
                                }
                            }
                            rowno++
                        }
                        result[contestNames[sheetno]] = results
                        if (show) println("ActivityReport sheet '${sheet.sheetName}' with ${results.size} results for contest '${contestNames[sheetno]}'")
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