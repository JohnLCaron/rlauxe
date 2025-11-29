package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.pfn
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestMultiContestPoolData {
    val N = 50000
    val ncontests = 40
    val nbs = 11
    val marginRange = 0.01..0.04
    val underVotePct = 0.234..0.345
    val phantomRange = 0.001..0.01
    val poolPct = .11
    val test: MultiContestTestData
    val infos: Map<Int, ContestInfo>

    init {
        test = MultiContestTestData(ncontests, nbs, N, marginRange, underVotePct,
            phantomRange, poolPct=poolPct)
        infos = test.contests.associate { it.id to it.info }
    }

    @Test
    fun testBallotPartitions() {
        val calcN = test.ballotStylePartition.map { it.value }.sum()
        assertEquals(N, calcN)

        val poolSize = test.ballotStylePartition.filter{it.key < 2}.map { it.value }.sum()
        assertEquals(roundToClosest(N*poolPct), poolSize)
    }

    @Test
    fun testBallotStyles() {
        val calcN = test.cardStyles.sumOf { it.ncards }
        assertEquals(N, calcN)

        test.cardStyles.forEachIndexed { idx, it ->
            assertEquals(it.poolId, if (idx < 2) 1 else null)
        }

        val poolSize = test.cardStyles.filter { it.poolId != null }.sumOf { it.ncards }
        assertEquals(roundToClosest(N*poolPct), poolSize)
    }

    @Test
    fun testMakeCardPoolManifest() {
        val (_, cards, pools) = test.makeCardPoolManifest()
        println(pools)
        println("poolPct = ${pfn(poolPct)}")
        println()

        val manifestTabs = tabulateAuditableCards(Closer(cards.iterator()), infos).toSortedMap()
        manifestTabs.forEach { (contestId, tab) ->
            val contest = test.contests.find { it.id == contestId }!!
            println("contest $contest")
            println("tab $tab")
            println("extra cards= ${tab.ncards - contest.Ncast} is ${pfn((tab.ncards - contest.Ncast)/contest.Ncast.toDouble())}")
            assertTrue (tab.ncards >= contest.Ncast)
            println()
        }
    }
}