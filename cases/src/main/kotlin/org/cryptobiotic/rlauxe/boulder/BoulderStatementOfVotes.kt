package org.cryptobiotic.rlauxe.boulder

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.File
import java.nio.charset.Charset
import kotlin.text.appendLine

// parses Boulder County "statement of votes" csv files, eg from
// https://assets.bouldercounty.gov/wp-content/uploads/2024/11/2024G-Boulder-County-Official-Statement-of-Votes.xlsx

// variations
// (2023R) "Precinct Code","Precinct Number","Active Voters","Contest Title","Candidate Name","Total Ballots","Round 1 Votes","Round 2 Votes","Total Votes","Total Blanks","Total Overvotes","Total Exhausted"
// (2023) "Precinct Code","Precinct Number","Active Voters","Contest Title","Choice Name","Total Ballots","Total Votes","Total Undervotes","Total Overvotes"
// (2024) "Precinct Code","Precinct Number","Contest Title","Choice Name","Active Voters","Total Ballots","Total Votes","Total Undervotes","Total Overvotes"

// lines look like
//100,2181207100,Presidential Electors,Kamala D. Harris / Tim Walz,"1,569","1,325",900,24,0
//100,2181207100,Presidential Electors,Donald J. Trump / JD Vance,"1,569","1,325",354,24,0
//100,2181207100,Presidential Electors,Blake Huber / Andrea Denault,"1,569","1,325",1,24,0

// replicate https://assets.bouldercounty.gov/wp-content/uploads/2024/11/2024G-Boulder-County-Official-Summary-of-Votes.pdf
data class BoulderStatementOfVotes(val filename: String, val contests: List<BoulderContestVotes>) {
    fun show() = buildString{
        appendLine("Boulder Statement of Votes")
        appendLine(filename)
        appendLine("ncontests = ${contests.size}")
        appendLine()
        appendLine(BoulderContestVotes.header)
        contests.forEach {
            appendLine(it)
        }
    }

    companion object {
        fun combine(sovos: List<BoulderStatementOfVotes>): BoulderStatementOfVotes {
            val combined = sovos.map { it.contests }.flatten()
            return BoulderStatementOfVotes("combined", combined)
        }
    }
}

data class BoulderContestVotes(
    val contestTitle: String,
) {
    var precinctCount: Int = 0
    var activeVoters: Int = 0
    var totalBallots: Int = 0  // Nc
    var totalVotes: Int = 0     // sum of votes
    var totalUnderVotes: Int = 0  // undervotes
    var totalOverVotes: Int = 0     // hmmm
    val candidateVotes = mutableMapOf<String, Int>()  // candidateName -> number of votes

    fun addPrecinct(precinct: BoulderContestPrecinctVotes) {
        precinctCount++
        activeVoters += precinct.activeVoters
        totalBallots += precinct.totalBallots
        totalUnderVotes += precinct.totalUnderVotes
        totalOverVotes += precinct.totalOverVotes
        precinct.lines.forEach { addLine(it) }
    }

    fun addLine(line: BoulderStatementLine) {
        totalVotes += line.totalVotes
        val votes = candidateVotes.getOrDefault(line.choiceName, 0)
        candidateVotes[line.choiceName] = votes + line.totalVotes
    }

    override fun toString(): String {
        return "$contestTitle, $precinctCount, $activeVoters, $totalBallots, $totalVotes, $totalUnderVotes, $totalOverVotes"
    }

    companion object {
        val header = "contestTitle, precinctCount, activeVoters, totalBallots, totalVotes, totalUnderVotes, totalOverVotes"
    }
}

data class BoulderContestPrecinctVotes(
    val contestTitle: String,
    val precinctCode: String,
    val precinctNumber: String,
    val activeVoters: Int,
    val totalBallots: Int,
    val totalUnderVotes: Int,
    val totalOverVotes: Int,
) {
    constructor(line: BoulderStatementLine): this(line.contestTitle, line.precinctCode, line.precinctNumber, line.activeVoters, line.totalBallots, line.totalUnderVotes, line.totalOverVotes)

    val lines = mutableListOf<BoulderStatementLine>()

    fun addLine(line: BoulderStatementLine) {
        lines.add(line)
    }
}

// "Precinct Code","Precinct Number","Contest Title","Choice Name","Active Voters","Total Ballots","Total Votes","Total Undervotes","Total Overvotes"
//100,2181207100,Presidential Electors,Kamala D. Harris / Tim Walz,"1,569","1,325",900,24,0
//100,2181207100,Presidential Electors,Donald J. Trump / JD Vance,"1,569","1,325",354,24,0
//100,2181207100,Presidential Electors,Blake Huber / Andrea Denault,"1,569","1,325",1,24,0

