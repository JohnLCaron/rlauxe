package org.cryptobiotic.rlauxe.co.input

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.auditcenter.AuditReason
import org.cryptobiotic.rlauxe.co.auditcenter.ActivityReport
import org.cryptobiotic.rlauxe.co.auditcenter.ContestRound
import org.cryptobiotic.rlauxe.co.auditcenter.MvrComparison
import org.cryptobiotic.rlauxe.co.auditcenter.ResultsReport
import org.cryptobiotic.rlauxe.co.auditcenter.TabulateContest
import org.cryptobiotic.rlauxe.co.auditcenter.TabulateCounty
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput

private val logger = KotlinLogging.logger("ContestMvrMergedInfo")

// Originally to check that the sequence of Draws was correct. Go back to that purpose
// Count things by contest - move to ContestMvrComparison ....

data class MvrMergedInfo(
    // constructor params are from Activity
    val dbId: Int,
    val county: String,
    val imprintedId: String,
    val discrepency: String, // what does this mean anyway ?? probably bogus
    val revision: String,
    val auditReason: AuditReason,
    val timeOfSubmission: String,
) {
    // from result
    var prn: Int = 0
    var seq: Int = 0
    var mult: Int = 1 // default; revised dont get set

    // optional, from mvrComparisonFile; // TODO are these names canonical ??
    var lines: List<MvrComparison>? = null

    override fun toString(): String {
        return "MvrMergedInfo(dbId=$dbId, '$county', '$imprintedId', prn=$prn, seq=$seq, '$auditReason' $timeOfSubmission, discrepency='$discrepency', revision='$revision')"
    }
}

