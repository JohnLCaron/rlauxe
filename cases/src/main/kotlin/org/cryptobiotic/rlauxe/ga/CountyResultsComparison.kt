package org.cryptobiotic.rlauxe.ga

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.String

//// read ballotImageAudit file
//// return CountyIAs and ContestIAs

data class CountyIA(val county: String) {
    val contests = mutableMapOf<String, ContestIA>()

    fun addContestCandidateCount(contest: String, contestId:Int, cand: String, voteCount: Int) {
        val contest = contests.getOrPut(contest) { ContestIA(contest, contestId) }
        contest.addCandidateCount(cand, voteCount)
        // contest.addCounty(county)
    }
}

// used both for county subtotals (inside of CountyIA) and total across all counties
data class ContestIA(val contestName: String, val contestId: Int) {
    val counties = mutableMapOf<String, ContestIA>()
    val candCount = mutableMapOf<String, Int>()
    var ncards = 0
    var adjustedName = ""

    fun nameAndCands(): Set<String> {
        val result = mutableSetOf<String>()
        result.addAll(candCount.map { it.key} )
        result.add(contestName)
        return result
    }

    fun addCountsFrom(county: String, other: ContestIA) {
        counties[county] = other
        ncards += other.ncards
        other.candCount.forEach { (cand, voteCount) ->
            addCandidateCount(cand, voteCount)
        }
    }

    fun addCandidateCount(cand: String, voteCount: Int) {
        if (cand == "Ballots Cast") {
            ncards += voteCount
        } else {
            val accum = candCount.getOrPut(cand) { 0 }
            candCount[cand] = accum + voteCount
        }
    }

    fun sumVotes() = candCount.values.sum()

    override fun toString() = buildString {
        appendLine("Contest $contestId '$contestName', counties = $counties")
        candCount.forEach { (name, count) -> appendLine("    '$name' votes = $count") }
        appendLine("  ncards=$ncards sumVotes=${sumVotes()} undervotes=${ncards - sumVotes()}")
    }

    fun makeContest(nextId: Int): Contest? {
        if (candCount.size == 0) {
            return null
        }
        this.adjustedName = if (counties.size == 1) {
            val countyName = counties.keys.first()
            if (contestName.contains("For County")) contestName.replace("For County", "For $countyName County")
            else "$contestName (County $countyName)"
        } else contestName

        val candNames = candCount.keys.mapIndexed { idx, name -> Pair(name, idx)}.toMap()
        val info = ContestInfo(adjustedName, nextId, candNames, SocialChoiceFunction.PLURALITY, nwinners=1)
        info.metadata["Counties"] = counties.keys.joinToString()
        info.metadata["CountyNcards"] = counties.values.joinToString()

        val votes: Map<Int, Int> = candCount.toList().mapIndexed { idx, pair -> Pair(idx, pair.second)}.toMap()

        return Contest(info, votes, ncards, ncards)
    }
}

///////////////////////////////////////////////////////////////////////////////
// "County","ContestId","ContestName","DetailId","DetailName","OriginalCount","AuditCount","Difference"

data class CountyResultsComparison(
    val county: String,
    val contestId: Int,
    val contestName: String,
    val detailId: String,
    val detailName: String,
    val orgCount: Int,
    val auditCount: Int,
    val diffCount: Int,
)

fun readBallotImageAudit(filename: String): List<CountyResultsComparison> {
    val parser = CSVParser.parse(File(filename), StandardCharsets.UTF_8, CSVFormat.DEFAULT)
    val records = parser.iterator()
    var countReplaceForCounty = 0

    // read the header
    val headerLine = records.next()
    println(headerLine.values())

    val crcs = mutableListOf<CountyResultsComparison>()
    while (records.hasNext()) {
        val line = records.next()
        if (line.all { it.isEmpty() }) continue

        try {
            val countyName = line[0].replace("County", "").trim()
            var contestName = canonContest(line[2]).trim()
            val crc = CountyResultsComparison(
                county=countyName,
                line[1].toInt(),
                contestName,
                line[3].trim(),
                canonCand(line[4].trim()),
                if (line[5].isEmpty()) 0 else line[5].toInt(),
                if (line[5].isEmpty()) 0 else line[6].toInt(),
                if (line[5].isEmpty()) 0 else line[7].toInt(),
            )
            crcs.add(crc)
        } catch (e: Exception) {
            println("*** ${e.message} $line")
        }
    }
    println("read ${crcs.size} rows countReplaceForCounty=$countReplaceForCounty")
    return crcs
}

//////////////////////////////////////////////////////////////////////////

