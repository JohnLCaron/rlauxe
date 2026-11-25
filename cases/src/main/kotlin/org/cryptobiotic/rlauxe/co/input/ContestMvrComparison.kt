package org.cryptobiotic.rlauxe.co.input

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.auditcenter.AuditReason
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

data class CountyMvrComparison(val county: String) {
    val mvrs = mutableListOf<MvrMergedComparison>() // allows duplicates for sequence chanking
    val mvrMap = mutableMapOf<Int, MvrMergedComparison>()   // dbid -> MvrMergedInfo

    fun addActivity(activity: ActivityReport, auditReason: AuditReason) {
        val mvr = MvrMergedComparison(
            activity.dbId,
            activity.county,
            activity.imprintedId,
            activity.discrepency,
            activity.revision,
            auditReason,
            activity.timeOfSubmission
        )
        mvrs.add(mvr)
        mvrMap[mvr.dbId] = mvr
    }

    fun addResult(result: ResultsReport) {
        val mvr = mvrMap[result.dbId]
        if (mvr != null) {
            require(mvr.imprintedId == result.imprintedId)
            require(mvr.dbId == result.dbId)
            require(mvr.county == result.county)
            mvr.prn = result.randomNumber
            mvr.seq = result.sequence
            mvr.mult = result.multiplicity
        }
    }

    fun addLine(line: MvrComparison) {
        val mvr = mvrMap[line.cvr_id]
        if (mvr != null) {
            mvr.lines.add(line)
        }  else
            logger.warn {"dont have line.cvr_id ${line.cvr_id}"}
    }
}


data class MvrMergedComparison(
    // constructor params are from Activity
    val dbId: Int,
    val county: String,
    val imprintedId: String,
    val discrepency: String, // what does this mean anyway ?? probably bogus
    val revision: String,
    val auditReason: AuditReason, // taken from contest, but duplicate when county targets multiple contests
    val timeOfSubmission: String,
) {
    // from result needed?
    var prn: Int = 0
    var seq: Int = 0
    var mult: Int = 1 // default; revised dont get set

    // optional, from mvrComparisonFile; // TODO are these names canonical ??
    val lines = mutableListOf<MvrComparison>()

    override fun toString(): String {
        return "MvrMergedComparison(dbId=$dbId, '$county', '$imprintedId', prn=$prn, seq=$seq, '$auditReason' $timeOfSubmission, discrepency='$discrepency', revision='$revision')"
    }
}

// one for each contest
data class ContestMvrComparison(val minfo: ContestMergedInfo) {
    // var dbList = emptyList<Int>() // from target
    //val mvrs = mutableListOf<MvrMergedComparison>() // allows duplicates for sequence chanking
    //val mvrMap = mutableMapOf<Int, MvrMergedComparison>()   // dbid -> MvrMergedInfo

    var winner: String? = null
    var loser: String? = null

    // to calculate this we need to know the MinAssertion: winner and runner up
    var p2o: Int = 0 // twoOver
    var p1o: Int = 0 // oneOver
    var p1u: Int = 0 // oneUnder
    var p2u: Int = 0 // twoUnder

    fun hasErrors(): Boolean {
        return p2o > 0 || p1o > 0 || p1u > 0 || p2u > 0
    }

    fun addContestWinner(contestTab: TabulateContest) {
        val sortedChoices = contestTab.choices.toList().sortedByDescending { it.second }
        this.winner = sortedChoices[0].first
        if (sortedChoices.size > 1) this.loser = sortedChoices[1].first
    }

    fun processLine(stateInput: StatewideInput, line: MvrComparison) {
        if (this.winner == null || this.loser == null) return
        val county = line.county_name
        val rawCvrChoice = line.choice_per_voting_computer
        val cvrChoice =
            stateInput.matchCanonicalCandidate(county, this.minfo.canonicalContest, rawCvrChoice) ?: rawCvrChoice

        val rawMvrChoice = line.audit_board_selection
        val mvrChoice =
            stateInput.matchCanonicalCandidate(county, this.minfo.canonicalContest, rawMvrChoice) ?: rawMvrChoice

        val error = classifyDiscrepency(mvrChoice, cvrChoice, this.winner!!, this.loser!!)
        when (error) {
            ErrorType2.p2o -> this.p2o++
            ErrorType2.p1o -> this.p1o++
            ErrorType2.p1u -> this.p1u++
            ErrorType2.p2u -> this.p2u++
            else -> {}
        }
    }
}

