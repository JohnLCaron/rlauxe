package org.cryptobiotic.rlauxe.co.auditcenter

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.cryptobiotic.rlauxe.corlaInput.defaultCharset
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.trunc
import java.io.File

// one line for each contest ~ 1K

// roundX/contestSelection.csv
// min_margin,contest_name,contest_cvr_ids
data class ContestSelection(
    val min_margin: Int,
    val contest_name:String,
    val contest_cvr_ids: String,
) {
    val dbList: List<Int>

    init {
        val clean = contest_cvr_ids.filterNot { it in charsToRemove }
        dbList = clean.split(",").filter { it.isNotEmpty() }.map { it.toInt() }
    }

    fun show() = buildString {
        append("${nfn(min_margin,6)}, ${trunc(contest_name,contestWidth)}, ${nfn(dbList.size,3)}")
    }

    companion object {
        val contestWidth = -66
        val showHeader = "margin, ${trunc("contest_name", contestWidth)}, ids"
        val charsToRemove = setOf('[',']')

        fun readFromFile(filename: String): Map<String, ContestSelection> {
            val results = mutableListOf<ContestSelection>()

            val file = File(filename)
            val format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true) // Automatically trims leading and trailing spaces
                .get()

            CSVParser.parse(file, defaultCharset, format).use { parser ->
                for (record in parser) {
                    val contest = ContestSelection(
                        min_margin=record.get("min_margin").toInt(),
                        contest_name=record.get("contest_name"),
                        contest_cvr_ids=record.get("contest_cvr_ids"),
                    )
                    // println("   $contest")
                    if (contest.dbList.isNotEmpty())
                        results.add(contest)
                }
            }
            return results.associateBy { it.contest_name }.toSortedMap()
        }
    }
}