data class BoulderStatementLine(
    val precinctCode: String,
    val precinctNumber: String,
    val contestTitle: String,
    val choiceName: String,
    val activeVoters: Int,
    val totalBallots: Int,
    val totalVotes: Int,
    val totalUnderVotes: Int,
    val totalOverVotes: Int,
) {
    companion object {
        // (2024) "Precinct Code","Precinct Number","Contest Title","Choice Name","Active Voters","Total Ballots","Total Votes","Total Undervotes","Total Overvotes"
        fun make2024(line: CSVRecord): BoulderStatementLine {
            return BoulderStatementLine(
                line.get(0),
                line.get(1),
                line.get(2),
                line.get(3),
                line.get(4).convertToInteger(),
                line.get(5).convertToInteger(),
                line.get(6).convertToInteger(),
                line.get(7).convertToInteger(),
                line.get(8).convertToInteger(),
            )
        }

        // (2023) "Precinct Code","Precinct Number","Active Voters","Contest Title","Choice Name","Total Ballots","Total Votes","Total Undervotes","Total Overvotes"
        fun make2023(line: CSVRecord): BoulderStatementLine {
            return BoulderStatementLine(
                line.get(0),    // code
                line.get(1),    // precinct
                line.get(3),    // contest
                line.get(4),    // choice, candidate
                line.get(2).convertToInteger(), // activeVoters
                line.get(5).convertToInteger(), // totalBallots
                line.get(6).convertToInteger(), // totalVotes
                line.get(7).convertToInteger(), // under
                line.get(8).convertToInteger(), // over
            )
        }

        // "Precinct Code","Precinct Number","Active Voters","Contest Title","Candidate Name","Total Ballots","Round 1 Votes","Round 2 Votes","Total Votes","Total Blanks","Total Overvotes","Total Exhausted"
        fun make2023Rcv(line: CSVRecord): BoulderStatementLine {
            return BoulderStatementLine(
                line.get(0),    // code
                line.get(1),    // precinct
                line.get(3),    // contest
                line.get(4),    // choice, candidate
                line.get(2).convertToInteger(), // activeVoters
                line.get(5).convertToInteger(), // totalBallots
                line.get(8).convertToInteger(), // totalVotes
                line.get(9).convertToInteger(), // under
                line.get(10).convertToInteger(), // over
            )
        }
    }
}

fun readBoulderStatementOfVotes(filename: String, variation: String): BoulderStatementOfVotes {
    val parser = CSVParser.parse(File(filename), Charset.forName("UTF-8"), CSVFormat.DEFAULT)
    // val parser = CSVParser.parse(File(filename), Charset.forName("ISO-8859-1"), CSVFormat.DEFAULT)

    val records = parser.iterator()

    // we expect the first line to be the headers
    records.next()
    // val header = headerRecord.toList().joinToString(", ")
    // println(header)

    // subsequent lines contain ballot manifest info
    val lines = mutableListOf<BoulderStatementLine>()
    var line: CSVRecord? = null
    try {
        while (records.hasNext()) {
            line = records.next()
            val bmi = when (variation) {
                "Boulder2024" -> BoulderStatementLine.make2024(line)
                "Boulder2023" -> BoulderStatementLine.make2023(line)
                "Boulder2023Rcv" -> BoulderStatementLine.make2023Rcv(line)
                else -> { throw RuntimeException("Unknown variation $variation")}
            }
            lines.add(bmi)
        }
    } catch (ex: Exception) {
        println("Error on line ${lines.size} == ${line}")
        throw ex
    }

    // first, group by precinct
    val precincts = mutableMapOf<String, BoulderContestPrecinctVotes>()
    lines.forEach {
        val key = "${it.contestTitle}#${it.precinctCode}#${it.precinctNumber}"
        val precinct = precincts.getOrPut(key) { BoulderContestPrecinctVotes(it) }
        precinct.addLine(it)
    }

    // now accumulate into contests
    val contests = mutableMapOf<String, BoulderContestVotes>()
    precincts.values.forEach { precinct ->
        val key = precinct.contestTitle
        val contest = contests.getOrPut(key) { BoulderContestVotes(key) }
        contest.addPrecinct(precinct)
    }
    return BoulderStatementOfVotes(filename, contests.toSortedMap().values.toList())
}

fun String.convertToInteger(): Int {
    if (this == "N/A") return -1
    val cs = mutableListOf<Char>()
    // remove quotes and comma
    for (i in 0 until this.length) {
        val c = this[i]
        if (c != '"' && c != ',') {
            cs.add(c)
        }
    }
    val s = String(cs.toCharArray())
    return s.toInt()
}
