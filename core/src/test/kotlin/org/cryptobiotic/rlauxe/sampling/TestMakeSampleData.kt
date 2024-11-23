package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.util.df
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMakeSampleData {
    @Test
    fun testMakeSampleData() {
        val test = MultiContestTestData(20, 11, 20000)
        println(test)
        println("countBallots = ${test.countBallots}")
        val contests = test.makeContests()
        contests.forEach {
            it.winners.forEach { winner ->
                it.losers.forEach { loser ->
                    val margin = (it.votes[winner]!! - it.votes[loser]!!) / it.Nc.toDouble()
                    println("  winner=$winner loser = $loser margin = ${df(margin)}")
                    // assertTrue(abs(it.margin - margin) > .001)
                }
            }
        }

        val cvrs = test.makeCvrsFromContests()
        val votes: Map<Int, Map<Int, Int>> = org.cryptobiotic.rlauxe.util.tabulateVotes(cvrs).toSortedMap()
        votes.forEach { c ->
            println(" contest $c")
            val contest = contests.find{ it.id == c.key }!!
            assertEquals(c.value, contest.votes)
        }
    }

}