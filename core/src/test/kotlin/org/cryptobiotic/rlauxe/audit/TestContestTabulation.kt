package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.makeContestsWithUndervotesAndPhantoms
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TestContestTabulation {

    @Test
    fun testContestTabulation() {
        val candVotes = mutableListOf<Map<Int, Int>>()
        candVotes.add(mapOf(0 to 200, 1 to 123, 2 to 17))
        candVotes.add(mapOf(0 to 71, 1 to 123, 2 to 0, 3 to 77, 4 to 99))
        candVotes.add(mapOf(0 to 102, 1 to 111))

        val undervotes = listOf(15, 123, 3)
        val phantoms = listOf(2, 7, 0)
        val voteForNs = listOf(1, 2, 1)
        val (contests, cvrs) = makeContestsWithUndervotesAndPhantoms(candVotes,
            undervotes=undervotes, phantoms=phantoms, voteForNs = voteForNs)

        val contestMap = contests.associate { Pair(it.id, it) }
        val infos = contests.associate { Pair(it.id, it.info) }
        val contestTabs: Map<Int, ContestTabulation> = tabulateCvrs(cvrs.iterator(), infos)

        assertEquals(3, contestTabs.size)
        contestTabs.forEach { (id, tab) ->
            println("contestTab=${tab}")
            val contest = contestMap[id]!!
            println("contest=${contest}")


            assertEquals(voteForNs[id], tab.voteForN)
            assertEquals(contest.Nc, tab.ncards)
            assertEquals(contest.undervotes + contest.Np() * contest.info.voteForN, tab.undervotes)
            assertTrue(checkEquivilentVotes(candVotes[id], tab.votes))
            println()
        }

        val cvrsTrunc = cvrs.subList(0, 100)
        val tabTrunc: Map<Int, ContestTabulation> = tabulateCvrs(cvrsTrunc.iterator(), infos)
        assertNotEquals(tabTrunc, contestTabs)
        assertNotEquals(tabTrunc.hashCode(), contestTabs.hashCode())
    }

}