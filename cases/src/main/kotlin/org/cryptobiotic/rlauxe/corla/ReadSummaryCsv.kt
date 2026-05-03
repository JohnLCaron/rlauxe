package org.cryptobiotic.rlauxe.corla

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.pfn
import org.cryptobiotic.rlauxe.util.sfn
import java.io.File
import java.nio.charset.Charset
import kotlin.collections.reversed
import kotlin.text.appendLine

/* Colorado Election Summary Results
    https://results.enr.clarityelections.com/CO/122598/web.345435/#/summary
    not in auditcenter repo

data/corla/2024election/summary.csv

has a Line for every contest and candidate

    "line number","contest name","choice name","party name","total votes","percent of votes","registered voters","ballots cast","num Area total","num Area rptg","over votes","under votes"
    1,"Presidential Electors (Vote For 1)","Kamala D. Harris / Tim Walz","DEM",1728159,54.16,0,0,64,0,"607","2801"
    2,"Presidential Electors (Vote For 1)","Donald J. Trump / JD Vance","REP",1377441,43.17,0,0,64,0,"607","2801"
    3,"Presidential Electors (Vote For 1)","Blake Huber / Andrea Denault","APV",2196,0.07,0,0,64,0,"607","2801"

data/corla/2024audit/round1/contest.csv has one line per contest, showing more information:

contest_name,audit_reason,random_audit_status,winners_allowed,ballot_card_count,contest_ballot_card_count,winners,min_margin,risk_limit,audited_sample_count,two_vote_over_count,one_vote_over_count,one_vote_under_count,two_vote_under_count,disagreement_count,other_count,gamma,overstatements,optimistic_samples_to_audit,estimated_samples_to_audit
17th Judicial District Ballot Question 7B,opportunistic_benefits,in_progress,1,516401,279529,"""No/Against""",37549,0.03000000,0,0,0,0,0,0,0,1.03905000,0,101,101
Adams 12 Five Star Schools Ballot Issue 5D,opportunistic_benefits,in_progress,1,516401,117043,"""No/Against""",12622,0.03000000,0,0,0,0,0,0,0,1.03905000,0,299,299
Adams 12 Five Star Schools Ballot Issue 5E,opportunistic_benefits,in_progress,1,516401,117043,"""Yes/For""",10481,0.03000000,0,0,0,0,0,0,0,1.03905000,0,360,360
Adams-Arapahoe School District 28J Ballot Issue 5A,opportunistic_benefits,in_progress,1,799746,84894,"""Yes/For""",22669,0.03000000,0,0,0,0,0,0,0,1.03905000,0,258,258
Adams-Arapahoe School District 28J Ballot Issue 5B,opportunistic_benefits,in_progress,1,799746,84894,"""Yes/For""",39107,0.03000000,0,0,0,0,0,0,0,1.03905000,0,150,150


we dont use
*/

class ColoradoElectionContestSummary(
    val contestName: String,
    val overVotes: Int,
    val underVotes: Int,
) {
    val candidates = mutableListOf<ColoradoElectionCandidateLine>()

    fun complete() {
        shortName = contestName.replace("(Vote For 1)", "").trim()
        contestVotes = candidates.sumOf { it.totalVotes }
        Nc = contestVotes + underVotes + overVotes  // TODO wrong - need voteForN
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
        appendLine("${shortName}: votes=${contestVotes} voteMargin=$vmargin margin=${dfn(margin,4)} dilutedMargin=${pfn(dilutedMargin)}")
        sortedCandidates.forEach {
            val calcPct = 100.0 * it.totalVotes / contestVotes
            appendLine("  ${sfn(it.name, 20)} (${it.partyName}): ${it.totalVotes} ${dfn(it.percentVotes,2)} calcPct=$calcPct")
        }
        appendLine()
    }
}

// "line number","contest name","choice name","party name","total votes","percent of votes","registered voters","ballots cast",
//    "num Area total","num Area rptg","over votes","under votes"
// 1,"Presidential Electors (Vote For 1)","Kamala D. Harris / Tim Walz","DEM",1728159,54.16,0,0,64,0,"607","2801"
// 139,"State Representative - District 19 (Vote For 1)","Jillaire McMillan","DEM",28310,49.90,0,0,2,0,"0","0"
// 140,"State Representative - District 20 (Vote For 1)","Jarvis Caldwell","REP",39949,71.94,0,0,1,0,"3","4103"
data class ColoradoElectionCandidateLine(
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

fun readColoradoElectionSummaryCsv(filename: String): List<ColoradoElectionContestSummary> {
    val parser = CSVParser.parse(File(filename), Charset.forName("ISO-8859-1"), CSVFormat.DEFAULT)
    val records = parser.iterator()

    // we expect the first line to be the headers
    val headerRecord = records.next()
    headerRecord.toList().joinToString(", ")
    // println(header)

    // subsequent lines contain ballot manifest info
    val lines = mutableListOf<ColoradoElectionCandidateLine>()
    val contests = mutableListOf<ColoradoElectionContestSummary>()
    var currContest: ColoradoElectionContestSummary? = null

    var line: CSVRecord? = null
    try {
        while (records.hasNext()) {
            line = records.next()!!
            var idx = 0
            val bmi = ColoradoElectionCandidateLine(
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
            // println(bmi)

            if (currContest == null || bmi.contestName != currContest.contestName) {
                currContest = ColoradoElectionContestSummary(bmi.contestName, bmi.overVotes, bmi.underVotes)
                contests.add(currContest)
            }
            currContest.candidates.add(bmi)
        }
    } catch (ex: Exception) {
        println(line)
        ex.printStackTrace()
    }

    return contests
}
