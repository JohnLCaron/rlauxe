package org.cryptobiotic.rlauxe.co.auditcenter

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.auditcenter.CanonicalContest
import org.cryptobiotic.rlauxe.co.corla.CorlaPrng
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.util.Locations.auditcenter

private val logger = KotlinLogging.logger("ContestMergedInfo")

data class MvrMergedInfo(
    val dbId: Int,
    val county: String,
    val imprintedId: String,
    val prn: Int,
    val seq: Int,
    val mult: Int,
) {
    val counties = mutableSetOf<String>()
    var lines: List<MvrComparison>? = null
    var auditReason = ""
    override fun toString(): String {
        return "MvrMergedInfo(dbId=$dbId, county='$county', imprintedId='$imprintedId', prn=$prn, seq=$seq, mult=$mult, auditReason='$auditReason' counties=$counties)"
    }
}

data class ContestMergedInfo(
    val contest: String,
    val dbList: List<Int>, // could be Int
) {
    val mvrMap = mutableMapOf<Int, MvrMergedInfo>()   // dbid -> MvrMergedInfo
    var ballotCardCount: Int = 0
    var auditReason = ""
    var counties: Set<String> = emptySet()

    // set before results
    fun addContestRound(round: ContestRound) {
        if (ballotCardCount == 0)
            ballotCardCount = round.ballot_card_count
        else
            println("duplicate round ??")
        auditReason = round.audit_reason
    }

    fun addResults(resultsList: List<ResultsReport>) {
        resultsList.forEach {
            var mvr = mvrMap[it.dbId]
            if (mvr == null) {
                mvr = MvrMergedInfo(it.dbId, it.county, it.imprintedId, it.randomNumber, it.sequence, it.multiplicity)
                mvrMap[it.dbId] = mvr
            } else {
                val should = MvrMergedInfo(it.dbId, it.county, it.imprintedId, it.randomNumber, it.sequence, it.multiplicity)
                require (mvr.imprintedId == it.imprintedId)
                require (mvr.prn == it.randomNumber)
                // require (mvr.seq == it.sequence)
            }
            mvr.counties.add(it.county)
            mvr.auditReason = this.auditReason
        }
    }

    fun addCounties(counties: Set<String>) {
        this.counties = counties
    }

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
            println("county not set on $contest")
            failed = true
        }

        return failed
    }
}

class ContestMergedInfos(val stateInput: StatewideInput) {
    val mcontests: Map<String, ContestMergedInfo>

    init {
        // kludge 2026 for the moment
        val selected: Map<String, ContestSelection> = ContestSelection.readFromFile("$auditcenter/2026/general/files/round2/contestSelection.csv")
        val results = ResultsReport.readFromFile("$auditcenter/2026/general/files/round2/resultsReport.xlsx")
        val rounds: Map<String, ContestRound> = ContestRound.readFromFile(stateInput.contestRoundFile).associateBy { it.contest_name }
        val canon: Map<String, CanonicalContest> = stateInput.canonicalContests()

        val linesById: Map<String, List<MvrComparison>> = MvrComparison.readFromFile(stateInput.mvrComparisonFile).groupBy { it.imprinted_id }

        // ContestSelection have list of dbids
        mcontests = selected.mapValues { (_, select) ->
            ContestMergedInfo(select.contest_name, select.dbList)
        }

        /// contest rounds have ballot_card_count and audit_reason
        rounds.entries.forEach { entry ->
            val mcontest = mcontests[entry.key]
            if (null != mcontest) {
                mcontest.addContestRound(entry.value)
            }
        }

        // Results have contest name -> mvr
        results.forEach { (contestName, resultsList) ->
            val mcontest = mcontests[contestName]!!
            mcontest.addResults(resultsList)
        }

        /// contest rounds have list of counties
        canon.entries.forEach { entry ->
            val mcontest = mcontests[entry.key]
            if (null != mcontest) {
                val counties = entry.value.counties
                // if (counties.size != 1) println("mcontest ${entry.key} has counties = ${counties}")
                mcontest.addCounties(counties)
            }
        }

        // add lines
        mcontests.values.forEach { mcontest ->
           mcontest.mvrMap.values.forEach { mvr ->
               mvr.lines = linesById[mvr.imprintedId]
           }
        }

        // check ok
        val anyfailed = mcontests.values.any { it.check() }
        if (anyfailed) {
            val anyfailed = mcontests.values.any { it.check() }
            logger.warn{ "anyfailed = $anyfailed" }
        }
    }

    fun getMvrsForCounty(county: String): List<MvrMergedInfo> {
        val result = mutableSetOf<MvrMergedInfo>()
        mcontests.values.forEach { mcontest ->
            if (mcontest.counties.contains(county)) {
                result.addAll(mcontest.mvrMap.values.filter { it.counties.contains(county) })
            }
        }
        return result.toList() // TODO sort on imprinted id
    }

    fun testSequences(): Boolean {
        // test sequences for all targeted ballots
        var failed = false
        mcontests.values.forEach { mcontest ->
            println("${mcontest.contest} (${mcontest.mvrMap.size})")
            val prng = CorlaPrng(stateInput.seed, with_replacement = true, 1, mcontest.ballotCardCount)
            val prns = mutableListOf<Int>()
            repeat(mcontest.dbList.size) { prns.add(prng.next()) }

            // val prns = prng.getRandomNumbers(0, mcontest.dbList.size-1)

            val mvrMapPrn = mcontest.mvrMap.mapKeys { it.value.prn }

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
    }
}