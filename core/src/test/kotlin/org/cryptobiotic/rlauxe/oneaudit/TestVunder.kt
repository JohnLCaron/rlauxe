package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.audit.makePhantomCvrs
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.estimate.OneAuditVunderBarFuzzer
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.verify.checkEquivilentVotes
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import org.cryptobiotic.rlauxe.util.tabulateVotesFromCvrs
import org.cryptobiotic.rlauxe.util.Vunder
import org.cryptobiotic.rlauxe.util.VunderBar
import org.cryptobiotic.rlauxe.util.makeVunderCvrs
import org.cryptobiotic.rlauxe.util.tabulateCards
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.collections.List
import kotlin.test.Test
import kotlin.test.assertEquals


class TestVunder {

    @Test
    fun testMakeVunderCvrs() {
        val contestVotes = mutableMapOf<Int, Vunder>() // contestId -> VotesAndUndervotes
        val candVotes0 = mapOf(0 to 200, 1 to 123, 2 to 17)
        contestVotes[0] = Vunder(candVotes0, 51, 1)

        val candVotes1 = mapOf(0 to 71, 1 to 123, 2 to 3)
        contestVotes[1] = Vunder(candVotes1, 51, 1)

        val cvrs = makeVunderCvrs(contestVotes, "poolName", null)

        // check
        val tabVotes: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(cvrs.iterator())
        contestVotes.forEach { (contestId, vunders) ->
            val tv = tabVotes[contestId] ?: emptyMap()
            println("contestId=${contestId}")
            println("  tabVotes=${tv}")
            println("  vunders= ${vunders.vunder.toMap()}")
        }

        assertEquals("votes={0=200, 1=123, 2=17} undervotes=51, voteForN=1", contestVotes[0].toString())
        assertEquals(mapOf(0 to 200, 1 to 123, 2 to 17, 3 to 51), contestVotes[0]!!.vunder.toMap())

        assertEquals("votes={1=123, 0=71, 2=3} undervotes=51, voteForN=1", contestVotes[1].toString())
        assertEquals(mapOf(0 to 71, 1 to 123, 2 to 3, 3 to 51), contestVotes[1]!!.vunder.toMap())
    }

    @Test
    fun testMakeVunderCvrsVotesFor2() {
        val contestVotes = mutableMapOf<Int, Vunder>() // contestId -> VotesAndUndervotes
        val candVotes0 = mapOf(0 to 200, 1 to 123, 2 to 17)
        contestVotes[0] = Vunder(candVotes0, 51, 2)

        val candVotes1 = mapOf(0 to 71, 1 to 123, 2 to 3)
        contestVotes[1] = Vunder(candVotes1, 51, 1)

        val cvrs = makeVunderCvrs(contestVotes, "poolName", null)

        // check
        val tabVotes: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(cvrs.iterator())
        contestVotes.forEach { (contestId, vunders) ->
            val tv = tabVotes[contestId] ?: emptyMap()
            println("contestId=${contestId}")
            println("  tabVotes=${tv}")
            println("  vunders= ${vunders.candVotesSorted}")
        }

        assertEquals("votes={0=200, 1=123, 2=17} undervotes=51, voteForN=2", contestVotes[0].toString())
        assertEquals(mapOf(0 to 200, 1 to 123, 2 to 17, 3 to 51), contestVotes[0]!!.vunder.toMap())
    }

    @Test
    fun testMakeContestsWithVunder() {
        val candVotes = mutableListOf<Map<Int, Int>>()
        candVotes.add(mapOf(0 to 200, 1 to 123, 2 to 17))
        candVotes.add(mapOf(0 to 71, 1 to 123, 2 to 0, 3 to 77, 4 to 99))
        candVotes.add(mapOf(0 to 102, 1 to 111))

        val (contests, cvrs) = makeContestsWithVunder(candVotes, listOf(15, 123, 3), listOf(2, 7, 0))

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

        val (contests, cvrs) = makeContestsWithVunder(candVotes,
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
    fun testOneAuditVunderBarFuzzer() {
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
        val vunderFuzz = OneAuditVunderBarFuzzer( VunderBar(cardPools), infos, .00)

        val oaFuzzedPairs: List<Pair<AuditableCard, AuditableCard>> = vunderFuzz.makePairsFromCards(cards)
        val fuzzedMvrs = oaFuzzedPairs.map { it.first }

        val fuzzedMvrTab = tabulateCards(fuzzedMvrs.iterator(), infos)
        println("fuzzedMvrTab= ${fuzzedMvrTab[contestOA.id]}")

        val fuzzedPool = calcCardPoolsFromMvrs(
            infos,
            cardStyles = listOf(CardStyle("pool42", listOf(1,2), 42)),
            fuzzedMvrs.map { it.cvr() },
        )
        println("fuzzedPool= ${fuzzedPool.first().show()}")

        // what if we choose the first 1000 ballots ??
        val limit = 1000
        val limitedCards = cards.subList(0, limit)
        vunderFuzz.reset()
        val limitedPairs: List<Pair<AuditableCard, AuditableCard>> = vunderFuzz.makePairsFromCards(limitedCards)
        val limitedMvrs = limitedPairs.map { it.first }

        val limitedMvrTab = tabulateCards(limitedMvrs.iterator(), infos)
        // TODO ContestTabulation(id=1 isIrv=false, voteForN=1, votes=[0=403, 1=392], nvotes=795 ncards=1000, novote=205, undervotes=205, overvotes=0)
        // TODO why so many undervotes ??
        println("cardlimit=$limit limitedMvrTab= ${limitedMvrTab[contestOA.id]}")

        val limitedPool = calcCardPoolsFromMvrs(
            infos,
            cardStyles = listOf(CardStyle(cardPool.name(), listOf(1,2), cardPool.poolId)),
            limitedMvrs.map { it.cvr() },
        )
        println("limitedPool= ${limitedPool.first().show()}")
    }

}

fun makeContestsWithVunder(
    candsv: List<Map<Int, Int>>, undervotes: List<Int>, phantoms: List<Int>, voteForNs: List<Int>? = null)
        : Pair<List<Contest>, List<Cvr>> {
    val candsMap = candsv.mapIndexed { idx, it -> Pair(idx, it ) }.toMap()
    val phantomMap = phantoms.mapIndexed { idx, it -> Pair(idx, it ) }.toMap()

    val contestVotes = mutableMapOf<Int, Vunder>() // contestId -> VotesAndUndervotes
    candsv.forEachIndexed { idx: Int, cands: Map<Int, Int> ->  // use the idx as the Id
        val voteForN = if (voteForNs == null) 1 else voteForNs[idx]
        contestVotes[idx] = Vunder(cands, undervotes[idx], voteForN = voteForN)
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

