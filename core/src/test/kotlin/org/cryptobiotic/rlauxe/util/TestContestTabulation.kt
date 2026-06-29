package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.verify.checkEquivilentVotes
import org.junit.jupiter.api.Assertions
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestContestTabulation {

    @Test
    fun testContestTabulationRepeat() {
        repeat(100) { testContestTabulation() }
        repeat(100) { testContestTabulationWithMissing() }
    }

    @Test
    fun testContestTabulation() {
        val candVotes = mutableListOf<Map<Int, Int>>()
        candVotes.add(mapOf(0 to 200, 1 to 123, 2 to 17))
        candVotes.add(mapOf(0 to 71, 1 to 123, 2 to 0, 3 to 77, 4 to 99))
        candVotes.add(mapOf(0 to 102, 1 to 111))

        val undervotes = listOf(15, 120, 3)
        val phantoms = listOf(2, 7, 0)
        val voteForNs = listOf(1, 2, 1)
        val (contests, cvrs) = makeContestsWithUndervotesAndPhantoms(candVotes,
            undervotes=undervotes, phantoms=phantoms, voteForNs = voteForNs)

        val contestMap = contests.associate { Pair(it.id, it) }
        val infos = contests.associate { Pair(it.id, it.info) }
        val contestTabs: Map<Int, ContestTabulation> = tabulateCvrs(cvrs.iterator(), infos)

        assertEquals(3, contestTabs.size)
        contestTabs.forEach { (id, tab) ->
            //println("contestTab=${tab}")
            val contest = contestMap[id]!!
            //println("contest=${contest}")

            assertEquals(voteForNs[id], tab.voteForN)
            assertEquals(contest.Nc, tab.ncardsTabulated)
            assertEquals(contest.undervotes, tab.undervotes)
            assertTrue(
                checkEquivilentVotes(candVotes[id], tab.votes),
                "${candVotes[id].toSortedMap()} != ${tab.votes.toSortedMap()}"
            )
            println()
        }

        val cvrsTrunc = cvrs.subList(0, 100)
        val tabTrunc: Map<Int, ContestTabulation> = tabulateCvrs(cvrsTrunc.iterator(), infos)
        assertNotEquals(tabTrunc, contestTabs)
        assertNotEquals(tabTrunc.hashCode(), contestTabs.hashCode())

        val contestTabs2: Map<Int, ContestTabulation> = tabulateCvrs(cvrs.iterator(), infos)
        assertEquals(contestTabs2, contestTabs)
        assertEquals(contestTabs2.hashCode(), contestTabs.hashCode())
    }

    @Test
    fun testContestTabulationWithMissing() {
        val candVotes = mutableListOf<Map<Int, Int>>()
        candVotes.add(mapOf(0 to 200, 1 to 123, 2 to 17))
        candVotes.add(mapOf(0 to 71, 1 to 123, 2 to 0, 3 to 77, 4 to 99))
        candVotes.add(mapOf(0 to 102, 1 to 111))

        val undervotes = listOf(15, 120, 3)
        val phantoms = listOf(2, 7, 0)
        val voteForNs = listOf(1, 2, 1)
        val missings = listOf(11, 211, 111)
        val (contests, cvrs) = makeContestsWithUndervotesAndPhantoms(
            candVotes,
            undervotes = undervotes, phantoms = phantoms, voteForNs = voteForNs, missings = missings
        )

        val contestMap = contests.associate { Pair(it.id, it) }
        val infos = contests.associate { Pair(it.id, it.info) }
        val contestTabs: Map<Int, ContestTabulation> = tabulateCvrs(cvrs.iterator(), infos)

        assertEquals(3, contestTabs.size)
        contestTabs.forEach { (id, tab) ->
            //println("contestTab=${tab}")
            val contest = contestMap[id]!!
            //println("contest=${contest}")

            assertEquals(voteForNs[id], tab.voteForN)
            assertEquals(contest.Nc, tab.ncardsTabulated)
            assertEquals(contest.undervotes, tab.undervotes)
            assertTrue(
                checkEquivilentVotes(candVotes[id], tab.votes),
                "${candVotes[id].toSortedMap()} != ${tab.votes.toSortedMap()}"
            )
            println()
        }

        val cvrsTrunc = cvrs.subList(0, 100)
        val tabTrunc: Map<Int, ContestTabulation> = tabulateCvrs(cvrsTrunc.iterator(), infos)
        assertNotEquals(tabTrunc, contestTabs)
        assertNotEquals(tabTrunc.hashCode(), contestTabs.hashCode())

        val contestTabs2: Map<Int, ContestTabulation> = tabulateCvrs(cvrs.iterator(), infos)
        assertEquals(contestTabs2, contestTabs)
        assertEquals(contestTabs2.hashCode(), contestTabs.hashCode())

    }

    @Test
    fun testTabulateCardsAndCount() {
        val test = MultiContestTestData(20, 11, 20000)
        val ( mvrs, cards, pools, styles) = test.makeMvrCardAndPops()
        val infos = test.contests.map { it.info }.associateBy { it.id }

        val cardTabulation = CardTabulation(Closer (cards.iterator() ), infos) { }
        val tabs = cardTabulation.tabs
        val count = cardTabulation.cvrCount
        Assertions.assertEquals(cards.size, count)

        val tab2 = tabulateAuditableCards(Closer(cards.iterator()), infos)
        tabs.forEach { println(it) }

        Assertions.assertEquals(tabs, tab2)
    }

    @Test
    fun checkTabulationCvrsAndCards() {
        val test = MultiContestTestData(20, 11, 20000)
        val infos = test.contests.associate { it.id to it.info }

        val ( mvrs, cards, pools, styles) = test.makeMvrCardAndPops()
        val cvrTabs =  tabulateCvrs(mvrs.iterator(), infos)

        val cardTabulation = CardTabulation( Closer(cards.iterator()), infos) { }
        val cardTabs = cardTabulation.tabs
        val count = cardTabulation.cvrCount

        assertEquals(cards.size, count)
        assertEquals(cvrTabs.size, cardTabs.size)
        cvrTabs.forEach { (id, cvrTab) ->
            val cardTab = cardTabs[id]
            assertEquals(cvrTab, cardTab)
        }
    }

}