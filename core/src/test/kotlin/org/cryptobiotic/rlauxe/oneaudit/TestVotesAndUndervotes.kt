package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.ContestTabulation
import org.cryptobiotic.rlauxe.audit.checkEquivilentVotes
import org.cryptobiotic.rlauxe.audit.tabulateCvrs
import org.cryptobiotic.rlauxe.audit.tabulateVotesFromCvrs
import org.cryptobiotic.rlauxe.util.VotesAndUndervotes
import org.cryptobiotic.rlauxe.util.makeContestsWithUndervotesAndPhantoms
import org.cryptobiotic.rlauxe.util.makeVunderCvrs
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals


class TestVotesAndUndervotes {
/*
    @Test
    fun testBallotPool() {
        repeat (6) { it ->
            println("=================================")
            testBallotPool( it + 1)
        }
    }

    fun testBallotPool(voteForN: Int) {
        // TODO not great coverage; do more tests with random contest values
        val contestOA = (voteForN)
        // println("$contestOA nwinners=${contestOA.info.nwinners}")

        val ballotPool = contestOA.pools[1]!!
        println("ballotPool=$ballotPool nvotes = ${ballotPool.votes.values.sum()}")

        val vunders = ballotPool.votesAndUndervotes(voteForN)
        println(vunders)

        val contestVotes = mapOf(contestOA.id to vunders)

        val cvrs = makeVunderCvrs(contestVotes, poolId = 22)

        println("ncards=${ballotPool.ncards}, cvrs=${cvrs.size}")
        assertEquals(ballotPool.ncards, cvrs.size)

        val rcvrVotes: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(cvrs.iterator())
        assertEquals(ballotPool.votes.toSortedMap(), rcvrVotes[0]!!.toSortedMap())

        val tabsWith = tabulateVotesWithUndervotes(cvrs.iterator(), contestOA.id, vunders.votes.size, vunders.voteForN).toSortedMap()
        val vunder = ballotPool.votesAndUndervotes(voteForN)
        println("ncvrs= ${cvrs.size} tabsWith = ${tabsWith}")
        println("votesAndUndervotes()= ${vunder.votesAndUndervotes()}")
        assertEquals(vunder.votesAndUndervotes(), tabsWith)
    } */

    @Test
    fun testMakeVunderCvrs() {
        val contestVotes = mutableMapOf<Int, VotesAndUndervotes>() // contestId -> VotesAndUndervotes
        val candVotes0 = mapOf(0 to 200, 1 to 123, 2 to 17)
        contestVotes[0] = VotesAndUndervotes(candVotes0, 51, 1)

        val candVotes1 = mapOf(0 to 71, 1 to 123, 2 to 3)
        contestVotes[1] = VotesAndUndervotes(candVotes1, 51, 1)

        val cvrs = makeVunderCvrs(contestVotes, null)

        // check
        val tabVotes: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(cvrs.iterator())
        contestVotes.forEach { (contestId, vunders) ->
            val tv = tabVotes[contestId] ?: emptyMap()
                println("contestId=${contestId}")
                println("  tabVotes=${tv}")
                println("  vunders= ${vunders.candVotesSorted}")
                require(checkEquivilentVotes(vunders.candVotesSorted, tv))
        }

    }

    @Test
    fun testMakeContestsWithUndervotes() {
        val candVotes = mutableListOf<Map<Int, Int>>()
        candVotes.add(mapOf(0 to 200, 1 to 123, 2 to 17))
        candVotes.add(mapOf(0 to 71, 1 to 123, 2 to 0, 3 to 77, 4 to 99))
        candVotes.add(mapOf(0 to 102, 1 to 111))

        val (contests, cvrs) = makeContestsWithUndervotesAndPhantoms(candVotes,
            listOf(15, 123, 3), listOf(2, 7, 0))

        val contestMap = contests.associate { Pair(it.id, it) }
        val infos = contests.associate { Pair(it.id, it.info) }
        val contestTabs: Map<Int, ContestTabulation> = tabulateCvrs(cvrs.iterator(), infos)

        // check
        contestTabs.forEach { (id, tab) ->
            println("contestId=${id}")
            println("  tabVotes=${tab}")
            val contest = contestMap[id]!!
            assertEquals(contest.Nc, tab.ncards)
            assertEquals(contest.undervotes, tab.undervotes)
            assertTrue(checkEquivilentVotes(contest.votes, tab.votes))
        }
    }

}

