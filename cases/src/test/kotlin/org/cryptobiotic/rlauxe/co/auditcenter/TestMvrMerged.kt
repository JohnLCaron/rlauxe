package org.cryptobiotic.rlauxe.co.auditcenter

import org.cryptobiotic.rlauxe.auditcenter.CanonicalContest
import org.cryptobiotic.rlauxe.corla.CorlaPRNG
import org.cryptobiotic.rlauxe.corla.CorlaPrng
import org.cryptobiotic.rlauxe.corlaInput.Statewide2026General
import org.cryptobiotic.rlauxe.util.Locations.auditcenter
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMvrMerged {
    val targetContests: Map<String, TargetContest> = TargetContest.readFromFile("$auditcenter/2026/general/files/2026GeneralRLAContestList.xlsx").associateBy { it.contestName }
    val results = ResultsReport.readFromFile("$auditcenter/2026/general/files/round1/resultsReport.xlsx")
    // val activities: Map<String, List<ActivityReport>> = ActivityReport.readFromFile("$auditcenter/2026/general/files/round1/activityReport.xlsx") // contest name -> mvr
    val rounds: Map<String, ContestRound> = ContestRound.readFromFile("$auditcenter/2026/general/files/round1/contest.csv").associateBy { it.contest_name }
    val selected: Map<String, ContestSelection> = ContestSelection.readFromFile("$auditcenter/2026/general/files/round1/contestSelection.csv")

    val lines: List<MvrComparison> = MvrComparison.readFromFile("$auditcenter/2026/general/files/round1/contestComparison.csv")
    val mvrsByContest: Map<String, Map<String, List<MvrComparison>>>  = MvrComparison.mvrByContestAndCounties(lines)   // contest -> county -> lines
    // val mvrs: Map<String, Map<String, List<MvrComparison>>>  = MvrComparison.mvrs(lines)   // county -> imprintedId -> lines

    val stateInput = Statewide2026General()
    val canon: Map<String, CanonicalContest> = stateInput.canonicalContests()

    @Test
    fun testContestNames() {

        var fail = false
        targetContests.entries.forEach { entry ->
            val cc = stateInput.matchCanonicalContest(entry.value.countyName, entry.key)
            if (null == cc) {
                println("no canon for target contest ${entry.key}")
                fail = true
            }
        }
        println()

        results.entries.forEach { entry ->
            if (null == stateInput.matchCanonicalContest(entry.key)) {
                println("no canon for activities contest ${entry.key}")
                fail = true
            }
        }
        println()

        rounds.entries.forEach { entry ->
            if (null == stateInput.matchCanonicalContest(entry.key)) {
                println("no canon for rounds contest ${entry.key}")
                fail = true
            }
        }

        selected.entries.forEach { entry ->
            if (null == stateInput.matchCanonicalContest(entry.key)) {
                println("no canon for selected contest ${entry.key}")
                fail = true
            }
        }
        println()

        mvrsByContest.entries.forEach { entry ->
            if (null == stateInput.matchCanonicalContest(entry.key)) {
                println("no canon for mvrs contest ${entry.key}")
                fail = true
            }
        }
        if (fail) throw RuntimeException("Failed")
    }


    data class MvrMergedInfo(
        val dbId: Int,
        val imprintedId: String,
        val prn: Int,
        val seq: Int,
        val mult: Int,
    )

    data class ContestMergedInfo(
        val contest: String,
        val dbList: List<Int>, // could be Int
    ) {
        val mvrMap = mutableMapOf<Int, MvrMergedInfo>()   // dbid -> MvrMergedInfo
        var ballotCardCount: Int = 0
        var counties: Set<String> = emptySet()

        /*fun addActivites(activityList: List<ActivityReport>) {
            activityList.forEach {
                val db2id = db2ids[it.dbId]
                if (db2id == null) db2ids[it.dbId] = it.imprintedId
                    else require(db2id == it.imprintedId)
            }
        } */

        fun addResults(resultsList: List<ResultsReport>) {
            resultsList.forEach {
                val mvr = mvrMap[it.dbId]
                if (mvr == null) mvrMap[it.dbId] = MvrMergedInfo(it.dbId, it.imprintedId, it.randomNumber, it.sequence, it.multiplicity)
                else {
                    val should = MvrMergedInfo(it.dbId, it.imprintedId, it.randomNumber, it.sequence, it.multiplicity)
                    require (mvr.imprintedId == it.imprintedId)
                    require (mvr.prn == it.randomNumber)
                    // require (mvr.seq == it.sequence)
                }
            }
        }
/* fun addMvrComparison(comparison: Collection<MvrComparison>) {
            comparison.forEach {
                val db2id = db2ids[it.cvr_id]
                if (db2id == null) db2ids[it.cvr_id] = it.imprinted_id
                else require(db2id == it.imprinted_id)
            }
        } */

        fun addContestRound(round: ContestRound) {
            if (ballotCardCount == 0)
                ballotCardCount = round.ballot_card_count
            else
                println("duplicate round ??")
        }

        fun addCounties(counties: Set<String>) {
            this.counties = counties
        }

        fun check() {
            dbList.forEach {
                val db2id = mvrMap[it]
                if (db2id == null)
                    println("$it not in mvrMap")
            }
            mvrMap.keys.forEach {
                if (!dbList.contains(it))
                    println("$it not in dbList")
            }
            if (ballotCardCount == 0)
                println("ballotCardCount not set")
            if (counties.isEmpty())
                println("county not set on $contest")
        }
    }

    @Test
    fun testContestMerged() {

        // ContestSelection have list of dbids
        val mcontests = selected.mapValues { (_, select) ->
            ContestMergedInfo(select.contest_name, select.dbList)
        }

        /* Activities have contest name -> mvr // TODO resultsReport
        activities.forEach { (contestName, activityList) ->
            val mcontest = mcontests[contestName]!!
            mcontest.addActivites(activityList)
        } */

        // Results have contest name -> mvr // TODO resultsReport
        results.forEach { (contestName, resultsList) ->
            val mcontest = mcontests[contestName]!!
            mcontest.addResults(resultsList)
        }

        /* mvrsByContest contest -> county -> lines
        mvrsByContest.forEach { (contestName, countyMap) ->
            val mcontest = mcontests[contestName]
            if (null != mcontest) {
                println("$contestName: mvrsByContest ncounties = ${countyMap.size}")
                countyMap.values.forEach { mcontest.addMvrComparison(it) }
            } else {
                // println("missing $contestName: with ncounties = ${countyMap.size}")
                /* countyMap.values.forEach {
                    val dbids = it.map { it.cvr_id }
                    println("   dbids = $dbids")
                } */
            }
        } */

        rounds.entries.forEach { entry ->
            val mcontest = mcontests[entry.key]
            if (null != mcontest) {
                mcontest.addContestRound(entry.value)
            }
        }

        canon.entries.forEach { entry ->
            val mcontest = mcontests[entry.key]
            if (null != mcontest) {
                val counties = entry.value.counties
                // if (counties.size != 1) println("mcontest ${entry.key} has counties = ${counties}")
                mcontest.addCounties(counties)
            }
        }


        // activites fail, results pass
        mcontests.values.forEach { it.check() }

        println("ok so far")

        mcontests.values.forEach { mcontest ->
            if (mcontest.counties.size > 1)
                print("")
            println("${mcontest.contest} (${mcontest.mvrMap.size})")
            val prng = CorlaPRNG(stateInput.seed, with_replacement = true, 1, mcontest.ballotCardCount)
            val prns = prng.getRandomNumbers(0, mcontest.dbList.size-1)

            val mvrMapPrn = mcontest.mvrMap.mapKeys { it.value.prn }

            val mvrs = mcontest.mvrMap.values.sortedBy { it.seq }
            prns.forEachIndexed { idx, prn ->
                val mvr = mvrMapPrn[prn]
                if (mvr == null)
                    println("$idx: $prn")
                else {
                    println("($idx):   $prn ${mvr.prn} $mvr")
                    assertEquals(prn, mvr.prn)
                }
            }

        }


    }
}