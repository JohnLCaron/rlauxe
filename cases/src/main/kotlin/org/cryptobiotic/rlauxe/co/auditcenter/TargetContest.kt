package org.cryptobiotic.rlauxe.co.auditcenter

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.IOException

// targetContests.csv; 2026GeneralRLAContestList
// // "Targeted Contests",,,,,,,,,,,,,,,,,,,,,,,,,,
//// "County","Contest","Vote For","Lowest Winner","Highest Loser","Contest Margin","Diluted Margin","Risk Limit","Estimated # of CVRs to audit","# of CVRs","Remarks",,,,,,,,,,,,,,,,
data class TargetContest(
    val countyName: String,
    val contestName: String,
    val nwinners: Int,
    val lowestWinner: Int,
    val highestLoser: Int,
    val voteMargin: Int,
    val dilutedMargin: Double,
    val riskLimit: Double,
    val estimatedSamplesToAudit: Int, // early estimate - do not use
    val numberOfCvrs: Int,
    val remarks: String, // "Single County Contest", "Audited in all 64 Counties"
) {

    companion object {

        fun readFromFile(filename: String): List<TargetContest> { // county name -> mvr
            val result = mutableListOf<TargetContest>()

            val excelFile = File(filename)
            try {
                WorkbookFactory.create(excelFile).use { workbook ->
                    val sheet: Sheet = workbook.getSheetAt(0)
                    var count = 0
                    for (row in sheet) {
                        val cellValues: List<Cell> = row.toList()
                        // println(cellValues)
                        if (count > 0 && cellValues.size > 10 && !cellValues.first().getStringCellValue().startsWith("*")) {
                            var idx = 0
                            result.add(
                                TargetContest(
                                    countyName = cellValues[idx++].getStringCellValue().trim(),
                                    contestName = cellValues[idx++].getStringCellValue().trim(),
                                    nwinners = cellValues[idx++].getNumericCellValue().toInt(),
                                    lowestWinner = cellValues[idx++].getNumericCellValue().toInt(),
                                    highestLoser = cellValues[idx++].getNumericCellValue().toInt(),
                                    voteMargin = cellValues[idx++].getNumericCellValue().toInt(),
                                    dilutedMargin = cellValues[idx++].getNumericCellValue(),
                                    riskLimit = cellValues[idx++].getNumericCellValue(),
                                    estimatedSamplesToAudit = cellValues[idx++].getNumericCellValue().toInt(),
                                    numberOfCvrs = cellValues[idx++].getNumericCellValue().toInt(),
                                    remarks = clean(cellValues[idx++].getStringCellValue()).trim(),
                                )
                            )
                        }
                        count++
                    }
                }

            } catch (e: IOException) {
                e.printStackTrace()
            }

            return result
        }

    }
}

fun clean(s: String): String {
    return s.trim().replace("\"", "").replace(",", "").replace("\n", " ").replace("%", "")
}