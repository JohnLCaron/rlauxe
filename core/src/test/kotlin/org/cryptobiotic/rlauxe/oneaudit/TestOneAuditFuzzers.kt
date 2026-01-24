package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.Population
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.util.tabulateCards
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TestOneAuditFuzzers {

    @Test
    fun testOneAuditVunderFuzzer() {
        val fuzzPct = 0.001
        val extraPct = 0.01
        val Nc = 10000

        val (contestOA, mvrs, cards, pools) =
            makeOneAuditTest(
                margin = .01,
                Nc = Nc,
                cvrFraction = .95,
                undervoteFraction = 0.01,
                phantomFraction = 0.005,
                extraInPool= (extraPct * Nc).toInt(), // this creates a second contest with only undervotes
            )

        val info = contestOA.contest.info()
        val infos = mapOf(info.id to info, 2 to ContestInfo(2))
        val mvrTabs = tabulateCvrs(mvrs.iterator(), infos)
        val mvrTab = mvrTabs[info.id]!!
        println("mvrTab = $mvrTab")

        assertEquals(mvrTab.votes, contestOA.contest.votes())
        assertEquals(mvrTab.ncards, contestOA.contest.Nc())

        assertEquals(1, pools.size)
        val cardPool = pools.first()
        println(cardPool.show())
        assertTrue(cardPool.hasContest(info.id))
        assertFalse(cardPool.hasContest(42))
        assertEquals(2, cardPool.contests().size) // when extraPct > 0

        val countPoolCards = cards.count { it.poolId == cardPool.poolId }
        assertEquals(countPoolCards, cardPool.ncards())

        val vunderFuzz = OneAuditVunderFuzzer(pools, infos, fuzzPct, cards)
        val oaFuzzedPairs: List<Pair<AuditableCard, AuditableCard>> = vunderFuzz.mvrCvrPairs
        assertEquals(cards.size, oaFuzzedPairs.size)

        val countPoolCards2 = oaFuzzedPairs.count { it.second.poolId == cardPool.poolId }
        assertEquals(countPoolCards, countPoolCards2)

        val fuzzedMvrs = oaFuzzedPairs.map { it.first }
        val fuzzedMvrTab = tabulateCards(fuzzedMvrs.iterator(), infos)
        println("fuzzedMvrTab= ${fuzzedMvrTab[info.id]}")

        val countPoolCards3 = fuzzedMvrs.count { it.poolId == cardPool.poolId }
        assertEquals(countPoolCards, countPoolCards3)

        val fuzzedPool = calcOneAuditPoolsFromMvrs(
            infos,
            populations = listOf(Population("fuzzedPool", 42, intArrayOf(1, 2), false)),
            fuzzedMvrs.map { it.cvr() },
        )
        println("fuzzedPool= ${fuzzedPool.first().show()}")
        println()

        /* cvrs arent exact ??
        val poolMvrs: Map<Int, List<Cvr>> = pools.associate { it.poolId to it.simulateMvrsForPool() }
        poolMvrs.forEach { println("pool ${it.key} has ncards= ${cardPool.ncards()} and ${it.value.size} simulatedMvrsForPool") }

        // makeOneAuditTestP adds separate cards for "extra"; simulateMvrsForPool combines them.
        // so we dont have enough simulatedMvrsForPoolfor the fuzzing

        // what about a real contest? simulateMvrsForPool has to match whatever ncards has.

        val oaFuzzer = OneAuditPairFuzzer( poolMvrs, infos, fuzzPct)

        val oaFuzzedPairs: List<Pair<CardIF, AuditableCard>> = oaFuzzer.makeFromCards(cards)
        assertEquals(cards.size, oaFuzzedPairs.size) */
    }
}
