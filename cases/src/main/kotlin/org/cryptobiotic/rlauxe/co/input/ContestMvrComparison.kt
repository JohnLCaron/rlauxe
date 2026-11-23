package org.cryptobiotic.rlauxe.co.input

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.auditcenter.AuditReason
import org.cryptobiotic.rlauxe.auditcenter.getAuditReason
import org.cryptobiotic.rlauxe.co.auditcenter.ActivityReport
import org.cryptobiotic.rlauxe.co.auditcenter.MvrComparison
import org.cryptobiotic.rlauxe.co.auditcenter.ResultsReport
import org.cryptobiotic.rlauxe.co.auditcenter.TabulateContest
import org.cryptobiotic.rlauxe.co.auditcenter.TabulateCounty
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import kotlin.collections.set

private val logger = KotlinLogging.logger("ContestMvrMergedInfo")


// Originally to check that the sequence of Draws was correct.
// Not trying to use it to count the errors in all the contests ....

data class MvrMergedComparison(
    // constructor params are from Activity
    val dbId: Int,
    val county: String,
    val imprintedId: String,
    val discrepency: String, // what does this mean anyway ?? probably bogus
    val revision: String,
    val auditReason: AuditReason,
    val timeOfSubmission: String,
) {
    // from result needed?
    var prn: Int = 0
    var seq: Int = 0
    var mult: Int = 1 // default; revised dont get set

    // optional, from mvrComparisonFile; // TODO are these names canonical ??
    // var lines: List<MvrComparison>? = null

    override fun toString(): String {
        return "MvrMergedComparison(dbId=$dbId, '$county', '$imprintedId', prn=$prn, seq=$seq, '$auditReason' $timeOfSubmission, discrepency='$discrepency', revision='$revision')"
    }
}

// one for each contest
data class ContestMvrComparison(
    val contestName: String,
    val counties: Set<String>,
    val auditReason: AuditReason,
    val ballotCardCount: Int,
) {
    // var dbList = emptyList<Int>() // from target
    val mvrs = mutableListOf<MvrMergedComparison>() // allows duplicates, but check for overrides
    private val mvrMap = mutableMapOf<Int, MvrMergedComparison>()   // dbid -> MvrMergedInfo

    var winner: String? = null
    var loser: String? = null

    // to calculate this we need to know the MinAssertion: winner and runner up
    var p2o: Int = 0 // twoOver
    var p1o: Int = 0 // oneOver
    var p1u: Int = 0 // oneUnder
    var p2u: Int = 0 // twoUnder

    // these are only for the target contests ... do we care here ??

    fun addActivities(activityList: List<ActivityReport>) {
        activityList.forEach {
            val mvr = MvrMergedComparison(it.dbId, it.county, it.imprintedId, it.discrepency, it.revision, this.auditReason, it.timeOfSubmission)
            mvrs.add(mvr)
            mvrMap[mvr.dbId] = mvr
        }
    }

    // add to mvrs
    fun addResults(resultsList: List<ResultsReport>) {
        resultsList.forEach {
            val mvr = mvrMap[it.dbId]
            if (mvr != null) {
                require(mvr.imprintedId == it.imprintedId)
                require(mvr.dbId == it.dbId)
                require(mvr.county == it.county)
                mvr.prn = it.randomNumber
                mvr.seq = it.sequence
                mvr.mult = it.multiplicity
            }
        }
    }

    fun addContestWinner(contestTab: TabulateContest) {
        val sortedChoices = contestTab.choices.toList().sortedByDescending { it.second }
        this.winner = sortedChoices[0].first
        this.loser = sortedChoices[1].first
    }

    fun processLine(line: MvrComparison) {
        val error = classifyDiscrepency(line)
        when (error) {
            ErrorType2.p2o -> this.p2o++
            ErrorType2.p1o -> this.p1o++
            ErrorType2.p1u -> this.p1u++
            ErrorType2.p2u -> this.p2u++
            else -> {}
        }
    }

    // TODO test assignments
    fun classifyDiscrepency(mvr: MvrComparison): ErrorType2? {
        val cvrChoice = mvr.choice_per_voting_computer
        val mvrChoice = mvr.audit_board_selection
        if (cvrChoice == mvrChoice) return null
        if (cvrChoice == this.winner) {
            if (mvrChoice == this.loser) return ErrorType2.p2o
            else return ErrorType2.p1o
        } else if (cvrChoice == this.loser) {
            if (mvrChoice == this.winner) return ErrorType2.p2u
            else return ErrorType2.p1u
        } else {
            if (mvrChoice == this.loser) return ErrorType2.p1o
            if (mvrChoice == this.winner) return ErrorType2.p1u
            return null
        }
    }
}

enum class ErrorType2 {p2o, p1o, p1u, p2u }

class ContestMvrComparisons(val stateInput: StatewideInput, minfos: List<ContestMergedInfo>) {
    val mcontests: Map<String, ContestMvrComparison>

    init {
        // use minfos in order to get the canonical names
        mcontests = minfos.map { minfo ->
            ContestMvrComparison(minfo.contestName, minfo.counties, minfo.auditReason, minfo.corlaPop)
        }.associateBy { it.contestName }

        // Activities have the Mvrs
        if (stateInput.activityReportFile() != null) {
            val activities = ActivityReport.readFromFile(stateInput.activityReportFile()!!)
            activities.forEach { (contestName, activityList) ->
                val ccname = stateInput.convertContestName("ActivityReport", contestName)
                if (ccname != null) {
                    val mcontest = mcontests[ccname]!!
                    mcontest.addActivities(activityList)
                }
            }
            println("added ${activities.size} target contests")
        }

        // Results add prn, seq, mult to the Mvrs
        if (stateInput.resultsReportFile != null) {
            val results = ResultsReport.readFromFile(stateInput.resultsReportFile!!)
            results.forEach { (contestName, resultsList) ->
                val ccname = stateInput.convertContestName("ResultsReport", contestName)
                if (ccname != null) {
                    val mcontest = mcontests[ccname]!!
                    mcontest.addResults(resultsList)
                }
            }
            println("added ${results.size} target contests")
        }

        // add contest tabs
        val countyTabs = TabulateCounty.readFromFile(stateInput.tabulateCountyFile, stateInput )
        val contestTabs = TabulateContest.fromTabulateCounty(countyTabs.values.toList())
        contestTabs.values.forEach { contestTab ->
            val ccname = stateInput.convertContestName("TabulateContest", contestTab.contestName)
            if (ccname != null) {
                val mcontest = mcontests[ccname]!!
                mcontest.addContestWinner(contestTab)
            }
        }

        // process comparison lines
        val lines: List<MvrComparison> = MvrComparison.readFromFile(stateInput.mvrComparisonFile)
        lines.forEach { line ->
            val ccname = stateInput.convertContestName("MvrComparison", line.contest_name)
            if (ccname != null) {
                val mcontest = mcontests[ccname]!!
                mcontest.processLine(line)
            }
        }
    }

}