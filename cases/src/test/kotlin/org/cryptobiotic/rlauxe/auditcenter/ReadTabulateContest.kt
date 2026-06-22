package org.cryptobiotic.rlauxe.auditcenter

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.File
import java.nio.charset.Charset
import kotlin.Int
import kotlin.String

/////////////////////////////////////////////////////////////////////////////////////////
// NOT USED

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

fun readTabulateCsv(filename: String, cleanupContest: (String) -> String, cleanupCandidate: (String) -> String): Map<String, TabulateContestCsv> {
    val file = File(filename)
    val parser = CSVParser.parse(file, Charset.forName("ISO-8859-1"), CSVFormat.DEFAULT)
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
            val tabLine = ChoiceVote(
                "",
                cleanupContest(line.get(idx++).trim()),
                cleanupCandidate(line.get(idx++).trim()),
                line.get(idx++).toInt(),
            )
            val contest = contests.getOrPut(tabLine.contestName) { TabulateContestCsv(tabLine.contestName, contestIdx++) }
            contest.addChoice(tabLine.choiceName, tabLine.countyVote)
        }
    } catch (ex: Exception) {
        println(line)
        ex.printStackTrace()
    }

    return contests
}



