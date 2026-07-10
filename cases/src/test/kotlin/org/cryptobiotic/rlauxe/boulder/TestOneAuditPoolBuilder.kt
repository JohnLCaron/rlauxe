package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.Vunder
import org.cryptobiotic.rlauxe.estimate.makeCvrsForOnePool
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import kotlin.collections.forEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TestOneAuditPoolBuilder {

    @Test
    fun testOneAuditPoolBuilder() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUAs: List<ContestWithAssertions> = test.contests.map {
            ContestWithAssertions(it, isClca = true).addStandardAssertions()
        }

        val contestVotes = mutableMapOf<Int, Vunder>() // contestId -> VotesAndUndervotes
        contestsUAs.forEach { contestUA ->
            val candVotes = contestUA.contest.votes()!!
            val voteForN = contestUA.contest.info().voteForN
            val sumVotes = candVotes.map { it.value }.sum()
            val underVotes = contestUA.contest.Ncast() * voteForN - sumVotes
            contestVotes[contestUA.id] = Vunder.fromNpop(contestUA.id, underVotes, contestUA.contest.Ncast(), candVotes, voteForN)
        }

        // TODO val cvrs2 = makeCvrsFromPopulations(test.populations)
        val cvrs = makeCvrsForOnePool(contestVotes, "poolName", poolId = 42, hasExactContests = false)
        val infos = contestsUAs.associate { Pair(it.id, it.contest.info()) }
        val cvrTabs = tabulateCvrs(cvrs.iterator(), infos)

        val cardPool = OneAuditPoolBuilder("pool42", 42, false, cvrTabs, infos)
        println(cardPool)

        assertEquals(cardPool, cardPool)
        assertEquals(cardPool.hashCode(), cardPool.hashCode())

        val cardPool2 = cardPool.copy(hasExactContests = true)
        assertNotEquals(cardPool2, cardPool)
        assertNotEquals(cardPool2.hashCode(), cardPool.hashCode())
    }
}