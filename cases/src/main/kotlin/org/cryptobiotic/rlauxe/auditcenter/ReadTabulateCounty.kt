package org.cryptobiotic.rlauxe.auditcenter

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.File
import java.nio.charset.Charset
import kotlin.Int
import kotlin.String
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.text.appendLine

//////////////////////////////////////////////////////////
// corla/src/test/data/2024audit/tabulateCounty.csv
// county_name,contest_name,choice,votes
// Adams,Presidential Electors,Kamala D. Harris / Tim Walz,124050
// Adams,Presidential Electors,Donald J. Trump / JD Vance,103011
// Adams,Presidential Electors,Robert F. Kennedy Jr. / Nicole Shanahan,2909

// for one county, all contests
data class CountyTabAllContests(val countyName: String) {
    val contests = mutableMapOf<String, CountyContestVotes>() // contestName (canonical I think) -> CountyContestVotes

    override fun toString() = buildString {
        appendLine("'$countyName'")
        contests.values.forEach{ appendLine("  $it") }
    }

    fun addChoiceVote(line: ChoiceVote) {
        val contest = contests.getOrPut(line.contestName ) { CountyContestVotes(line.contestName) }
        contest.addChoice(line.choiceName, line.countyVote)
    }
}

// we only know votes, not ncards or undervotes.
// for one county, one contest
data class CountyContestVotes(val contestName: String) {
    val choices = mutableMapOf<String, Int>() // choice name (not canonical) -> contest choice vote in this county

    fun addChoice(choiceName: String, choiceVote: Int) {
        val accum = choices.getOrDefault(choiceName, 0)
        choices[choiceName] = accum + choiceVote
    }

    fun canonicalChoices(canonicalContest: CanonicalContest): Map<String, Int> {
        return choices.filter { !isWriteIn(it.key) }.mapKeys {
            canonicalContest.matchCandidateName(it.key )!!
        }
    }

    fun contestVotes() = choices.values.sumOf { it }

    override fun toString() = buildString {
        append("'$contestName': $choices")
    }
}

// for one county/contest/choice
data class ChoiceVote(
    val countyName: String,
    val contestName: String,
    val choiceName: String,
    val countyVote: Int,
)

fun readCountyTabulateCsv(filename: String): Map<String, CountyTabAllContests> {
    val file = File(filename)
    val parser = CSVParser.parse(file, Charset.forName("ISO-8859-1"), CSVFormat.DEFAULT)
    val records = parser.iterator()

    // we expect the first line to be the headers
    val headerRecord = records.next()
    val header = headerRecord.toList().joinToString(", ")
    // println(header)

    val counties = mutableMapOf<String, CountyTabAllContests>()

    var count = 0
    var line: CSVRecord? = null
    try {
        while (records.hasNext()) {
            line = records.next()!!
            var idx = 0
            val choiceVote = ChoiceVote(
                line.get(idx++).trim(),
                line.get(idx++).trim(),
                line.get(idx++).trim(),
                line.get(idx).toInt(),
            )
            val county = counties.getOrPut(choiceVote.countyName) { CountyTabAllContests(choiceVote.countyName) }
            county.addChoiceVote( choiceVote)
            count++
        }
    } catch (ex: Exception) {
        println(line)
        ex.printStackTrace()
    }
    // println("read $count lines")
    return counties.toSortedMap()
}

data class ContestTabAllCounties(val contestName: String) {
    val choices = mutableMapOf<String, Int>() // original choice name -> votes
    val counties = mutableSetOf<String>()     // countyNames
    val countyVotes = mutableMapOf<String, Int>()     // countyName -> total votes for this contest in this county

    fun add(countyName: String, votes: CountyContestVotes) {
        votes.choices.forEach { (choiceName, votes) ->
            val accum = choices.getOrDefault(choiceName, 0)
            choices[choiceName] = accum + votes
        }
        counties.add(countyName)
        countyVotes[countyName] = votes.choices.values.sum()
    }

    // did you know that '' looks like " in a lot of fancy fonts ?
    // so the county tab file may have the same contests in different variants.....
    fun canonicalChoices(canonicalContest: CanonicalContest): Map<String, Int>  { // canonical choice name -> votes
        val result = mutableMapOf<String, Int>()
        choices.filter{ !isWriteIn(it.key) }.forEach { (choiceName, votes) ->
            val canonCand = canonicalContest.matchCandidateName(choiceName)
            if (canonCand == null) {
                println("'${choiceName}' not found in canonical contest '${canonicalContest.contestName}'")
                throw RuntimeException()
            }
            val accum = result.getOrDefault(canonCand, 0)
            result[canonCand] = accum + votes
        }
        return result
    }

    fun sumVotes() = choices.values.sumOf { it }

    override fun toString() = buildString {
        appendLine("'$contestName'")
        choices.values.forEach{ appendLine("  $it") }
    }
}
