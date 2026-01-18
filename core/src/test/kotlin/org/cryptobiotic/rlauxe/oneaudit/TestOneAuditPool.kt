package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.util.tabulateCvrs
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.Vunder
import org.cryptobiotic.rlauxe.util.makeVunderCvrs
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TestOneAuditPool {

    @Test
    fun testCardPoolFromCvrs() {
        val (contestOA, mvrs, cards, cardPools) =
            makeOneAuditTest(
                margin = .02,
                Nc = 50000,
                cvrFraction = .80,
                undervoteFraction = 0.10,
                phantomFraction = 0.005
            )

        val info = contestOA.contest.info()
        val infos = mapOf(info.id to info)
        val cvrTabs = tabulateCvrs(mvrs.iterator(), infos)
        val cvrTab = cvrTabs[info.id]!!

        assertEquals(cvrTab.votes, contestOA.contest.votes())
        assertEquals(cvrTab.ncards, contestOA.contest.Nc())

        // only one pool, only one contest
        val cardPool = cardPools.first()
        assertTrue(cardPool.hasContest(info.id))
        assertFalse(cardPool.hasContest(42))
        assertEquals(1, cardPool.regVotes().size)

        assertTrue(cardPool is OneAuditPoolFromCvrs)
        val cardPoolCvrs = cardPool as OneAuditPoolFromCvrs
        val cardPoolCvrs2 = cardPoolCvrs.copy()
        assertEquals(cardPoolCvrs, cardPoolCvrs)
        assertEquals(cardPoolCvrs.hashCode(), cardPoolCvrs.hashCode())

        assertNotEquals(cardPoolCvrs2, cardPoolCvrs)
        assertNotEquals(cardPoolCvrs2.hashCode(), cardPoolCvrs.hashCode())

        val poolTabs = mutableMapOf<Int, ContestTabulation>()
        cardPool.addTo(poolTabs)
        assertEquals(cardPool.contestTabs, poolTabs)

        cardPool.addTo(poolTabs)

        assertEquals(1, poolTabs.size)
        val tab = poolTabs.values.first()
        assertEquals(1, tab.info.id)
        val poolVotes = cardPool.contestTabs[1]!!.votes
        poolVotes.forEach { (cand, vote) ->
            assertEquals(2 * vote, tab.votes[cand])
        }
    }

    @Test
    fun testAddUndervotes() {
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
            contestVotes[contestUA.id] = Vunder(candVotes, underVotes, voteForN)
        }

        val cvrs = makeVunderCvrs(contestVotes, "poolName", poolId = 42)
        val infos = contestsUAs.associate { Pair(it.id, it.contest.info()) }

        val cardPools = calcOneAuditPoolsFromMvrs(infos, test.populations, cvrs)
        // was val cardPools = CardPoolFromCvrs.makeCardPools(cvrs.iterator(), infos)
        val cardPool = cardPools.first()

        // TODO we need multi contest cvrs
        var changed = 0
        cvrs.forEach { cvr ->
            val cvru = cardPool.addUndervotes(cvr)
            if (cvr != cvru) changed++
        }
        println("changed = $changed out of ${cvrs.size}")
    }

    @Test
    fun testOneAuditPoolWithBallotStyle() {
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
            contestVotes[contestUA.id] = Vunder(candVotes, underVotes, voteForN)
        }

        val cvrs = makeVunderCvrs(contestVotes, "poolName", poolId = 42)
        val infos = contestsUAs.associate { Pair(it.id, it.contest.info()) }
        val cvrTabs = tabulateCvrs(cvrs.iterator(), infos)

        val cardPool = OneAuditPoolWithBallotStyle("pool42", 42, false, cvrTabs, infos)
        println(cardPool)

        assertEquals(cardPool, cardPool)
        assertEquals(cardPool.hashCode(), cardPool.hashCode())

        val cardPool2 = cardPool.copy(hasSingleCardStyle = true)
        assertNotEquals(cardPool2, cardPool)
        assertNotEquals(cardPool2.hashCode(), cardPool.hashCode())
    }
}