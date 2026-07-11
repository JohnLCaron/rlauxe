package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.Vunder
import org.cryptobiotic.rlauxe.estimate.makeCvrsForOnePool
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import kotlin.collections.forEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TestAdjustableCardPool {

    @Test
    fun testCardPoolBuilder() {
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

        val cvrs = makeCvrsForOnePool(contestVotes, "poolName", poolId = 42, hasExactContests = false)
        val infos = contestsUAs.associate { Pair(it.id, it.contest.info()) }
        val cvrTabs = tabulateCvrs(cvrs.iterator(), infos)

        val cardPoolb = CardPoolBuilder("pool42", 42, false, infos, cvrTabs)
        println(cardPoolb)

        assertEquals(cardPoolb, cardPoolb)
        assertEquals(cardPoolb.hashCode(), cardPoolb.hashCode())
        val cardPool = cardPoolb.build()

        cardPoolb.setNcards(4234)
        val cardPool2 = cardPoolb.build()

        assertNotEquals(cardPool, cardPool2)
        assertNotEquals(cardPool.hashCode(), cardPool2.hashCode())
    }
}