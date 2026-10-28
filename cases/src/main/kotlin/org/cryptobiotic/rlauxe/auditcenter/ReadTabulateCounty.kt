package org.cryptobiotic.rlauxe.auditcenter

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.corlaInput.defaultCharset
import org.cryptobiotic.rlauxe.corlaInput.isWriteIn
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.nfn
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import kotlin.Int
import kotlin.String
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.text.appendLine

private val logger = KotlinLogging.logger("ReadTabulateCounty")

//////////////////////////////////////////////////////////

/////////////////////////////////////////////////////////////
// maybe should be interfaces ?
// TODO get rid of ??
//   trying to remove corla stuff i think

interface ContestTabAllCountiesIF {
    val contestName: String
    val choices:  Map<String, Int>// original choice name -> votes
    val counties: Set<String>    // countyNames
    val countyVotes: Map<String, Int>     // countyName -> total votes for this contest in this county

    fun sumVotes(): Int
}

// for one county, all contests
interface CountyTabAllContestsIF {
    val countyName: String
    val contests:  Map<String, CountyContestVotesIF>// contestName (canonical I think) -> CountyContestVotes
}

// we only know votes, not ncards or undervotes.
// for one county, one contest
interface CountyContestVotesIF {
    val countyName: String
    val contestName: String
    val choices: Map<String, Int> // choice name  -> contest choice vote in this county

    fun contestVotes() = choices.values.sumOf { it }

    // convert to canonical choice names TODO get rid of
    fun canonicalChoices(choiceMapper: (String, String, String) -> String): Map<String, Int> {
        return choices.filter { !isWriteIn(it.key) }.mapKeys {
            choiceMapper(countyName, contestName, it.key )
        }
    }

    fun makeContestTabulation(info: ContestInfo, ncards: Int, choiceMapper: (String, String, String) -> String): ContestTabulation {
        val candidateVotes = canonicalChoices(choiceMapper).map { (canonicalChoiceName, vote) ->
            if (info.candidateNames[canonicalChoiceName] == null)
                logger.error{"contestTab candidate name $canonicalChoiceName not found in info"}
            Pair( info.candidateNames[canonicalChoiceName]!!, vote)
        }.toMap()

        return ContestTabulation(info, candidateVotes, ncards)
    }
}

//////////////////////////////////////////////////////////
// corla/src/test/data/2024audit/tabulateCounty.csv
// county_name,contest_name,choice,votes
// Adams,Presidential Electors,Kamala D. Harris / Tim Walz,124050
// Adams,Presidential Electors,Donald J. Trump / JD Vance,103011
// Adams,Presidential Electors,Robert F. Kennedy Jr. / Nicole Shanahan,2909

// for one county, all contests
data class CountyTabAllContests(override val countyName: String): CountyTabAllContestsIF {
    override val contests = mutableMapOf<String, CountyContestVotes>() // contestName (canonical I think) -> CountyContestVotes

    fun show() = buildString {
        appendLine("'$countyName' County")
        contests.forEach{ (name, choices) ->
            appendLine("  '$name':")
            choices.choices.forEach { (choice, votes) ->
                appendLine("    ${nfn(votes, 6)} '$choice'")
            }
            appendLine()
        }
    }

    fun addChoiceVote(line: ChoiceVote) {
        val contest = contests.getOrPut(line.contestName ) { CountyContestVotes(countyName, line.contestName) }
        contest.addChoice(line.choiceName, line.countyVote)
    }

    fun addContestTabulations(contestTabs: Map<Int, ContestTabulation>, infos: Map<Int, ContestInfo>) {
        contestTabs.forEach { (contestId, contestTab) ->
            val info = infos[contestId]!!
            val ccvFromTab = CountyContestVotes.fromContestTab(countyName, contestTab, info)
            val ccv = contests.getOrPut(info.name ) { CountyContestVotes(countyName, info.name) }
            ccvFromTab.choices.forEach { (name, vote) -> ccv.addChoice(name, vote) }
        }
    }
}

// we only know votes, not ncards or undervotes.
// for one county, one contest
data class CountyContestVotes(override val countyName: String, override val contestName: String): CountyContestVotesIF {
    override val choices = mutableMapOf<String, Int>() // choice name (not canonical) -> contest choice vote in this county

