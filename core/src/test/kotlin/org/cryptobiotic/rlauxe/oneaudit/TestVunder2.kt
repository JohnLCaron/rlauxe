package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.Population
import org.cryptobiotic.rlauxe.util.makePhantomCvrs
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.verify.checkEquivilentVotes
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import org.cryptobiotic.rlauxe.util.Vunder2
import org.cryptobiotic.rlauxe.util.makeVunderCvrs
import org.cryptobiotic.rlauxe.util.tabulateCards
import org.cryptobiotic.rlauxe.util.tabulateCvrsWithVoteForNs
import org.cryptobiotic.rlauxe.util.tabulateVotesFromCvrs
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.collections.List
import kotlin.test.Test
import kotlin.test.assertEquals

class TestVunder2 {

    @Test
    fun testMakeVunderCvrs() {
        val contestVotes = mutableMapOf<Int, Vunder2>() // contestId -> VotesAndUndervotes
        val candVotes0 = mapOf(0 to 200, 1 to 123, 2 to 17)
        val undervotes = 51
        contestVotes[0] = Vunder2.fromNpop(0,  undervotes, candVotes0.values.sum() + undervotes, candVotes0, 1)

        val candVotes1 = mapOf(0 to 71, 1 to 123, 2 to 3)
        contestVotes[1] = Vunder2.fromNpop(1, undervotes, candVotes1.values.sum() + undervotes, candVotes1, 1)

        val cvrs = makeVunderCvrs(contestVotes, "poolName", null)

        // check
        val tabVotes: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(cvrs.iterator())
        contestVotes.forEach { (contestId, vunders) ->
            val tv = tabVotes[contestId] ?: emptyMap()
            println("contestId=${contestId}")
            println("  tabVotes=${tv}")
            println("  vunders= ${vunders.cands()}")
        }

        assertEquals("id=0, voteForN=1, votes={0=200, 1=123, 2=17}, nvotes=340 ncards=391, undervotes=51, missing=0", contestVotes[0].toString())
        assertTrue(checkEquivilentVotes(mapOf(0 to 200, 1 to 123, 2 to 17), contestVotes[0]!!.cands()))

        assertEquals("id=1, voteForN=1, votes={0=71, 1=123, 2=3}, nvotes=197 ncards=248, undervotes=51, missing=0", contestVotes[1].toString())
        assertTrue(checkEquivilentVotes(mapOf(0 to 71, 1 to 123, 2 to 3), contestVotes[1]!!.cands()))
    }

    @Test
    fun testMakeVunderCvrsVotesFor2() {
        val vunders = mutableMapOf<Int, Vunder2>() // contestId -> VotesAndUndervotes
        val candVotes0 = mapOf(0 to 200, 1 to 123, 2 to 17)
        val undervotes = 51
        vunders[0] = Vunder2.fromNpop(0,  undervotes, candVotes0.values.sum() + undervotes, candVotes0,2)

        val candVotes1 = mapOf(0 to 71, 1 to 123, 2 to 3)
        vunders[1] = Vunder2.fromNpop(1, undervotes, candVotes1.values.sum() + undervotes, candVotes1,1)

        val cvrs = makeVunderCvrs(vunders, "poolName", null)

        // check
        val voteForNs = vunders.mapValues { it.value.voteForN }
        val tabVotes = tabulateCvrsWithVoteForNs(cvrs.iterator(), voteForNs)
        vunders.forEach { (contestId, vunder) ->
            val tv = tabVotes[contestId]!!
            println("contestId=${contestId}")
            println("  tabVotes=${tv}")
            println("  vunder= ${vunder.cands()}")
        }

        assertTrue(checkEquivilentVotes(mapOf(0 to 200, 1 to 123, 2 to 17), vunders[0]!!.cands()))
        assertEquals("id=0, voteForN=2, votes={0=200, 1=123, 2=17}, nvotes=340 ncards=391, undervotes=51, missing=196", vunders[0]!!.toString())
    }

    // checkEquivilentVotes(vunder.candVotes, contestTab.votes)

    @Test
    fun testMakeContestsWithVunder2() {
        val candVotes = mutableListOf<Map<Int, Int>>()
        candVotes.add(mapOf(0 to 200, 1 to 123, 2 to 17))
        candVotes.add(mapOf(0 to 71, 1 to 123, 2 to 0, 3 to 77, 4 to 99))
        candVotes.add(mapOf(0 to 102, 1 to 111))

        val (contests, cvrs) = makeContestsWithVunder2(candVotes, listOf(15, 123, 3), listOf(2, 7, 0))

        val contestMap = contests.associate { Pair(it.id, it) }
        val infos = contests.associate { Pair(it.id, it.info) }
        val contestTabs: Map<Int, ContestTabulation> = tabulateCvrs(cvrs.iterator(), infos)

        // check
        contestTabs.forEach { (id, tab) ->
            println("contestTab=${tab}")
            val contest = contestMap[id]!!
            println("contest=${contest}")
            assertEquals(contest.Nc, tab.ncards)
            assertEquals(contest.undervotes + contest.Nphantoms(), tab.undervotes)
            assertTrue(checkEquivilentVotes(contest.votes, tab.votes))
        }
    }

