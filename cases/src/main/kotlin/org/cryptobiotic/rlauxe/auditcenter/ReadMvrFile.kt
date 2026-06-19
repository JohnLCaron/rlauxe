package org.cryptobiotic.rlauxe.auditcenter

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.File
import java.nio.charset.Charset
import kotlin.text.appendLine

// auditcenter 2020 has no "audit reason"
// county_name,contest_name,imprinted_id,ballot_type,choice_per_voting_computer,audit_board_selection,consensus,record_type,audit_board_comment,timestamp,cvr_id
// county_name,contest_name,imprinted_id,ballot_type,choice_per_voting_computer,audit_board_selection,consensus,record_type,audit_board_comment,timestamp,cvr_id,audit_reason


// auditcenter 2020
// county_name,contest_name,imprinted_id,ballot_type,choice_per_voting_computer,audit_board_selection,consensus,record_type,audit_board_comment,timestamp,cvr_id
// Adams,Adams County Ballot Issue 1A,3-69-79,Type 34,"""Yes/For""","""Yes/For""",YES,uploaded,"",2020-11-17 13:41:22.174,3762242

// cases/src/test/data/corla/2024audit/round1/contestComparison.csv
// county_name,contest_name,imprinted_id,ballot_type,choice_per_voting_computer,audit_board_selection,consensus,record_type,audit_board_comment,timestamp,cvr_id,audit_reason
// Adams,17th Judicial District Ballot Question 7B,101-101-7,52,"""Yes/For""","""Yes/For""",YES,uploaded,"",2024-11-19 09:44:18.62646,178977,
// Adams,17th Judicial District Ballot Question 7B,101-130-14,14,"""Yes/For""","""Yes/For""",YES,uploaded,"",2024-11-19 09:49:44.148182,240137,
// Adams,17th Judicial District Ballot Question 7B,101-146-54,65,"""No/Against""","""No/Against""",YES,uploaded,"",2024-11-19 09:54:41.65526,250284,
// Adams,17th Judicial District Ballot Question 7B,101-285-1,16,"""No/Against""","""No/Against""",YES,uploaded,"",2024-11-19 10:02:29.59411,379206,

data class CountyStylesFromMvrs(val countyName: String) {
    val styles = mutableMapOf<Set<String>, MvrStyle>()
    var cardCount = 0

    fun add(contests:Set<String>) {
        val style = styles.getOrPut(contests) { MvrStyle(styles.size, contests) }
        style.cardCount++
        cardCount++
    }

    override fun toString()= buildString {
        append("'$countyName' has ${styles.size} styles cardCount=$cardCount")
    }

    fun show()= buildString {
        appendLine("'$countyName' has ${styles.size} styles cardCount=$cardCount")
        styles.values.forEach { appendLine("  $it")}
    }
}

data class MvrStyle(val id: Int, val contests: Set<String>) {
    var cardCount = 0
    override fun toString()= buildString {
        append("style $id has ${contests.size} contests cardCount=$cardCount")
    }

    fun show(contestNameToId: Map<String, Int>, sort:Boolean = true): String {
        val contestIds = contests.map { contestNameToId[it]!! }
        val useIds = if (sort) contestIds.sorted() else contestIds
        return "  MvrStyle(${id}, contests=${useIds}, count= ${cardCount}"
    }

}

///////////////////////////////////////////////////////////////

// for each contest, total mvrs over all counties
data class ContestMvrCount(val contestName: String) {
    var countMvr = 0
    var countStatewide = 0
}

// for each county, over all contests
data class CountyMvrCount(val countyName: String) {
    var countMvr = 0
}

///////////////////////////////////////////

data class Card(val cvrId: Int) {
    val lines = mutableListOf<ComparisonLine>()

    fun add(line: ComparisonLine) {
        lines.add(line)
        if (!validate()) {
            println("bad $line")
        }
    }

