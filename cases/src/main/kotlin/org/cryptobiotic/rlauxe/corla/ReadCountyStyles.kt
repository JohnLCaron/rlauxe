package org.cryptobiotic.rlauxe.corla

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.File
import java.nio.charset.Charset
import kotlin.text.appendLine

// cases/src/test/data/corla/2024audit/round1/contestComparison.csv
// county_name,contest_name,imprinted_id,ballot_type,choice_per_voting_computer,audit_board_selection,consensus,record_type,audit_board_comment,timestamp,cvr_id,audit_reason
// Adams,17th Judicial District Ballot Question 7B,101-101-7,52,"""Yes/For""","""Yes/For""",YES,uploaded,"",2024-11-19 09:44:18.62646,178977,
// Adams,17th Judicial District Ballot Question 7B,101-130-14,14,"""Yes/For""","""Yes/For""",YES,uploaded,"",2024-11-19 09:49:44.148182,240137,
// Adams,17th Judicial District Ballot Question 7B,101-146-54,65,"""No/Against""","""No/Against""",YES,uploaded,"",2024-11-19 09:54:41.65526,250284,
// Adams,17th Judicial District Ballot Question 7B,101-285-1,16,"""No/Against""","""No/Against""",YES,uploaded,"",2024-11-19 10:02:29.59411,379206,

data class CountyStyles(val countyName: String) {
    val styles = mutableMapOf<Set<String>, Style>()
    var cardCount = 0

    fun add(contests:Set<String>) {
        val style = styles.getOrPut(contests) { Style(styles.size, contests) }
        style.cardCount++
        cardCount++
    }

    override fun toString()= buildString {
        append("'$countyName' has ${styles.size} styles cardCount=$cardCount")
    }

    fun show()= buildString {
        append("'$countyName' has ${styles.size} styles cardCount=$cardCount")
        styles.values.forEach { appendLine("  $it")}
    }
}

data class Style(val id: Int, val contests: Set<String>) {
    var cardCount = 0
    override fun toString()= buildString {
        append("style $id has ${contests.size} contests cardCount=$cardCount")
    }
}

///////////////////////////////////////////////////////////////

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
)

fun readContestComparisonCsv(filename: String): List<CountyStyles> {
    val file = File(filename)
    val parser = CSVParser.parse(file, Charset.forName("ISO-8859-1"), CSVFormat.DEFAULT) // TODO
    val records = parser.iterator()

    // we expect the first line to be the headers
    val headerRecord = records.next()
    val header = headerRecord.toList().joinToString(", ")
    println("readContestComparisonCsv $header")

    val cards = mutableMapOf<Int, Card>()
    var  count = 0
    var line: CSVRecord? = null
    try {
        while (records.hasNext()) {
            line = records.next()!!
            try {
                // 0 county_name,contest_name,imprinted_id,ballot_type, choice_per_voting_computer,audit_board_selection,
                // 6 consensus,record_type,audit_board_comment,timestamp,cvr_id,audit_reason
                val compareLine = ComparisonLine(
                    line.get(0).trim(),
                    line.get(1).trim(),
                    line.get(2).trim(),
                    line.get(3).trim(),
                    line.get(4).trim(),
                    line.get(5).trim(),
                    line.get(10).toInt(),
                )
                val card = cards.getOrPut(compareLine.cvrId) { Card(compareLine.cvrId) }
                card.add(compareLine)
                count++

            } catch (e: Exception) {
                println(line)
                println(line.size())
                println(e)
                println()
            }
        }
    } catch (ex: Exception) {
        println(line)
        ex.printStackTrace()
    }
    cards.values.forEach { card: Card -> card.validate() }
    println("read ${cards.size} distinct cards with ${cards.values.sumOf{ it.contests().size } } total contests voted on")

    // create the CountyStyles
    val stylesByCounty = mutableMapOf<String, CountyStyles>()
    cards.values.forEach { card: Card ->
        val countyStyles = stylesByCounty.getOrPut(card.county()) { CountyStyles(card.county()) }
        countyStyles.add(card.contests())
    }

    return stylesByCounty.values.toList()
}
