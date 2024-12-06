package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.workflow.checkEquivilentVotes
import kotlin.test.Test
import kotlin.test.assertTrue

class TestMultiContestTestData {

    // @Test
    fun testMakeSampleDataRepeat() {
        repeat(100) { testMakeSampleData() }
    }

    @Test
    fun testMakeSampleData() {
        val test = MultiContestTestData(20, 11, 20000, 0.011 .. 0.03)
        println("testMakeSampleData--------------------------------------------------------------")
        println("ballotStylePartition = ${test.ballotStylePartition} total = ${test.ballotStylePartition.map{ it.value}.sum()} ")
        print(test)
        println()

        println("test makeContests")
        val contests = test.makeContests()
        contests.forEachIndexed { idx, contest ->
            val fcontest = test.fcontests[idx]
            contest.winners.forEach { winner ->
                contest.losers.forEach { loser ->
                    val margin = (contest.votes[winner]!! - contest.votes[loser]!! + 2) / contest.Nc.toDouble()
                    println("  $contest margin = ${df(margin)} fmargin = ${df(fcontest.margin)}")
                    assertTrue(fcontest.margin >= .011)
                    assertTrue(margin >= fcontest.margin)
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
                val contest = contests.find { it.id == vcontest.key }!!
                checkEquivilentVotes(vcontest.value, contest.votes)
            }
        }
    }

}