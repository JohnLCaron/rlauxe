package org.cryptobiotic.rlauxe.co.auditcenter

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.cryptobiotic.rlauxe.auditcenter.getAuditReason
import org.cryptobiotic.rlauxe.corlaInput.defaultCharset
import org.cryptobiotic.rlauxe.corlacvr.reverseMungedDate
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.trunc
import java.io.File
import kotlin.text.appendLine

// one line for each target mvr and contest on the mvr; ~200K
// TODO test if stream is faster? avoild associate lookup ??

// auditcenter 2020 has no "audit reason"
// county_name,contest_name,imprinted_id,ballot_type,choice_per_voting_computer,audit_board_selection,consensus,record_type,audit_board_comment,timestamp,cvr_id
// county_name,contest_name,imprinted_id,ballot_type,choice_per_voting_computer,audit_board_selection,consensus,record_type,audit_board_comment,timestamp,cvr_id,audit_reason

// TODO are these names canonical ??
data class MvrComparison(
    val county_name: String,
    val contest_name: String,
    val imprinted_id: String,
    val ballot_type: String,
    val choice_per_voting_computer: String,
    val audit_board_selection: String,
    val consensus: String,  // ?? Always YES I think ??
    val record_type: String,
    val audit_board_comment: String,
    val timestamp: String,
    val cvr_id: Int, // apparently the opaque "db creation id"
    val audit_reason: String?, // 2020 didnt have this field, but 2026 does; apparently its only on some of the ballots ???? I guess its unreliable
) {

    companion object {
        val countyWidth = -11
        val contestWidth = -66
        val showHeader = "margin, ${trunc("contest_name", contestWidth)}, ids"
        val charsToRemove = setOf('[', ']')

        fun readFromFile(filename: String): List<MvrComparison> {
            val results = mutableListOf<MvrComparison>()

            val file = File(filename)
            val format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true) // Automatically trims leading and trailing spaces
                .get()

            CSVParser.parse(file, defaultCharset, format).use { parser ->
                for (record in parser) {
                    val comparison = MvrComparison(
                        county_name = record.get("county_name"),
                        contest_name = record.get("contest_name"),
                        imprinted_id = reverseMungedDate(record.get("imprinted_id")),
                        ballot_type = record.get("ballot_type"),
                        choice_per_voting_computer = record.get("choice_per_voting_computer"),
                        audit_board_selection = record.get("audit_board_selection"),
                        consensus = record.get("consensus"),
                        record_type = record.get("record_type"),
                        audit_board_comment = record.get("audit_board_comment"),
                        timestamp = record.get("timestamp"),
                        cvr_id = record.get("cvr_id").toInt(),
                        audit_reason = if (record.isMapped("audit_reason")) record.get("audit_reason") else null,
                    )
                    // println("   $comparison")
                    results.add(comparison)
                }
            }
            return results
        }

        // TODO are these used ??
        fun mvrByCountyAndContest(lines: List<MvrComparison>): Map<String, Map<String, List<MvrComparison>>> { // county -> contest -> lines
            val results = mutableMapOf<String, MutableMap<String, MutableList<MvrComparison>>>()
            lines.forEach { line ->
                val countyMap = results.getOrPut(line.county_name) { mutableMapOf() }
                val contestList = countyMap.getOrPut(line.contest_name) { mutableListOf() }
                contestList.add(line)
            }
            return results
        }

        fun mvrByContestAndCounties(lines: List<MvrComparison>): Map<String, Map<String, List<MvrComparison>>> { // contest -> county -> lines
            val results = mutableMapOf<String, MutableMap<String, MutableList<MvrComparison>>>()
            lines.forEach { line ->
                val contestMap = results.getOrPut(line.contest_name) { mutableMapOf() }
                val countyList = contestMap.getOrPut(line.county_name) { mutableListOf() }
                countyList.add(line)
            }
            return results
        }

        fun mvrs(lines: List<MvrComparison>): Map<String, Map<String, List<MvrComparison>>> { // county -> imprintedId -> lines
            val results = mutableMapOf<String, MutableMap<String, MutableList<MvrComparison>>>()
            lines.forEach { line ->
                val countyMap = results.getOrPut(line.county_name) { mutableMapOf() }
                val contestList = countyMap.getOrPut(line.contest_name) { mutableListOf() }
                contestList.add(line)
            }
            return results
        }
    }
}