fun classifyDiscrepency(mvrChoice: String, cvrChoice: String, winner: String, loser: String): ErrorType2? {
    if (cvrChoice == mvrChoice) return null
    if (mvrChoice == winner) {
        if (cvrChoice == loser) return ErrorType2.p2u
        else return ErrorType2.p1u
    } else if (mvrChoice == loser) {
        if (cvrChoice == winner) return ErrorType2.p2o
        else return ErrorType2.p1o
    } else {
        if (cvrChoice == loser) return ErrorType2.p1u
        if (cvrChoice == winner) return ErrorType2.p1o
        return null
    }
}

enum class ErrorType2 {p2o, p1o, p1u, p2u }

private val show = false

class ContestMvrComparisons(val stateInput: StatewideInput, minfos: List<ContestMergedInfo>, keepLines:Boolean) {
    val mcontests: Map<String, ContestMvrComparison>
    val mcounties = mutableMapOf<String, CountyMvrComparison>() // allows duplicates for sequence chanking

    init {
        // use minfos in order to get the canonical names
        mcontests = minfos.map { minfo -> ContestMvrComparison(minfo) }.associateBy { it.minfo.contestName }

        // Activities have the Mvrs
        if (stateInput.activityReportFile() != null) {
            val activities = ActivityReport.readFromFile(stateInput.activityReportFile()!!)
            activities.forEach { (contestName, activityList) ->
                val ccname = stateInput.convertContestName("ActivityReport", contestName)
                if (ccname != null) {
                    val mcontest = mcontests[ccname]!!
                    activityList.forEach { activity ->
                        if (!stateInput.skipCounties(activity.county)) {
                            val mcounty = mcounties.getOrPut(activity.county) { CountyMvrComparison(activity.county) }
                            mcounty.addActivity(activity, mcontest.minfo.corlaReason)
                        }
                    }
                }
            }
            if (show) println("added ${activities.size} target contests")
        }

        // Results add prn, seq, mult to the Mvrs
        if (stateInput.resultsReportFile != null) {
            val results = ResultsReport.readFromFile(stateInput.resultsReportFile!!)
            results.forEach { (contestName, resultsList) ->
                resultsList.forEach { result ->
                    if (!stateInput.skipCounties(result.county)) {
                        val mcounty = mcounties.getOrPut(result.county) { CountyMvrComparison(result.county) }
                        mcounty.addResult(result)
                    }
                }
            }
            if (show) println("added ${results.size} target contests")
        }

        // add contest tabs
        val countyTabs = TabulateCounty.readFromFile(stateInput.tabulateCountyFile, stateInput )
        val contestTabs = TabulateContest.fromTabulateCounty(countyTabs)
        contestTabs.values.forEach { contestTab ->
            val mcontest = mcontests[contestTab.contestName]!!
            mcontest.addContestWinner(contestTab)
        }

        // process comparison lines
        val lines: List<MvrComparison> = MvrComparison.readFromFile(stateInput.mvrComparisonFile)
        lines.forEach { line ->
            val ccname = stateInput.convertContestName("MvrComparison", line.contest_name) // TODO convert in MvrComparison?
            if (ccname != null) {
                val mcontest = mcontests[ccname]!!
                mcontest.processLine(stateInput, line)
            } else {
                logger.warn{ "cant find contest ${line.contest_name} from ${line.cvr_id}"}
            }
            if (keepLines) {
                val mcounty = mcounties[line.county_name]!!
                mcounty.addLine(line)
            }
        }
    }

    fun getMvrsForCounty(county: String): List<MvrMergedComparison> {
        val mcounty = mcounties[county]!!
        return mcounty.mvrMap.values.toList() // or mvrs ??
    }

}