    fun addChoice(choiceName: String, choiceVote: Int) {
        val accum = choices.getOrDefault(choiceName, 0)
        choices[choiceName] = accum + choiceVote
    }

    fun canonicalChoices(canonicalContest: CanonicalContest): Map<String, Int> {
        return choices.filter { !isWriteIn(it.key) }.mapKeys {
            canonicalContest.matchCanonicalCandidate(it.key )!!
        }
    }

    fun makeContestTabulationCorla(info: ContestInfo, canonicalContest: CanonicalContest, ncards: Int): ContestTabulation {
        val candidateVotes = canonicalChoices(canonicalContest).map { (canonicalChoiceName, vote) ->
            if (info.candidateNames[canonicalChoiceName] == null)
                throw RuntimeException("contestTab candidate name $canonicalChoiceName not found in info")
            Pair( info.candidateNames[canonicalChoiceName]!!, vote)
        }.toMap()
        return ContestTabulation(info, candidateVotes, ncards)
    }

    override fun contestVotes() = choices.values.sumOf { it }

    override fun toString() = buildString {
        append("'$contestName': $choices")
    }

    companion object {
        fun fromContestTab(county: String, contestTab: ContestTabulation, info: ContestInfo): CountyContestVotes {
            val ccv = CountyContestVotes(county, info.name)
            contestTab.votes.forEach { (candId, candVote) ->
                if (info.candidateIdToName[candId] == null)
                    print("")
                ccv.addChoice(info.candidateIdToName[candId]!!, candVote)
            }
            return ccv
        }
    }
}

// for one county/contest/choice
data class ChoiceVote(
    val countyName: String,
    val contestName: String,
    val choiceName: String,
    val countyVote: Int,
)

fun readCountyTabulateCsvFromFile(filename: String): Map<String, CountyTabAllContests> {
    val file = File(filename)
    val parser = CSVParser.parse(file, Charset.forName(defaultCharset), CSVFormat.DEFAULT)
    return readCountyTabulateCsvFromParser(parser)
}

fun readCountyTabulateCsvFromResource(resourcePath: String): Map<String, CountyTabAllContests> {
    val resourceStream =
        object {}.javaClass.getClassLoader().getResourceAsStream(resourcePath) ?: throw IOException("$resourcePath does not exist")
    val reader: Reader = InputStreamReader(resourceStream, defaultCharset)
    val parser =  CSVParser.parse(reader, CSVFormat.DEFAULT)
    return readCountyTabulateCsvFromParser(parser)
}

fun readCountyTabulateCsvFromParser(parser: CSVParser): Map<String, CountyTabAllContests> {
    val records = parser.iterator()

    // we expect the first line to be the headers
    val headerRecord = records.next()
    val header = headerRecord.toList().joinToString(", ")
    // county_name,contest_name,choice,votes

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
                line.get(idx).trim().toInt(),
            )
            val county = counties.getOrPut(choiceVote.countyName) { CountyTabAllContests(choiceVote.countyName) }
            county.addChoiceVote( choiceVote)
            count++
        }
    } catch (ex: Exception) {
        println(line)
        ex.printStackTrace()
    }
    return counties.toSortedMap()
}

/////////////////////////////////////////////////
// this is derived from CountyTabAllContests
// TODO what if the contest or choice names are different across counties ??
data class ContestTabAllCounties(override val contestName: String): ContestTabAllCountiesIF {
    override val choices = mutableMapOf<String, Int>() // original choice name -> votes
    override val counties = mutableSetOf<String>()     // countyNames
    override val countyVotes = mutableMapOf<String, Int>()     // countyName -> total votes for this contest in this county

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
            val canonCand = canonicalContest.matchCanonicalCandidate(choiceName) // need county override, so dont do this here
            if (canonCand == null) {
                logger.error{"'${choiceName}' not found in canonical contest '${canonicalContest.contestName}'"}
                throw RuntimeException()
            }
            val accum = result.getOrDefault(canonCand, 0)
            result[canonCand] = accum + votes
        }
        return result
    }

    override fun sumVotes() = choices.values.sumOf { it }

    override fun toString() = buildString {
        appendLine("'$contestName'")
        choices.values.forEach{ appendLine("  $it") }
    }
}
