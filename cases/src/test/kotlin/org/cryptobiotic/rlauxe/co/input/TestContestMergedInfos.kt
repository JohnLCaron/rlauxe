package org.cryptobiotic.rlauxe.co.input

import org.cryptobiotic.rlauxe.co.auditcenter.ContestRound
import org.cryptobiotic.rlauxe.co.auditcenter.ContestSelection
import org.cryptobiotic.rlauxe.co.auditcenter.MvrComparison
import org.cryptobiotic.rlauxe.co.auditcenter.ResultsReport
import org.cryptobiotic.rlauxe.co.corla.CorlaPrng
import org.cryptobiotic.rlauxe.corlaInput.Co2020G
import org.cryptobiotic.rlauxe.corlaInput.Statewide2026General
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.corlaInput.statewideInputs
import kotlin.test.Test
import kotlin.test.assertEquals

class TestContestMergedInfos {
    val testInput = Co2020G()
    @Test
    fun testOneNames() {
        testContestNames(testInput)
    }

    @Test
    fun testAllNames() {
        statewideInputs.forEach {
            testContestNames(it)
        }
    }

    fun testContestNames(stateInput: StatewideInput) {
        var fail = false
        println("\nStateInput = ${stateInput.name()}")

        /*
        if (stateInput.targetContestsFile != null) {
            val targetContests: Map<String, TargetContest> =
                TargetContest.readFromFile(stateInput.targetContestsFile!!).associateBy { it.contestName }

            println("  TargetContest")
            targetContests.entries.forEach { entry ->
                val cc = stateInput.matchCanonicalContest(entry.value.countyName, entry.key)
                if (null == cc) {
                    stateInput.matchCanonicalContest(entry.value.countyName, entry.key)
                    println("no canon for target contest ${entry.key}")
                    //fail = true
                }
            }
        } */

        val rounds: Map<String, ContestRound> = ContestRound.readFromFile(stateInput.contestRoundFile, stateInput).associateBy { it.contest_name }
        println("  ContestRound")
        rounds.entries.forEach { entry ->
            if (null == stateInput.matchCanonicalContest(entry.key)) {
                println("no canon for rounds contest ${entry.key}")
                fail = true
            }
        }

        println("  ResultsReport")
        val results = if (stateInput.resultsReportFile != null) ResultsReport.readFromFile(stateInput.resultsReportFile!!) else null
        if (results != null) {
            results.entries.forEach { entry ->
                if (null == stateInput.matchCanonicalContest(entry.key)) {
                    println("no canon for results report ${entry.key}")
                    fail = true
                }
            }
        }

        println("  ContestSelection")
        val selected: Map<String, ContestSelection> = ContestSelection.readFromFile(stateInput.contestSelectionFile)
        selected.entries.forEach { entry ->
            if (null == stateInput.matchCanonicalContest(entry.key)) {
                println("no canon for selected contest ${entry.key}")
                fail = true
            }
        }

        println("  MvrComparison") // test the contest names in the comparison file
        val lines: List<MvrComparison> = MvrComparison.readFromFile(stateInput.mvrComparisonFile)
        val mvrsByContest: Map<String, Map<String, List<MvrComparison>>>  = MvrComparison.mvrByContestAndCounties(lines)   // contest -> county -> lines
        mvrsByContest.keys.forEach { key ->
            if (null == stateInput.matchCanonicalContest(key)) {
                println("no canon for mvrs contest ${key}")
                fail = true
            }
        }

        // if (fail) throw RuntimeException("Failed")
    }

    @Test
    fun testOneContestMergedInfos() {
        testContestMergedInfos(testInput)
    }

    @Test
    fun testAllContestMergedInfos() {
        statewideInputs.forEach {
            testContestMergedInfos(it)
        }
    }

    fun testContestMergedInfos(stateInput: StatewideInput) {

        val mvrInfos = ContestMvrMergedInfos(stateInput, true, true)

        // test sequences for all targeted ballots
        mvrInfos.mcontests.values.filter { it.contestName.startsWith("Governor") }.forEach { mcontest ->
            println("ContestMvrMergedInfo for ${mcontest.contestName} mvrs=${mcontest.mvrs.size} maximumIndex=${mcontest.ballotCardCount} ")
            val prng = CorlaPrng(stateInput.seed, with_replacement = true, 1, mcontest.ballotCardCount)
            val mergedContest = stateInput.mergedContestMap[mcontest.contestName]!!
            println("ContestMergedInfo for ${mergedContest.contestName} strata = ${mergedContest.strata}")

            // val prns = prng.getRandomNumbers(0, mcontest.dbList.size-1)
            val prns = mutableListOf<Int>()
            repeat(mergedContest.strata!!.stateMvrs) { prns.add(prng.next()) }

            val mvrMapPrn = mcontest.mvrs.associateBy { it.prn }

            prns.forEachIndexed { idx, prn ->
                val mvr = mvrMapPrn[prn]
                if (mvr == null)
                    println("$idx: $prn -> missing ")
                else {
                    println("($idx):   $prn ${mvr.prn} -> $mvr")
                    assertEquals(prn, mvr.prn)
                }
            }
            println()
        }
    }

    @Test
    fun testGetStatewideMvrCount() {
        testGetStatewideMvrCount(Statewide2026General())
    }

    fun testGetStatewideMvrCount(stateInput: StatewideInput) {
        val infos = ContestMvrMergedInfos(stateInput, false, false)
        val counts = infos.getStatewideMvrCount()
        counts.forEach { println(it )}
        println("total = ${counts.values.sum()}")
    }

}