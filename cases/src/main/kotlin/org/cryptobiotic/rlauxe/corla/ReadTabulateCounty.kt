package org.cryptobiotic.rlauxe.corla

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.File
import java.nio.charset.Charset
import kotlin.String
import kotlin.text.appendLine

fun convertToCountyContestTabs(contestTabs: List<ContestTabByCounty>): List<CountyContestTab> {
    val counties = mutableMapOf<String, CountyContestTab>()

    contestTabs.forEach { contestTab ->
        contestTab.choices.values.forEach { choice: CountyTabulateChoice  ->
            choice.counties.forEach {
                val county = counties.getOrPut(it.countyName ) { CountyContestTab(it.countyName)}
                county.addChoice(it.contestName, it.choiceName, it.countyVote)
            }
        }
    }
    return counties.values.toList()
}

data class CountyContestTab(val countyName: String) {
    val contests = mutableMapOf<String, ContestTab>()

    override fun toString() = buildString {
        appendLine("'$countyName'")
        contests.values.forEach{ appendLine("  $it") }
    }

    fun addChoice(contestName: String, choiceName: String, choiceVote: Int) {
        val contest = contests.getOrPut(contestName ) { ContestTab(contestName) }
        contest.addChoice(choiceName, choiceVote)
    }
}

data class ContestTab(val contestName: String) {
    val choices = mutableMapOf<String, Int>() // choice name -> contest choice vote in this county
    val stylesForContest = mutableListOf<Style>()

    fun addChoice(choiceName: String, choiceVote: Int) {
        require( choices[choiceName] == null )
        choices[choiceName] = choiceVote
    }

    fun contestVotes() = choices.values.sumOf { it }

    override fun toString() = buildString {
        append("'$contestName': $choices")
        //choices.forEach{ append("$it, ") }
        //append(")")
    }
}

//////////////////////////////////////////////////////////
// corla/src/test/data/2024audit/tabulateCounty.csv
// county_name,contest_name,choice,votes
// Adams,Presidential Electors,Kamala D. Harris / Tim Walz,124050
// Adams,Presidential Electors,Donald J. Trump / JD Vance,103011
// Adams,Presidential Electors,Robert F. Kennedy Jr. / Nicole Shanahan,2909

// for one contest, all counties
data class ContestTabByCounty(val contestName: String) {
    val choices = mutableMapOf<String, CountyTabulateChoice>() // choice name -> CountyTabulateChoice
    var totalVotesAllCounties = 0 // total votes across counties

    fun addChoiceVote(line: ChoiceVote) {
        val choice = choices.getOrPut(line.choiceName) { CountyTabulateChoice(line.choiceName) }
        choice.addChoiceVote(line)
        totalVotesAllCounties += line.countyVote
    }

    override fun toString() = buildString {
        appendLine("'$contestName'")
        choices.values.forEach{ appendLine("  $it") }
    }

    fun counties() : List<String> {
        val counties = mutableSetOf<String>()
        choices.values.forEach { choice ->
            choice.counties.forEach { county ->
                counties.add(county.countyName)
            }
        }
        return counties.toList()
    }

    // total votes for one county for all choices
    fun countyVotes(countyName: String): Int {
        return choices.values.sumOf { it.countyVotes(countyName) }
    }
}

// for one choice, all counties
data class CountyTabulateChoice(
    val choiceName: String,
) {
    val counties = mutableListOf<ChoiceVote>()
    var totalVotes = 0 // total votes across counties for this choice

    fun addChoiceVote(line: ChoiceVote) {
        counties.add(line)
        totalVotes += line.countyVote
    }

    // total votes for one county for this choice
    fun countyVotes(countyName: String): Int {
        return counties.filter{ it.countyName == countyName }.sumOf { it.countyVote }
    }

    override fun toString() = buildString {
        append("'$choiceName'= $totalVotes [")
        counties.forEach{ append("${it.countyName}=${it.countyVote}, ") }
        append("]")
    }
}

// for one county/contest/choice
data class ChoiceVote(
    val countyName: String,
    val contestName: String,
    val choiceName: String,
    val countyVote: Int,
)

fun readCountyTabulateCsv(filename: String): List<ContestTabByCounty> {
    val file = File(filename)
    val parser = CSVParser.parse(file, Charset.forName("ISO-8859-1"), CSVFormat.DEFAULT)
    val records = parser.iterator()

    // we expect the first line to be the headers
    val headerRecord = records.next()
    val header = headerRecord.toList().joinToString(", ")
    println(header)

    val contests = mutableMapOf<String, ContestTabByCounty>()

    var line: CSVRecord? = null
    try {
        while (records.hasNext()) {
            line = records.next()!!
            var idx = 0
            if (line.size() < 4 )
                print("")
            val choiceVote = ChoiceVote(
                line.get(idx++).trim(),
                line.get(idx++).trim(),
                line.get(idx++).trim(),
                line.get(idx).toInt(),
            )
            val contest = contests.getOrPut(choiceVote.contestName) { ContestTabByCounty(choiceVote.contestName) }
            contest.addChoiceVote( choiceVote)
        }
    } catch (ex: Exception) {
        println(line)
        ex.printStackTrace()
    }

    return contests.toSortedMap().values.toList()
}


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

fun readTabulateCsv(filename: String): Map<String, TabulateContestCsv> {
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
                line.get(idx++),
                line.get(idx++),
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



