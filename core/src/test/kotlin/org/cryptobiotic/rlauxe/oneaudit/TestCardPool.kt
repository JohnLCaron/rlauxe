package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.util.tabulateCvrs
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.util.VotesAndUndervotes
import org.cryptobiotic.rlauxe.util.makeVunderCvrs
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCardPool {

    @Test
    fun testCardPoolFromCvrs() {
        val (contestOA, cardPools, cvrs) = makeOneContestUA(
            margin = .02,
            Nc = 50000,
            cvrFraction = .80,
            undervoteFraction = 0.10,
            phantomFraction = 0.005
        )

        val info = contestOA.contest.info()
        val infos = mapOf(info.id to info)
        val cvrTabs = tabulateCvrs(cvrs.iterator(), infos)
        val cvrTab = cvrTabs[info.id]!!

        assertEquals(cvrTab.votes, contestOA.contest.votes())
        assertEquals(cvrTab.ncards, contestOA.contest.Nc())

        // only one pool, only one contest
        val cardPool = cardPools.first()
        assertTrue(cardPool.contains(info.id))
        assertFalse(cardPool.contains(42))
        assertEquals(1, cardPool.regVotes().size)
    }

    @Test
    fun testAddUndervotes() {
        val test = MultiContestTestData(20, 11, 20000, hasStyle=true)
        val contestsUAs: List<ContestUnderAudit> = test.contests.map {
            ContestUnderAudit(it, isClca = true).addStandardAssertions()
        }

        val contestVotes = mutableMapOf<Int, VotesAndUndervotes>() // contestId -> VotesAndUndervotes
        contestsUAs.forEach { contestUA ->
            val candVotes = contestUA.contest.votes()!!
            val voteForN = contestUA.contest.info().voteForN
            val sumVotes = candVotes.map { it.value }.sum()
            val underVotes = contestUA.contest.Ncast() * voteForN - sumVotes
            contestVotes[contestUA.id] = VotesAndUndervotes(candVotes, underVotes, voteForN)
        }

        val cvrs = makeVunderCvrs(contestVotes, "poolName", poolId = 42)
        val infos = contestsUAs.associate { Pair(it.id, it.contest.info()) }

        val cardPools = CardPoolFromCvrs.makeCardPools(cvrs.iterator(), infos)
        val cardPool = cardPools.values.first()

        // TODO we need multi contest cvrs
        var changed = 0
        cvrs.forEach { cvr ->
            val cvru = cardPool.addUndervotes(cvr)
            if (cvr != cvru) changed++
        }
        println("changed = $changed out of ${cvrs.size}")
    }

    @Test
    fun testCardPool() {
        val test = MultiContestTestData(20, 11, 20000, hasStyle=true)
        val contestsUAs: List<ContestUnderAudit> = test.contests.map {
            ContestUnderAudit(it, isClca = true).addStandardAssertions()
        }

        val contestVotes = mutableMapOf<Int, VotesAndUndervotes>() // contestId -> VotesAndUndervotes
        contestsUAs.forEach { contestUA ->
            val candVotes = contestUA.contest.votes()!!
            val voteForN = contestUA.contest.info().voteForN
            val sumVotes = candVotes.map { it.value }.sum()
            val underVotes = contestUA.contest.Ncast() * voteForN - sumVotes
            contestVotes[contestUA.id] = VotesAndUndervotes(candVotes, underVotes, voteForN)
        }

        val cvrs = makeVunderCvrs(contestVotes, "poolName", poolId = 42)
        val infos = contestsUAs.associate { Pair(it.id, it.contest.info()) }
        val cvrTabs = tabulateCvrs(cvrs.iterator(), infos)

        val cardPool = CardPoolWithBallotStyle("pool42", 42, cvrTabs, infos)
        CardPoolWithBallotStyle.showVotes(contestVotes.keys.toList(), listOf(cardPool), width=6)
    }
}