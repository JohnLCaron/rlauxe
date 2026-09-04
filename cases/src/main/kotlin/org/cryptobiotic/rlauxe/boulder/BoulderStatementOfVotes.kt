package org.cryptobiotic.rlauxe.boulder

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.cvr.isEmpty
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import kotlin.text.appendLine

// parses Boulder County "statement of votes" csv files, eg from
// https://assets.bouldercounty.gov/wp-content/uploads/2024/11/2024G-Boulder-County-Official-Statement-of-Votes.xlsx

// variations
// (2023R) "Precinct Code","Precinct Number","Active Voters","Contest Title","Candidate Name","Total Ballots","Round 1 Votes","Round 2 Votes","Total Votes","Total Blanks","Total Overvotes","Total Exhausted"
// (2023) "Precinct Code","Precinct Number","Active Voters","Contest Title","Choice Name","Total Ballots","Total Votes","Total Undervotes","Total Overvotes"
// (2024) "Precinct Code","Precinct Number","Contest Title","Choice Name","Active Voters","Total Ballots","Total Votes","Total Undervotes","Total Overvotes"
// (2026) "Precinct Code","Precinct Number","Contest Title","Choice Name","Total Ballots","Total Votes","Total Undervotes","Total Overvotes"

// lines look like
//100,2181207100,Presidential Electors,Kamala D. Harris / Tim Walz,"1,569","1,325",900,24,0
//100,2181207100,Presidential Electors,Donald J. Trump / JD Vance,"1,569","1,325",354,24,0
//100,2181207100,Presidential Electors,Blake Huber / Andrea Denault,"1,569","1,325",1,24,0

// replicate https://assets.bouldercounty.gov/wp-content/uploads/2024/11/2024G-Boulder-County-Official-Summary-of-Votes.pdf
data class BoulderStatementOfVotes(val inputSource: String, val contests: List<SovoContestVotes>) {

    init {
        contests.forEachIndexed{ idx, it -> it.id = idx+1 }
    }

