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
    fun testCardPoolBuilders() {
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

        val cardPoolMinVotes = CardPoolBuilder.fromMinVotesNeeded("pool42", 42, false, infos, cvrTabs)
        println(cardPoolMinVotes)

        assertEquals(cardPoolMinVotes, cardPoolMinVotes)
        assertEquals(cardPoolMinVotes.hashCode(), cardPoolMinVotes.hashCode())

        val cardPoolMinCards = CardPoolBuilder.fromMinVotesNeeded("pool42", 42, false, infos, cvrTabs)
        println(cardPoolMinCards)

        val cardPool1 = cardPoolMinVotes.build()
        val cardPool2 = cardPoolMinCards.build()

        assertEquals(cardPool1, cardPool2)
        assertEquals(cardPool1.hashCode(), cardPool2.hashCode())

        cardPoolMinCards.setNcards(4234)
        val cardPool3 = cardPoolMinCards.build()

        assertNotEquals(cardPool1, cardPool3)
        assertNotEquals(cardPool1.hashCode(), cardPool3.hashCode())
    }
}