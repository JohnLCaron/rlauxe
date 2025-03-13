package org.cryptobiotic.rlaux.corla

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.sfn
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.collections.reversed
import kotlin.text.appendLine

// Colorado Election Results
// https://results.enr.clarityelections.com/CO/122598/web.345435/#/summary
// "/home/stormy/dev/github/rla/data/coloradoRLA/summary.csv"

// "line number","contest name","choice name","party name","total votes","percent of votes","registered voters","ballots cast","num Area total","num Area rptg","over votes","under votes"
//1,"Presidential Electors (Vote For 1)","Kamala D. Harris / Tim Walz","DEM",1728159,54.16,0,0,64,0,"607","2801"
//2,"Presidential Electors (Vote For 1)","Donald J. Trump / JD Vance","REP",1377441,43.17,0,0,64,0,"607","2801"
//3,"Presidential Electors (Vote For 1)","Blake Huber / Andrea Denault","APV",2196,0.07,0,0,64,0,"607","2801"

class ContestSummary(
    val contestName: String,
    val overVotes: Int,
    val underVotes: Int,
) {
    val candidates = mutableListOf<CandidateLine>()

    fun complete() {
        shortName = contestName.replace("(Vote For 1)", "").trim()
        contestVotes = candidates.sumOf { it.totalVotes }
        Nc = contestVotes + underVotes + overVotes
        underPct = 100.0 * underVotes / Nc

        val sortedCandidates = candidates.sortedBy { it.totalVotes }.reversed()
        vmargin = if (candidates.size > 1) sortedCandidates[0].totalVotes - sortedCandidates[1].totalVotes else 0
        margin = 100.0 * vmargin / contestVotes
        dilutedMargin = 100.0 * vmargin / Nc
    }
    var shortName = ""
    var contestVotes = 0
    var Nc = 0
    var underPct = 0.0
    var vmargin = 0
    var margin = 0.0
    var dilutedMargin = 0.0

    override fun toString(): String {
        return "${shortName}: votes=${contestVotes} underVotes=${underVotes} (${dfn(underPct,4)} %)"
    }

    fun show() = buildString{
        val sortedCandidates = candidates.sortedBy { it.totalVotes }.reversed()
        appendLine("${shortName}: votes=${contestVotes} voteMargin=$vmargin margin=${dfn(margin,4)} dilutedMargin=${dfn(dilutedMargin,4)} %")
        sortedCandidates.forEach {
            val calcPct = 100.0 * it.totalVotes / contestVotes
            appendLine("  ${sfn(it.name, 20)} (${it.partyName}): ${it.totalVotes} ${dfn(it.percentVotes,2)}")
        }
        appendLine()
    }
}

// "line number","contest name","choice name","party name","total votes","percent of votes","registered voters","ballots cast",
//    "num Area total","num Area rptg","over votes","under votes"
// 1,"Presidential Electors (Vote For 1)","Kamala D. Harris / Tim Walz","DEM",1728159,54.16,0,0,64,0,"607","2801"
// 139,"State Representative - District 19 (Vote For 1)","Jillaire McMillan","DEM",28310,49.90,0,0,2,0,"0","0"
// 140,"State Representative - District 20 (Vote For 1)","Jarvis Caldwell","REP",39949,71.94,0,0,1,0,"3","4103"
data class CandidateLine(
    val lineNumber: Int,
    val contestName: String,
    val choiceName: String,
    val partyName: String,
    val totalVotes: Int,
    val percentVotes: Double,
    val registeredVoters: Int,
    val ballotsCast: Int,
    val numAreaTotal: Int,
    val numAreaRptg: Int,
    val overVotes: Int,
    val underVotes: Int,
) {
    val name = if (choiceName.length > 20) choiceName.substring(20) else choiceName
}

fun readColoradoElectionSummaryCsv(filename: String): List<ContestSummary> {
    val path: Path = Paths.get(filename)
    val reader: Reader = Files.newBufferedReader(path)
    val parser = CSVParser(reader, CSVFormat.RFC4180)

    val records = parser.iterator()

    // we expect the first line to be the headers
    val headerRecord = records.next()
    val header = headerRecord.toList().joinToString(", ")
    println(header)

    // subsequent lines contain ballot manifest info
    val lines = mutableListOf<CandidateLine>()
    val contests = mutableListOf<ContestSummary>()
    var currContest: ContestSummary? = null

    try {
        while (records.hasNext()) {
            val line = records.next()
            var idx = 0
            val bmi = CandidateLine(
                line.get(idx++).toInt(),
                line.get(idx++),
                line.get(idx++),
                line.get(idx++),
                line.get(idx++).toInt(),
                line.get(idx++).toDouble(), // percentVotes
                line.get(idx++).toInt(),
                line.get(idx++).toInt(),
                line.get(idx++).toInt(),
                line.get(idx++).toInt(),
                line.get(idx++).toInt(),
                line.get(idx++).toInt(),
            )
            lines.add(bmi)
            println(bmi)

            if (currContest == null || bmi.contestName != currContest.contestName) {
                currContest = ContestSummary(bmi.contestName, bmi.overVotes, bmi.underVotes)
                contests.add(currContest)
            }
            currContest.candidates.add(bmi)
        }
    } catch (ex: Exception) {
        ex.printStackTrace()
    }

    return contests
}
