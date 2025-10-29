package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.verify.checkEquivilentVotes
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import org.cryptobiotic.rlauxe.util.tabulateVotesFromCvrs
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

        val cvrs = makeVunderCvrs(contestVotes, "poolName", null)

        // check
        val tabVotes: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(cvrs.iterator())
        contestVotes.forEach { (contestId, vunders) ->
            val tv = tabVotes[contestId] ?: emptyMap()
                println("contestId=${contestId}")
                println("  tabVotes=${tv}")
                println("  vunders= ${vunders.candVotesSorted}")
                require(checkEquivilentVotes(vunders.candVotesSorted, tv))
        }

        assertEquals("votes={0=200, 1=123, 2=17} undervotes=51, voteForN=1", contestVotes[0].toString())
        assertEquals(mapOf(0 to 200, 1 to 123, 2 to 17, 3 to 51), contestVotes[0]!!.votesAndUndervotes())

        assertEquals("votes={1=123, 0=71, 2=3} undervotes=51, voteForN=1", contestVotes[1].toString())
        assertEquals(mapOf(0 to 71, 1 to 123, 2 to 3, 3 to 51), contestVotes[1]!!.votesAndUndervotes())
    }

    @Test
    fun testMakeVunderCvrsN2() {
        val contestVotes = mutableMapOf<Int, VotesAndUndervotes>() // contestId -> VotesAndUndervotes
        val candVotes0 = mapOf(0 to 200, 1 to 123, 2 to 17)
        contestVotes[0] = VotesAndUndervotes(candVotes0, 51, 2)

        val candVotes1 = mapOf(0 to 71, 1 to 123, 2 to 3)
        contestVotes[1] = VotesAndUndervotes(candVotes1, 51, 1)

        val cvrs = makeVunderCvrs(contestVotes, "poolName", null)

        // check
        val tabVotes: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(cvrs.iterator())
        contestVotes.forEach { (contestId, vunders) ->
            val tv = tabVotes[contestId] ?: emptyMap()
            println("contestId=${contestId}")
            println("  tabVotes=${tv}")
            println("  vunders= ${vunders.candVotesSorted}")
            require(checkEquivilentVotes(vunders.candVotesSorted, tv))
        }

        assertEquals("votes={0=200, 1=123, 2=17} undervotes=51, voteForN=2", contestVotes[0].toString())
        assertEquals(mapOf(0 to 200, 1 to 123, 2 to 17, 3 to 51), contestVotes[0]!!.votesAndUndervotes())
    }

    @Test
    fun testMakeContestsWithUndervotesAndPhantoms() {
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
            println("contestTab=${tab}")
            val contest = contestMap[id]!!
            println("contest=${contest}")
            assertEquals(contest.Nc, tab.ncards)
            assertEquals(contest.undervotes + contest.Np(), tab.undervotes)
            assertTrue(checkEquivilentVotes(contest.votes, tab.votes))
        }
    }

    @Test
    fun testMakeContestsWithUndervotesAndPhantomsN2() {
        val candVotes = mutableListOf<Map<Int, Int>>()
        candVotes.add(mapOf(0 to 200, 1 to 123, 2 to 17))
        candVotes.add(mapOf(0 to 71, 1 to 123, 2 to 0, 3 to 77, 4 to 99))
        candVotes.add(mapOf(0 to 102, 1 to 111))

        val (contests, cvrs) = makeContestsWithUndervotesAndPhantoms(candVotes,
            listOf(15, 123, 3), listOf(2, 7, 0), voteForNs = listOf(1, 2, 1))

        val contestMap = contests.associate { Pair(it.id, it) }
        val infos = contests.associate { Pair(it.id, it.info) }
        val contestTabs: Map<Int, ContestTabulation> = tabulateCvrs(cvrs.iterator(), infos)

        // check
        contestTabs.forEach { (id, tab) ->
            println("contestTab=${tab}")
            val contest = contestMap[id]!!
            println("contest=${contest}")
            assertEquals(contest.Nc, tab.ncards)
            assertEquals(contest.undervotes + contest.Np() * contest.info.voteForN, tab.undervotes)
            assertTrue(checkEquivilentVotes(contest.votes, tab.votes))
            println()
        }
    }

}