    fun show() = buildString{
        appendLine("Boulder Statement of Votes")
        appendLine(inputSource)
        appendLine("ncontests = ${contests.size}")
        appendLine()
        appendLine(SovoContestVotes.header)
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

    fun setIds(contestIds:List<Pair<String, Int>>) {
        var countSkip = -1
        contests.forEach { sovoContest ->
            val contestId = contestIds.find { sovoContest.contestTitle.contains(it.first) }
            if (contestId == null)
                sovoContest.id = countSkip--
            else
                sovoContest.id = contestId.second
        }
    }
}

private val ContestNameWidth = 55


// all the votes for one contest
data class SovoContestVotes(
    val contestTitle: String,
) {
    var precinctCount: Int = 0
    var activeVoters: Int = 0
    var totalBallots: Int = 0  // = (totalVotes + totalUnderVotes) / voteForN + totalOverVotes
    var totalVotes: Int = 0     // sum of votes
    var totalUnderVotes: Int = 0  // undervotes = voteForN * Ncast - nvotes
    var totalOverVotes: Int = 0     // these are invalid, and are discarded
    val candidateVotes = mutableMapOf<String, Int>()  // candidateName -> number of votes
    var id = 0

    fun addPrecinct(precinct: BoulderSovPrecinctContest) {
        precinctCount++
        activeVoters += precinct.activeVoters
        totalBallots += precinct.totalBallots
        totalUnderVotes += precinct.totalUnderVotes
        totalOverVotes += precinct.totalOverVotes
        precinct.lines.forEach { addLine(it) }
    }

    fun addLine(line: BoulderSovPrecinctLine) {
        totalVotes += line.totalVotes
        val votes = candidateVotes.getOrDefault(line.choiceName, 0)
        candidateVotes[line.choiceName] = votes + line.totalVotes
    }

    fun calcNcast(voteForN: Int): Int {
        return (totalVotes + totalUnderVotes) / voteForN
    }

    fun checkTotalVotes(voteForN: Int): Boolean {
        return totalBallots == calcNcast(voteForN) + totalOverVotes
    }

    override fun toString() = buildString {
        append("${nfn(id,2)}, ")
        append("${trunc(contestTitle, ContestNameWidth)}, ")
        append("${nfn(precinctCount,8)}, ")
        append("${nfn(activeVoters,12)}, ")
        append("${nfn(totalBallots,12)}, ")
        append("${nfn(totalVotes,12)}, ")
        append("${nfn(totalUnderVotes,12)}, ")
        append("${nfn(totalOverVotes,12)}, ")
    }

    companion object {
        val header = "id, ${sfn("contestTitle", ContestNameWidth-8)}     precinctCount, activeVoters, totalBallots,   totalVotes, totalUnderVotes, totalOverVotes"
    }
}

// all the votes for one precinct and one contest
data class BoulderSovPrecinctContest(
    val contestTitle: String,
    val precinctCode: String,
    val precinctNumber: String,
    val activeVoters: Int,
    val totalBallots: Int,
    val totalUnderVotes: Int,
    val totalOverVotes: Int,
) {
    constructor(line: BoulderSovPrecinctLine): this(line.contestTitle, line.precinctCode, line.precinctNumber, line.activeVoters, line.totalBallots, line.totalUnderVotes, line.totalOverVotes)

    val lines = mutableListOf<BoulderSovPrecinctLine>()

    fun addLine(line: BoulderSovPrecinctLine) {
        lines.add(line)
    }
}

// "Precinct Code","Precinct Number","Contest Title","Choice Name","Active Voters","Total Ballots","Total Votes","Total Undervotes","Total Overvotes"
//100,2181207100,Presidential Electors,Kamala D. Harris / Tim Walz,"1,569","1,325",900,24,0
//100,2181207100,Presidential Electors,Donald J. Trump / JD Vance,"1,569","1,325",354,24,0
//100,2181207100,Presidential Electors,Blake Huber / Andrea Denault,"1,569","1,325",1,24,0

// all the votes for one precinct; recorded separately for each contest, no record of card style
data class BoulderSovPrecinctLine(
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

        // (2023) "Precinct Code","Precinct Number","Active Voters","Contest Title","Choice Name","Total Ballots","Total Votes","Total Undervotes","Total Overvotes"
        fun make2023(line: CSVRecord): BoulderSovPrecinctLine {
            return BoulderSovPrecinctLine(
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

        // Note only place we have IRV contests
        // Note we have "Round 1 Votes","Round 2 Votes": not useful for calculating Assertions, we need the ranks for each ballot
        // (2023) "Precinct Code","Precinct Number","Active Voters","Contest Title","Candidate Name","Total Ballots","Round 1 Votes","Round 2 Votes","Total Votes","Total Blanks","Total Overvotes","Total Exhausted"
        fun make2023Rcv(line: CSVRecord): BoulderSovPrecinctLine {
            return BoulderSovPrecinctLine(
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

        // (2025) "Precinct Code","Precinct Number","Contest Title","Choice Name","Active Voters*","Total Ballots","Total Votes","Total Undervotes","Total Overvotes"
        // (2024) "Precinct Code","Precinct Number","Contest Title","Choice Name","Active Voters","Total Ballots","Total Votes","Total Undervotes","Total Overvotes"
        fun make2024(line: CSVRecord): BoulderSovPrecinctLine {
            try {
                return BoulderSovPrecinctLine(
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
            } catch (e: Exception) {
                println(line)
                throw e
            }
        }

        // (2026) "Precinct Code","Precinct Number","Contest Title","Choice Name","Total Ballots","Total Votes","Total Undervotes","Total Overvotes"
        fun make2026(line: CSVRecord): BoulderSovPrecinctLine {
            return BoulderSovPrecinctLine(
                line.get(0),    // code
                line.get(1),    // precinct
                line.get(2),    // contest
                line.get(3),    // choice, candidate
                0, // activeVoters
                line.get(4).convertToInteger(), // totalBallots
                line.get(5).convertToInteger(), // totalVotes
                line.get(6).convertToInteger(), // under
                line.get(7).convertToInteger(), // over
            )
        }
    }
}

fun readBoulderSOV(source: String, electionName: String): BoulderStatementOfVotes {
    return if (source.startsWith("/resources/")) readBoulderSOVfromResourcePath(source, electionName)
           else readBoulderStatementOfVotes(source, electionName)
}

fun readBoulderSOVfromResourcePath(resourcePath: String, electionName: String): BoulderStatementOfVotes {
    val inputStream = object {}.javaClass.getResourceAsStream(resourcePath) ?:
    throw IOException("$resourcePath does not exist")
    return readBoulderSOVfromInputStream(inputStream, electionName, resourcePath)
}

fun readBoulderStatementOfVotes(filename: String, electionName: String): BoulderStatementOfVotes {
    return readBoulderSOVfromInputStream(FileInputStream(filename), electionName, filename)
}

fun readBoulderSOVfromInputStream(input: InputStream, electionName: String, inputSource: String): BoulderStatementOfVotes {
    val parser = CSVParser.parse(input, Charset.forName("UTF-8"), CSVFormat.DEFAULT)
    // val parser = CSVParser.parse(File(filename), Charset.forName("ISO-8859-1"), CSVFormat.DEFAULT)

    val records = parser.iterator()

    // we expect the first line to be the headers
    val header = records.next()
    // val header = headerRecord.toList().joinToString(", ")
    // println(header)

    // subsequent lines contain ballot manifest info
    val lines = mutableListOf<BoulderSovPrecinctLine>()
    var line: CSVRecord? = null
    try {
        while (records.hasNext()) {
            line = records.next()
            if (line.isEmpty()) break // assume done when we see a blank line
            val bmi: BoulderSovPrecinctLine = when (electionName) {
                "Boulder2023" -> BoulderSovPrecinctLine.make2023(line)
                "Boulder2023Rcv" -> BoulderSovPrecinctLine.make2023Rcv(line)
                "Boulder2024clca" -> BoulderSovPrecinctLine.make2024(line)
                "Boulder2024",
                "Boulder2025" -> BoulderSovPrecinctLine.make2024(line)
                "Boulder2026p" -> BoulderSovPrecinctLine.make2026(line)
                else -> { throw RuntimeException("Unknown electionName $electionName")}
            }
            lines.add(bmi)
        }
    } catch (ex: Exception) {
        println("Error on line ${lines.size} == ${line}")
        throw ex
    }

    // group by contest and precinct
    val precincts = mutableMapOf<String, BoulderSovPrecinctContest>()
    lines.forEach {
        val key = "${it.contestTitle}#${it.precinctCode}#${it.precinctNumber}"
        val precinct = precincts.getOrPut(key) { BoulderSovPrecinctContest(it) }
        precinct.addLine(it)
    }

    // accumulate into contests
    val contests = mutableMapOf<String, SovoContestVotes>()
    precincts.values.forEach { precinct ->
        val key = precinct.contestTitle
        val contest = contests.getOrPut(key) { SovoContestVotes( key) }
        contest.addPrecinct(precinct)
    }
    return BoulderStatementOfVotes(inputSource, contests.toSortedMap().values.toList())
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

