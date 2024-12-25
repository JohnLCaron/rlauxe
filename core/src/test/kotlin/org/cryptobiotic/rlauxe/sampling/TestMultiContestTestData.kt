package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.workflow.checkEquivilentVotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestMultiContestTestData {

    // @Test
    fun testMakeSampleDataRepeat() {
        repeat(100) { testMakeSampleData() }
    }

    @Test
    fun testMakeSampleData() {
        val N = 50000
        val ncontests = 40
        val nbs = 11
        val marginRange= 0.01 ..< 0.04
        val underVotePct= 0.234 ..< 0.345
        val phantomRange= 0.001 ..< 0.01
        val test = MultiContestTestData(ncontests, nbs, N, marginRange, underVotePct, phantomRange)
        val calcN = test.ballotStylePartition.map { it.value }.sum()
        assertEquals(N, calcN)
        println(test)

        println("test makeContests")
        assertEquals(ncontests, test.contests.size)

        test.contests.forEachIndexed { idx, contest ->
            val fcontest = test.fcontests[idx]
            contest.winners.forEach { winner ->
                contest.losers.forEach { loser ->
                    assertTrue(marginRange.contains(fcontest.margin))
                    assertTrue(underVotePct.contains(fcontest.undervotePct))
                    assertTrue(phantomRange.contains(fcontest.phantomPct))

                    val calcMargin = contest.calcMargin(winner, loser)
                    val margin = (contest.votes[winner]!! - contest.votes[loser]!!) / contest.Nc.toDouble()
                    val calcReportedMargin = contest.calcMargin(winner, loser)
                    assertEquals(margin, calcReportedMargin, doublePrecision)
                    assertEquals(margin, calcMargin, doublePrecision)
                    println(" ${contest.id} fcontest= ${fcontest.margin} contest=$margin")
                }
            }
        }
        println()

        println("test makeCvrsFromContests")
        val cvrs = test.makeCvrsFromContests()
        val votes: Map<Int, Map<Int, Int>> = org.cryptobiotic.rlauxe.util.tabulateVotes(cvrs).toSortedMap()
        votes.forEach { vcontest ->
            println("  tabulate contest $vcontest")
            votes.forEach { vcontest ->
                val contest = test.contests.find { it.id == vcontest.key }!!
                assertTrue(checkEquivilentVotes(vcontest.value, contest.votes))
            }
        }

        println("test makeBallotsForPolling")
        val ballots = test.makeBallotsForPolling()
        println(" nballots= ${ballots.size}")
        assertEquals(N, ballots.size)

        val bs = mutableMapOf<Int, Int>()
        ballots.forEach { ballot ->
            bs.merge(ballot.ballotStyleId!!, 1) { a, b -> a + b }
        }
        println(" ballotStyles= ${bs}")

        val ballotStyles = test.ballotStylePartition.toMap()
        assertEquals(nbs, ballotStyles.size)

        assertEquals(ballotStyles, bs)
    }

}