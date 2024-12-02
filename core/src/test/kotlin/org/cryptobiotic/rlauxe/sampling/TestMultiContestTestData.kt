package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.util.df
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestMultiContestTestData {
    @Test
    fun testMakeSampleData() {
        val test = MultiContestTestData(20, 11, 20000)
        println("countBallots = ${test.countBallots}")
        println("partition = ${test.partition} total = ${test.partition.map{ it.value}.sum()} ")
        print(test)
        println()

        println("make contests")
        val contests = test.makeContests()
        contests.forEach {
            it.winners.forEach { winner ->
                it.losers.forEach { loser ->
                    val margin = (it.votes[winner]!! - it.votes[loser]!!) / it.Nc.toDouble()
                    println("  ${it.name}: votes=${it.votes} winner=$winner loser=$loser margin = ${df(margin)}")
                    assertTrue(margin > .01)
                }
            }
        }
        println()

        println("make cvrs, tabulate votes")
        val cvrs = test.makeCvrsFromContests()
        val votes: Map<Int, Map<Int, Int>> = org.cryptobiotic.rlauxe.util.tabulateVotes(cvrs).toSortedMap()
        votes.forEach { vcontest ->
            println("  tabulate contest $vcontest")
            votes.forEach { vcontest ->
                val contest = contests.find { it.id == vcontest.key }!!
                assertEquals(vcontest.value, contest.votes)
            }
        }
    }

}