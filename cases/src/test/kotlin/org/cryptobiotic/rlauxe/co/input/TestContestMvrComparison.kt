package org.cryptobiotic.rlauxe.co.input

import org.cryptobiotic.rlauxe.betting.ClcaErrorRates
import org.cryptobiotic.rlauxe.co.auditcenter.ContestRound
import org.cryptobiotic.rlauxe.co.auditcenter.ContestSelection
import org.cryptobiotic.rlauxe.co.auditcenter.MvrComparison
import org.cryptobiotic.rlauxe.co.auditcenter.ResultsReport
import org.cryptobiotic.rlauxe.co.corla.CorlaPrng
import org.cryptobiotic.rlauxe.core.Assertion
import org.cryptobiotic.rlauxe.core.ClcaAssorter
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.PluralityAssorter
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.corlaInput.Statewide2026General
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.corlaInput.statewideInputs
import org.cryptobiotic.rlauxe.estimate.calcAssorterMargin
import org.cryptobiotic.rlauxe.estimate.makeCvr
import org.cryptobiotic.rlauxe.util.CvrBuilders
import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.util.listToMap
import org.cryptobiotic.rlauxe.util.makeContestFromCvrs
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.mean2margin
import kotlin.collections.forEachIndexed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class TestContestMvrComparison {

    @Test
    fun testOneNames() {
        testContestNames(Statewide2026General())
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
    fun testContestMergedComparison() {
        testContestMvrComparisons(Statewide2026General())
    }

    @Test
    fun testAllContestMergedInfos() {
        statewideInputs.forEach {
            testContestMvrComparisons(it)
        }
    }

    fun testContestMvrComparisons(stateInput: StatewideInput) {

        val contestMvrComparisons = ContestMvrComparisons(stateInput, stateInput.mergedContestMap.values.toList(), false)

        // test sequences for all targeted ballots
        contestMvrComparisons.mcontests.values.filter { it.minfo.contestName.startsWith("Governor") }.forEach { mcontest ->
            println("ContestMvrMergedInfo for ${mcontest.minfo.contestName} maximumIndex=${mcontest.minfo.corlaPop} ")
            val prng = CorlaPrng(stateInput.seed, with_replacement = true, 1, mcontest.minfo.corlaPop)
            val mergedContest = stateInput.mergedContestMap[mcontest.minfo.contestName]!!
            println("ContestMergedInfo for ${mergedContest.contestName} strata = ${mergedContest.strata}")

            // val prns = prng.getRandomNumbers(0, mcontest.dbList.size-1)
            val prns = mutableListOf<Int>()
            repeat(mergedContest.strata!!.stateMvrs) { prns.add(prng.next()) }

            /*
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
            println() */
        }
    }

    @Test
    fun testGetStatewideMvrCounty() {
        testGetStatewideMvrCounty(Statewide2026General(), "Boulder")
    }

    fun testGetStatewideMvrCounty(stateInput: StatewideInput, county: String) {
        val mvrs = ContestMvrComparisons(stateInput, stateInput.mergedContestMap.values.toList(), true).getMvrsForCounty(county)
        mvrs.forEach {
            println("  $it")
            it.lines.forEach {line -> println("    $line") }
        }
    }

    @Test
    fun testErrorCounts() {
        testErrorCounts(Statewide2026General())
    }

    fun testErrorCounts(input: StatewideInput) {
        val wtf = ContestMvrComparisons(input, minfos = input.mergedContestMap.values.toList(), false)
        var count = 0
        wtf.mcontests.values.forEach {
            if (it.hasErrors())
                println("$count: ${it.p2o} ${it.p1o} ${it.p1u} ${it.p2u} ")
            count++
        }
    }

    //     // TODO test assignments
    //    fun classifyDiscrepency(cvrChoice: String, mvrChoice: String): ErrorType2? {
    //        if (cvrChoice == this.winner) {
    //            if (mvrChoice == this.loser) return ErrorType2.p2o
    //            else return ErrorType2.p1o
    //        } else if (cvrChoice == this.loser) {
    //            if (mvrChoice == this.winner) return ErrorType2.p2u
    //            else return ErrorType2.p1u
    //        } else {
    //            if (mvrChoice == this.loser) return ErrorType2.p1o
    //            if (mvrChoice == this.winner) return ErrorType2.p1u
    //            return null
    //        }
    //    }

    @Test
    fun testClassifyDiscrepency() {
        val Npop = 1_000_000
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap("A", "B", "C"),
        )
        val winnerCvr = makeCvr(0)
        val loserCvr = makeCvr(1)
        val otherCvr = makeCvr(2)
        val votes = mapOf(0 to 1010, 1 to 990) // Map<Int, Int>
        val contest =  Contest(info, votes, 2000, Ncast=2000)

        val assorter = PluralityAssorter.makeWithVotes(contest, winner = 0, loser = 1)
        assertEquals(20/2000.toDouble(), assorter.dilutedMargin(), doublePrecision)

        val awinnerAvg = .505
        val margin = 2.0 * awinnerAvg - 1.0 // reported assorter margin
        assertEquals(.01, assorter.dilutedMargin(), doublePrecision)

        assertEquals(1.0, assorter.assort(winnerCvr)) // voted for the winner
        assertEquals(0.0, assorter.assort(loserCvr))  // voted for the loser
        assertEquals(0.5, assorter.assort(otherCvr))  // voted for someone else
        // so assort in {0, .5, 1}

        ////////////////////////////////////////////////////////////////////////////////////////////////

        val cassorter = ClcaAssorter(info, assorter)
        assertEquals(0.0, cassorter.overstatementError(winnerCvr, winnerCvr))
        assertEquals(-1.0, cassorter.overstatementError(winnerCvr, loserCvr)) // p2u
        assertEquals(-0.5, cassorter.overstatementError(winnerCvr, otherCvr)) // p1u

        assertEquals(1.0, cassorter.overstatementError(loserCvr, winnerCvr)) // p2o
        assertEquals(0.0, cassorter.overstatementError(loserCvr, loserCvr))
        assertEquals(0.5, cassorter.overstatementError(loserCvr, otherCvr)) // p1o

        assertEquals(0.5, cassorter.overstatementError(otherCvr, winnerCvr)) // p1o
        assertEquals(-0.5, cassorter.overstatementError(otherCvr, loserCvr)) // p1u
        assertEquals(0.0, cassorter.overstatementError(otherCvr, otherCvr))


        // overstatementError in [-1, -.5, 0, .5, 1]

        val noerror = 1.0 / (2.0 - margin)
        assertEquals(.5025125628140703, noerror, doublePrecision)
        assertEquals(1.0 / (3 - 2 * awinnerAvg), noerror, doublePrecision)

        // bassort(mvr: Cvr, cvr:Cvr)
        // (1 − ωi /upper) / (2 − margin/upper), upper == assorter.upper
        assertEquals(noerror, cassorter.bassort(winnerCvr, winnerCvr))         // no error
        assertEquals(null, classifyDiscrepency("A","A", "A", "B"))

                                                                                // mvr, cvr
        assertEquals(2 * noerror, cassorter.bassort(winnerCvr, loserCvr))      // cvr flipped vote from winner to loser: p2u
        assertEquals(ErrorType2.p2u, classifyDiscrepency("A","B", "A", "B"))

        assertEquals(1.5 * noerror, cassorter.bassort(winnerCvr, otherCvr))    // flipped vote from winner to other: p1u
        assertEquals(ErrorType2.p1u, classifyDiscrepency("A","C", "A", "B"))

        assertEquals(0.0, cassorter.bassort(loserCvr, winnerCvr))              // flipped vote from loser to winner: p2o
        assertEquals(ErrorType2.p2o, classifyDiscrepency("B", "A", "A", "B"))

        assertEquals(noerror, cassorter.bassort(loserCvr, loserCvr))           // no error
        assertEquals(null, classifyDiscrepency("B", "B", "A", "B"))

        assertEquals(0.5 * noerror, cassorter.bassort(loserCvr, otherCvr))       // flipped vote from loser to other: p1o
        assertEquals(ErrorType2.p1o, classifyDiscrepency("B", "C", "A", "B"))

        assertEquals(0.5 * noerror, cassorter.bassort(otherCvr, winnerCvr))      // flipped vote from other to winner: p1o
        assertEquals(ErrorType2.p1o, classifyDiscrepency("C", "A", "A", "B"))

        assertEquals(1.5 * noerror, cassorter.bassort(otherCvr, loserCvr))       // flipped vote from other to loser: p1u
        assertEquals(ErrorType2.p1u, classifyDiscrepency("C", "B", "A", "B"))

        assertEquals(noerror, cassorter.bassort(otherCvr, otherCvr))           // no error
        assertEquals(null, classifyDiscrepency("C", "C", "A", "B"))

        // so bassort in [0, 2 / (2 - margin)] = [0, 2 / (3 - 2 * Aavg)] in {0, .5, 1, 1.5, 2} * noerror
        // so bassort in [0, 2*noerror], where noerror > .5. since margin > 0, since awinnerAvg > .5.
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


}