    @Test
    fun testMakeContestsWithVunderN2() {
        val candVotes = mutableListOf<Map<Int, Int>>()
        candVotes.add(mapOf(0 to 200, 1 to 123, 2 to 17))
        candVotes.add(mapOf(0 to 71, 1 to 123, 2 to 0, 3 to 77, 4 to 99))
        candVotes.add(mapOf(0 to 102, 1 to 111))

        val (contests, cvrs) = makeContestsWithVunder2(candVotes,
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
            assertEquals(contest.undervotes + contest.Nphantoms() * contest.info.voteForN, tab.undervotes)
            assertTrue(checkEquivilentVotes(contest.votes, tab.votes))
            println()
        }
    }

    @Test
    fun testOneAuditVunderFuzzer2() {
        val margin = .02
        val Nc = 10000
        val cvrFraction = 0.80
        val (contestOA, mvrs, cards, cardPools) =
            makeOneAuditTest(margin, Nc, cvrFraction = cvrFraction, undervoteFraction = 0.0, phantomFraction = 0.0)

        val cardPool = cardPools.first()
        val votes = cardPool.regVotes()[contestOA.id]!!
        println("cardPool=${cardPool.show()}  nvotes = ${votes.votes.values.sum()} poolFraction=${1-cvrFraction}")

        val info2 = ContestInfo("contest2", 2,  mapOf("Wes" to 1), SocialChoiceFunction.PLURALITY)
        val infos = mapOf(contestOA.id to contestOA.contest.info(), 2 to info2)
        val vunderFuzz = OneAuditVunderFuzzer2( cardPools, infos, 0.0, cards)

        val oaFuzzedPairs: List<Pair<AuditableCard, AuditableCard>> = vunderFuzz.mvrCvrPairs
        val fuzzedMvrs = oaFuzzedPairs.map { it.first }

        val fuzzedMvrTab = tabulateCards(fuzzedMvrs.iterator(), infos)
        println("fuzzedMvrTab= ${fuzzedMvrTab[contestOA.id]}")

        val fuzzedPool = calcOneAuditPoolsFromMvrs(
            infos,
            listOf(Population("pool42", 42, intArrayOf(1, 2), false)),
            fuzzedMvrs.map { it.cvr() },
        )
        println("fuzzedPool= ${fuzzedPool.first().show()}")

        // what if we choose the first 1000 ballots ??
        val limit = 1000
        val limitedCards = cards.subList(0, limit)
        val limitedFuzz = OneAuditVunderFuzzer2( cardPools, infos, 0.0, limitedCards)
        val limitedPairs: List<Pair<AuditableCard, AuditableCard>> = limitedFuzz.mvrCvrPairs
        val limitedMvrs = limitedPairs.map { it.first }

        val limitedMvrTab = tabulateCards(limitedMvrs.iterator(), infos)
        // TODO ContestTabulation(id=1 isIrv=false, voteForN=1, votes=[0=403, 1=392], nvotes=795 ncards=1000, novote=205, undervotes=205, overvotes=0)
        // TODO why so many undervotes ??
        println("cardlimit=$limit limitedMvrTab= ${limitedMvrTab[contestOA.id]}")

        val limitedPool = calcOneAuditPoolsFromMvrs(
            infos,
            listOf(Population(cardPool.name(), cardPool.poolId, intArrayOf(1,2), false )),
            limitedMvrs.map { it.cvr() },
        )
        println("limitedPool= ${limitedPool.first().show()}")
    }

}

fun makeContestsWithVunder2(
    candsv: List<Map<Int, Int>>, undervotes: List<Int>, phantoms: List<Int>, voteForNs: List<Int>? = null)
        : Pair<List<Contest>, List<Cvr>> {
    val candsMap = candsv.mapIndexed { idx, it -> Pair(idx, it ) }.toMap()
    val phantomMap = phantoms.mapIndexed { idx, it -> Pair(idx, it ) }.toMap()

    val contestVotes = mutableMapOf<Int, Vunder2>() // contestId -> VotesAndUndervotes
    candsv.forEachIndexed { idx: Int, cands: Map<Int, Int> ->  // use the idx as the Id
        val voteForN = if (voteForNs == null) 1 else voteForNs[idx]
        contestVotes[idx] = Vunder2.fromNpop(idx, undervotes[idx], cands.values.sum() + undervotes[idx], cands, voteForN = voteForN)
    }

    val cvrs = makeVunderCvrs(contestVotes, "ballot", null)

    // make the infos
    val tabVotes: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(cvrs.iterator())
    val infos = tabVotes.keys.associate { id ->
        val orgCands = candsMap[id]!!
        val candidateNames = orgCands.keys.associate { "cand$it" to it }
        val voteForN = if (voteForNs == null) 1 else voteForNs[id]
        Pair(id, ContestInfo("contest$id", id, candidateNames, SocialChoiceFunction.PLURALITY, voteForN = voteForN))
    }

    // make the contests
    val contestTabs: Map<Int, ContestTabulation> = tabulateCvrs(cvrs.iterator(), infos)
    val contests = contestTabs.map { (id, tab) ->
        val phantoms = phantomMap[id]!!
        Contest(infos[id]!!, tab.votes, tab.ncards + phantoms, tab.ncards)
    }

    // add the phantoms
    val phantoms =  makePhantomCvrs(contests)

    return Pair(contests, cvrs + phantoms)
}