data class ContestMvrMergedInfo(
    val contestName: String,
    val counties: Set<String>,
    val auditReason: AuditReason,
    val ballotCardCount: Int,
) {
    // var dbList = emptyList<Int>() // from target
    val mvrs = mutableListOf<MvrMergedInfo>() // allows duplicates, but check for overrides
    private val mvrMap = mutableMapOf<Int, MvrMergedInfo>()   // dbid -> MvrMergedInfo

    var winner: String? = null
    var loser: String? = null

    // to calculate this we need to know the MinAssertion: winner and runner up
    var p2o: Int = 0 // twoOver
    var p1o: Int = 0 // oneOver
    var p1u: Int = 0 // oneUnder
    var p2u: Int = 0 // twoUnder

    fun addActivities(activityList: List<ActivityReport>) {
        activityList.forEach {
            val mvr = MvrMergedInfo(it.dbId, it.county, it.imprintedId, it.discrepency, it.revision, this.auditReason, it.timeOfSubmission)
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

    fun countErrors() {
        mvrs.forEach { mvr ->
            mvr.lines!!.forEach { line ->
                val error = classifyDiscrepency(line)
                when (error) {
                    ErrorType.p2o -> this.p2o++
                    ErrorType.p1o -> this.p1o++
                    ErrorType.p1u -> this.p1u++
                    ErrorType.p2u -> this.p2u++
                    else -> {}
                }
            }
        }
    }

    // TODO test assignments
    fun classifyDiscrepency(mvr: MvrComparison): ErrorType? {
        val cvrChoice = mvr.choice_per_voting_computer
        val mvrChoice = mvr.audit_board_selection
        if (cvrChoice == mvrChoice) return null
        if (cvrChoice == this.winner) {
            if (mvrChoice == this.loser) return ErrorType.p2o
            else return ErrorType.p1o
        } else if (cvrChoice == this.loser) {
            if (mvrChoice == this.winner) return ErrorType.p2u
            else return ErrorType.p1u
        } else {
            if (mvrChoice == this.loser) return ErrorType.p1o
            if (mvrChoice == this.winner) return ErrorType.p1u
            return null
        }
    }

    /*
    fun check(): Boolean {
        var failed = false
        dbList.forEach {
            val db2id = mvrMap[it]
            if (db2id == null) {
                println("$it not in mvrMap")
                failed = true
            }
        }
        mvrMap.keys.forEach {
            if (!dbList.contains(it)) {
                println("$it not in dbList")
                failed = true
            }
         }
        if (ballotCardCount == 0) {
            println("ballotCardCount not set")
            failed = true
        }
        if (counties.isEmpty()) {
            println("county not set on $contestName")
            failed = true
        }

        return failed
    } */
}

enum class ErrorType {p2o, p1o, p1u, p2u }

// data class ContestMergedInfo(
//    // canonical
//    val canonicalContest: CanonicalContest,
//    val contestName: String,
//    val choices: List<String>, // canonical choice names
//    val counties: Set<String>,
//
//    /// contest round
//    val auditReason: AuditReason,
//    val corlaPop:Int,   // ballot_card_count = corla population
//    val corlaNc:Int,    // contest_ballot_card_count
//    val voteForN: Int,  // winners_allowed
//    val estSamples: Int,  // estimated_samples_to_audit
//    val marginInVotes: Int, // min_margin
//    val riskLimit: Double, // risk_limit
//
//    // mvr and contestRounds
//    val strata: StrataInfo,
//)
class ContestMvrMergedInfos(val stateInput: StatewideInput, minfos: List<ContestMergedInfo>, withTabs: Boolean, withLines: Boolean) {
    val mcontests: Map<String, ContestMvrMergedInfo>

    constructor(stateInput: StatewideInput, withTabs: Boolean, withLines: Boolean): this(stateInput, stateInput.mergedContestMap.values.toList(), withTabs, withLines)

    init {

        // use stateInput.mergedContest in order to get the canonical names
        mcontests = minfos.map { minfo ->
            ContestMvrMergedInfo(minfo.contestName, minfo.counties, minfo.auditReason, minfo.corlaPop)
        }.associateBy { it.contestName }

        /* ContestSelection have list of dbids
        val selected: Map<String, ContestSelection> = ContestSelection.readFromFile(stateInput.contestSelectionFile)
        mcontests = selected.mapValues { (_, select) ->
            ContestMvrMergedInfo(select.contest_name, select.dbList)
        } */

        // Activities have the Mvrs
        if (stateInput.activityReportFile() != null) {
            val activities = ActivityReport.readFromFile(stateInput.activityReportFile()!!)
            activities.forEach { (contestName, activityList) ->
                val mcontest = mcontests[contestName]!!
                mcontest.addActivities(activityList)
            }
            println("added ${activities.size} target contests")
        }

        // Results add prn, seq, mult to the Mvrs
        if (stateInput.resultsReportFile != null) {
            val results = ResultsReport.readFromFile(stateInput.resultsReportFile!!)
            results.forEach { (contestName, resultsList) ->
                val mcontest = mcontests[contestName]!!
                mcontest.addResults(resultsList)
            }
            println("added ${results.size} target contests")
        }

        // add contest tabs
        if (withTabs && (stateInput.tabulateCountyFile() != null)) {
            val countyTabs = TabulateCounty.readFromFile(stateInput.tabulateCountyFile()!!, stateInput )
            val contestTabs = TabulateContest.fromTabulateCounty(countyTabs.values.toList())
            contestTabs.values.forEach { contestTab ->
                val mcontest = mcontests[contestTab.contestName]!!
                mcontest.addContestWinner(contestTab)
            }
        }

        // add comparison lines
        if (withLines) {
            val linesById: Map<Int, List<MvrComparison>> = MvrComparison.readFromFile(stateInput.mvrComparisonFile).groupBy { it.cvr_id }
            mcontests.values.forEach { mcontest ->
                mcontest.mvrs.forEach { mvr ->
                    mvr.lines = linesById[mvr.dbId]
                }
            }
        }

        if (withTabs && withLines) {
            mcontests.values.forEach { mcontest ->
                mcontest.countErrors()
            }
        }

        /* check ok
        val anyfailed = mcontests.values.any { it.check() }
        if (anyfailed) {
            val anyfailed = mcontests.values.any { it.check() }
            logger.warn{ "anyfailed = $anyfailed" }
        } */
    }

    fun getMvrsForCounty(county: String): List<MvrMergedInfo> {
        val result = mutableListOf<MvrMergedInfo>()
        mcontests.values.forEach { mcontest ->
            if (mcontest.counties.contains(county)) {
                result.addAll(mcontest.mvrs.filter { it.county == county })
            }
        }
        return result.toList()
    }

    fun getStatewideMvrCount(): Map<String, Int> { // county -> count of statewide mvrs
        val unique = mutableSetOf<MvrMergedInfo>()

        mcontests.values.forEach { mcontest ->
            mcontest.mvrs.filter { it.auditReason == AuditReason.state_wide_contest }.forEach {
                unique.add(it)
            }
        }
        logger.info {
            buildString {
                appendLine("unique statewideMvrs (${unique.size})")
                unique.forEach { appendLine("  $it") }
            }
        }

        val counts = mutableMapOf<String, Int>()
        unique.forEach {
            val count = counts.getOrDefault(it.county, 0)
            counts[it.county] = count + 1
        }
        return counts
    }

    /*
    fun testSequences(): Boolean {
        // test sequences for all targeted ballots
        var failed = false
        mcontests.values.forEach { mcontest ->
            println("${mcontest.contestName} (${mcontest.mvrs.size})")
            val prng = CorlaPrng(stateInput.seed, with_replacement = true, 1, mcontest.ballotCardCount)
            val prns = mutableListOf<Int>()
            repeat(mcontest.dbList.size) { prns.add(prng.next()) }

            // val prns = prng.getRandomNumbers(0, mcontest.dbList.size-1)

            val mvrMapPrn = mcontest.mvrs.associateBy { it.prn }

            prns.forEachIndexed { idx, prn ->
                val mvr = mvrMapPrn[prn]
                if (mvr == null) {
                    println("$idx: $prn mvr is null")
                    failed = true
                } else {
                    if (prn != mvr.prn) {
                        println("($idx):   $prn ${mvr.prn} $mvr NOT EQUAL")
                        failed = true
                    }
                }
            }

        }
        return failed
    } */

    companion object {
        // to complete the merging
        fun makeContestMvrInfos(stateInput: StatewideInput, mergedContestInfos: List<ContestMergedInfo>): ContestMvrMergedInfos {
            return ContestMvrMergedInfos(stateInput, mergedContestInfos, false, false)
        }
    }
}