fun makeContestsFromImageAuditFile(filename: String, showLevel: Int = 0): Triple<List<Contest>, List<CountyIA>, List<ContestIA>> {
    val countyResult = readBallotImageAudit(filename)

    // within county, combine candidates for the same contest
    // TODO use contestId to disambiguate
    val counties = mutableMapOf<String, CountyIA>()
    countyResult.forEach {
        val county = counties.getOrPut(it.county) { CountyIA(it.county) }
        county.addContestCandidateCount(it.contestName, it.contestId, it.detailName, it.orgCount)
    }
    println("Counties ${counties.size}")

    // across counties, combine contests when name and candidate names agree
    val contestsiaNameMap = mutableMapOf<String, ContestIA>()
    val contestsiaMap = mutableMapOf<Set<String>, ContestIA>()
    counties.values.forEach { countyia ->
        countyia.contests.values.forEach { contestIA ->
            /* if (contestIA.contestName.contains("For United States House of Representatives - District 13 (DEM)")) {
                val already = contestsiaNameMap[contestIA.contestName]
                val wtf1 = already?.nameAndCands()
                val wtf2 = contestIA.nameAndCands()
                val why = contestsiaMap[contestIA.nameAndCands()]
                print("")
            } */
            if (contestIA.candCount.isNotEmpty()) {
                val combineContest = contestsiaMap.getOrPut(contestIA.nameAndCands()) { ContestIA(contestIA.contestName, contestIA.contestId) }
                combineContest.addCountsFrom(countyia.county, contestIA)
                contestsiaNameMap[contestIA.contestName] = contestIA
            }
        }
    }
    println("CountyContests ${contestsiaMap.size}")
    val contestsia = contestsiaMap.values.toList().sortedBy { it.contestName }

    if (showLevel > 0) {
        contestsia.forEach { contestia: ContestIA ->
            if (showLevel > 1) println("  ${contestia}") else println(contestia.contestName)
        }
        println()
    }

    // keep in order by contestId as much as possible; but assign new sequential ids
    val icontestsia: List<Pair<Int, ContestIA>> = contestsia.map { Pair(it.contestId, it) }.sortedBy { it.first }

    val contests = mutableListOf<Contest>()
    var nextId = 1
    var countNoCand = 0
    icontestsia.map { it.second }.forEach {
        val contest = it.makeContest(nextId)
        if (contest != null) {
            contests.add(contest)
            nextId++
        } else countNoCand++
    }
    println("countNoCand = $countNoCand")

    // recreate the county grouping using the transformed contests
    val countyGroups = mutableMapOf<String, CountyIA>()
    contestsia.forEach { contestIA ->
        contestIA.counties.forEach { (county, countyContest) ->
            val countyGroup = countyGroups.getOrPut(county) { CountyIA(county) }
            countyGroup.contests[contestIA.adjustedName] = countyContest  // clever
        }
    }

    return Triple(contests, countyGroups.values.toList(), contestsia)
}

fun canonCand(candName: String) : String {
    return when (candName) {
        "Dwayne H. Gillis" -> "Dwayne Hamilton Gillis"
        "Billy Joe Nelson, Jr." -> "Billy Joe Nelson Jr."
        "Patrick J Wilver" -> "Patrick J. Wilver"
        "Sanford Bishop" -> "Sanford Bishop (I)"
        "Jeff Fauntleroy, Sr." -> "Jeff Fauntleroy Sr."
        "Edward D. Harbinson" -> "Edward D. Harbison"
        "Edward D Harbison" -> "Edward D. Harbison"
        "Emanuel D Jones" -> "Emanuel D. Jones"
        "L.C. Myles, Jr." -> "L.C. Myles Jr."
        "Corey B Morgan" -> "Corey B. Morgan"
        else -> candName
    }
}

fun canonContest(contest: String) : String {
    var work = contest
    if (contest.contains("United States House of  Representatives"))
        work = contest.replace("United States House of  Representatives", "United States House of Representatives")
    if (work.startsWith("For ")) work = work.drop(4)

    return when (work) {
        "Continuation of 1% Special Purpose Local Option Sales Tax for Educational Purposes" -> "Continuation of 1% Special Purpose Local Option Sales Tax for Education Purposes"
        "For United States House of Representatives - District 1" -> "For United States House of Representatives - District 1 (DEM)"
        "Democratic Party Question 3" -> "Democratic Party Question 3 (DEM)"
        "Democratic Party Question 4" -> "Democratic Party Question 4 (DEM)"
        "Democratic Party Question 5" -> "Democratic Party Question 5 (DEM)"
        "Democratic Party Question 6" -> "Democratic Party Question 6 (DEM)"
        "Democratic Party Question 7" -> "Democratic Party Question 7 (DEM)"
        "Judge - Court of Appeals of Georgia (Brown)" -> "For Judge Court of Appeals of Georgia (Brown, III)"
        "Judge - Court of Appeals of Georgia (Brown, III)" -> "For Judge Court of Appeals of Georgia"
        else -> work
    }
}

