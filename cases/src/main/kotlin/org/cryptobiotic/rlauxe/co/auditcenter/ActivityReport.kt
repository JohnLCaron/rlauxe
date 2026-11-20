package org.cryptobiotic.rlauxe.co.auditcenter

import java.io.File
import java.io.IOException

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import kotlin.String

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

        fun readFromFile(filename: String): Map<String, List<ActivityReport>> { // contest name -> mvr
            val result = mutableMapOf<String, MutableList<ActivityReport>>()

            val excelFile = File(filename)
            val formatter = DataFormatter()

            // Passing a File directly uses less memory than FileInputStream
            try {
                WorkbookFactory.create(excelFile).use { workbook ->
                    val contestsToSheets = mutableListOf<Pair<String, String>>()  // contest name, sheet name
                    val sheet: Sheet = workbook.getSheetAt(0) // Contests to Sheets
                    var rowCount = 0
                    for (row in sheet) {
                        val cellValues = row.toList().map{ it.getStringCellValue() }
                        // println(cellValues)
                        if (rowCount > 1 && cellValues.size > 1)
                            contestsToSheets.add(Pair(cellValues[0], cellValues[1]))
                        rowCount++
                    }

                    var firstHeader = emptyList<String>()
                    contestsToSheets.forEach { pair ->
                        val sheetName = if (pair.second == "Governor / Lieutenant Governor") "Governor   Lieutenant Governor" else pair.second // 2026General, sheesh
                        val sheet: Sheet? = workbook.getSheet(sheetName) //sheet name
                        if (sheet != null) {
                            var count = 0
                            val header = sheet.getRow(1).toList().map { it.getStringCellValue() }
                            // println("${pair.first}: $header")
                            val activities = mutableListOf<ActivityReport>()

                            if (firstHeader.isEmpty()) firstHeader = header
                            else if (firstHeader != header) {
                                println(header)
                            }

                            for (row in sheet) {
                                val cellValues = row.toList()
                                // println(cellValues)
                                if (count > 1  && cellValues.size > 1) {
                                    var idx = 0
                                    activities.add(
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
                                        )
                                    )
                                }
                                count++
                            }
                            result[pair.first] = activities

                        } else
                            println("ActivityReport FAILED to find sheet '${sheetName}'")
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