    fun validate(): Boolean {
        val firstLine = lines.first()
        lines.forEach {
            if (it.imprintedId != firstLine.imprintedId) return false
            if (it.countyName != firstLine.countyName) return false
            if (it.ballotType != firstLine.ballotType) return false
            if (it.cvrId != firstLine.cvrId) return false
        }
        return true
    }

    fun county() = lines.first().countyName

    // they only mark the statewide contests "statewide", but the entire ballot must have come from the statewide sampling
    fun statewide() = lines.any { it.statewide }

    fun contests() : Set<String> {
        val contests = mutableSetOf<String>()
        lines.forEach { contests.add(it.contestName) }
        return contests
    }
}

data class ComparisonLine(
    val countyName: String,
    val contestName: String,
    val imprintedId: String,
    val ballotType: String,
    val cvrChoice: String,
    val mvrChoice: String,
    val cvrId: Int,
    val statewide: Boolean,
)

fun readContestComparisonCsv(filename: String): CardComparisonResults {
    val file = File(filename)
    val parser = CSVParser.parse(file, Charset.forName("ISO-8859-1"), CSVFormat.DEFAULT) // TODO
    val records = parser.iterator()

    // we expect the first line to be the headers
    val headerRecord = records.next()
    val header = headerRecord.toList().joinToString(", ")
    // println("readContestComparisonCsv from $filename")

    val cards = mutableMapOf<Int, Card>()
    var  count = 0
    var line: CSVRecord? = null
    try {
        while (records.hasNext()) {
            line = records.next()!!
            try {
                // 0 county_name,contest_name,imprinted_id,ballot_type, choice_per_voting_computer,audit_board_selection,
                // 6 consensus,record_type,audit_board_comment,timestamp,cvr_id,
                // 11 audit_reason (optional)
                val compareLine = ComparisonLine(
                    line.get(0).trim(),
                    line.get(1).trim(),
                    line.get(2).trim(),
                    line.get(3).trim(),
                    line.get(4).trim(),
                    line.get(5).trim(),
                    line.get(10).toInt(),
                    if (line.size() > 11) (line.get(11).trim() == "STATE_WIDE_CONTEST") else false,
                    )
                val card = cards.getOrPut(compareLine.cvrId) { Card(compareLine.cvrId) }
                card.add(compareLine)
                count++

            } catch (e: Exception) {
                println(line)
                println(line.size())
                e.printStackTrace()
            }
        }
    } catch (ex: Exception) {
        println(line)
        ex.printStackTrace()
    }
    cards.values.forEach { card: Card -> card.validate() }
    // println("statewide cards ${cards.values.count { it.statewide() } } total ${cards.size}")

    // accumulate mvr counts by County, skip statewide
    val countyMvrs = mutableMapOf<String, CountyMvrCount>()
    // cards.values.filter { !it.statewide() }.forEach { card: Card ->
    cards.values.forEach { card: Card ->
        val line = card.lines.first()
        val countyMvrs = countyMvrs.getOrPut(line.countyName) { CountyMvrCount(line.countyName) }
        countyMvrs.countMvr++
    }

    // accumulate mvr counts by Contest
    val contestMvrs = mutableMapOf<String, ContestMvrCount>()
    cards.values.forEach { card: Card ->
        card.lines.forEach { line ->
            val contestMvr = contestMvrs.getOrPut(line.contestName) { ContestMvrCount(line.contestName) }
            if (card.statewide()) contestMvr.countStatewide++
                                  else contestMvr.countMvr++
        }
    }

    // create the CountyStyles
    val stylesByCounty = mutableMapOf<String, CountyStylesFromMvrs>()
    cards.values.forEach { card: Card ->
        val countyStyles = stylesByCounty.getOrPut(card.county()) { CountyStylesFromMvrs(card.county()) }
        countyStyles.add(card.contests())
    }

    return CardComparisonResults(contestMvrs.values.toList(), countyMvrs.values.toList(), stylesByCounty.values.toList())
}

data class CardComparisonResults(
    val contestMvrs: List<ContestMvrCount>,
    val countyMvrs: List<CountyMvrCount>,
    val stylesByCounty: List<CountyStylesFromMvrs>
)
