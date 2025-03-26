package org.cryptobiotic.rlaux.corla

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.File
import java.nio.charset.Charset

// corla/src/test/data/2024audit/tabulateCounty.csv
// county_name,contest_name,choice,votes
// Adams,Presidential Electors,Kamala D. Harris / Tim Walz,124050
// Adams,Presidential Electors,Donald J. Trump / JD Vance,103011
// Adams,Presidential Electors,Robert F. Kennedy Jr. / Nicole Shanahan,2909

data class CountyTabulateCsv(
    val contestName: String,
) {
    val choices = mutableMapOf<String, CountyTabulateChoice>()
}

data class CountyTabulateChoice(
    val choiceName: String,
) {
    val counties = mutableListOf<TabulateLine>()
    var totalVotes = 0

    fun addCounty(line: TabulateLine) {
        counties.add(line)
        totalVotes += line.totalVotes
    }
}

data class TabulateLine(
    val countyName: String,
    val contestName: String,
    val choiceName: String,
    val totalVotes: Int,
)

fun readCountyTabulateCsv(filename: String): Map<String, CountyTabulateCsv> {
    val file = File(filename)
    val parser = CSVParser.parse(file, Charset.forName("ISO-8859-1"), CSVFormat.RFC4180)
    val records = parser.iterator()

    // we expect the first line to be the headers
    val headerRecord = records.next()
    val header = headerRecord.toList().joinToString(", ")
    println(header)

    val contests = mutableMapOf<String, CountyTabulateCsv>()

    var line: CSVRecord? = null
    try {
        while (records.hasNext()) {
            line = records.next()!!
            var idx = 0
            val county = TabulateLine(
                line.get(idx++),
                line.get(idx++),
                line.get(idx++),
                line.get(idx++).toInt(),
            )
            val contest = contests.getOrPut(county.contestName) { CountyTabulateCsv(county.contestName) }
            val choice = contest.choices.getOrPut(county.choiceName) { CountyTabulateChoice(county.choiceName) }
            choice.addCounty( county)
        }
    } catch (ex: Exception) {
        println(line)
        ex.printStackTrace()
    }

    return contests
}

// corla/src/test/data/2024audit/tabulate.csv
// contest_name,choice,votes
// 17th Judicial District Ballot Question 7B,No/Against,142021
// 17th Judicial District Ballot Question 7B,Yes/For,104472
// Adams 12 Five Star Schools Ballot Issue 5D,No/Against,59440
// Adams 12 Five Star Schools Ballot Issue 5D,Yes/For,46818
// Adams 12 Five Star Schools Ballot Issue 5E,Yes/For,57572

data class TabulateContestCsv(
    val contestName: String,
    val idx: Int,
) {
    val choices = mutableListOf<TabulateContestChoice>()
    var choiceIdx = 0

    fun addChoice(choiceName: String, totalVotes: Int) {
        choices.add(TabulateContestChoice(choiceName, choiceIdx++, totalVotes))
    }
}

data class TabulateContestChoice(
    val choiceName: String,
    val idx: Int,
    val totalVotes: Int,
)

fun readTabulateCsv(filename: String): Map<String, TabulateContestCsv> {
    val file = File(filename)
    val parser = CSVParser.parse(file, Charset.forName("ISO-8859-1"), CSVFormat.RFC4180)
    val records = parser.iterator()

    // we expect the first line to be the headers
    val headerRecord = records.next()
    val header = headerRecord.toList().joinToString(", ")
    println(header)

    val contests = mutableMapOf<String, TabulateContestCsv>()

    var contestIdx = 0
    var line: CSVRecord? = null
    try {
        while (records.hasNext()) {
            line = records.next()!!
            var idx = 0
            val tabLine = TabulateLine(
                "",
                line.get(idx++),
                line.get(idx++),
                line.get(idx++).toInt(),
            )
            val contest = contests.getOrPut(tabLine.contestName) { TabulateContestCsv(tabLine.contestName, contestIdx++) }
            contest.addChoice(tabLine.choiceName, tabLine.totalVotes)
        }
    } catch (ex: Exception) {
        println(line)
        ex.printStackTrace()
    }

    return contests
}



