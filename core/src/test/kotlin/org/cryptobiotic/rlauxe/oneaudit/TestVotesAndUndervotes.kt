package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.tabulateVotesFromCvrs
import org.cryptobiotic.rlauxe.audit.tabulateVotesWithUndervotes
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.estimate.ContestSimulation
import org.cryptobiotic.rlauxe.util.VotesAndUndervotes
import org.cryptobiotic.rlauxe.util.makeVunderCvrs
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
    }
*/
}

