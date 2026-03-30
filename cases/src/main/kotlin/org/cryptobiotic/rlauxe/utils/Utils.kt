package org.cryptobiotic.rlauxe.utils

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.ContestTabulation
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

fun countPhantoms(contestTabSums: Map<Int, ContestTabulation>, contestNcs: Map<Int, Int>): Map<Int, Int> {
    val result = mutableMapOf<Int, Int>()
    contestTabSums.forEach { (_, contestSumTab) ->
        val useNc = contestNcs[contestSumTab.contestId] ?: contestSumTab.ncardsTabulated
        val Ncast = contestSumTab.ncardsTabulated
        result[contestSumTab.contestId] = useNc - Ncast
    }
    return result
}

fun tabulateCardsAndCount(cards: CloseableIterator<AuditableCard>, infos: Map<Int, ContestInfo>): Pair<Map<Int, ContestTabulation>, Int> {
    val tabs = mutableMapOf<Int, ContestTabulation>()
    var count = 0
    cards.use { cardIter ->
        while (cardIter.hasNext()) {
            val card = cardIter.next()
            count++
            infos.forEach { (contestId, info) ->
                if (card.hasContest(contestId)) { // TODO note that here, we believe possibleContests ...
                    val tab = tabs.getOrPut(contestId) { ContestTabulation(info) }
                    if (card.phantom) tab.nphantoms++
                    val votes = card.votes
                    if (votes != null && votes[contestId] != null) { // happens when cardStyle == all
                        val contestVote = votes[contestId]!!
                        tab.addVotes(contestVote, card.phantom)
                    } else {
                        tab.ncardsTabulated++
                    }
                }
            }
        }
    }
    return Pair(tabs, count)
}

// TODO is cvr.hasContest(contestId) the same as card.hasContest(contestId) ??
// no, card.hasContest(contestId) may be true but votes[contestId] = null; cards supporst missing contests
fun checkNpops(cvrs: List<Cvr>, cards: CloseableIterator<AuditableCard>, infoList: List<ContestInfo>): Pair<Map<Int, Int>, Int> {
    val npops = tabulateNpops(cvrs, infoList)
    val (npops2, count2) = tabulateNpopsFromCards(cards, infoList)
    if (npops2 != npops || count2 != cvrs.size) {
        print("tabulateNpopsFromCvrs != tabulateNpopsFromCards")
        println("     $count2 ? ${cvrs.size}")
        npops2.forEach { (key2, value2) ->
            val value1 = npops[key2]
            if (value2 != value1) {
                println("   contest $key2:  card: $value2 != $value1 cvr")
            }
        }
        checkHasContest(cvrs, cards, infoList)
        throw RuntimeException()
    }
    return Pair(npops2, count2)
}

fun tabulateNpops(cvrs: List<Cvr>, infos: List<ContestInfo>): Map<Int, Int> {
    val npops = mutableMapOf<Int, Int>()
    cvrs.forEach { cvr ->
        infos.forEach { info ->
            if (cvr.hasContest(info.id)) {
                val npop = npops.getOrPut(info.id) { 0 }
                npops[info.id] = npop + 1
            }
        }
    }
    return npops
}

fun tabulateNpopsFromCards(cards: CloseableIterator<AuditableCard>, infos: List<ContestInfo>): Pair<Map<Int, Int>, Int> {
    val npops = mutableMapOf<Int, Int>()
    var count = 0
    cards.use { cardIter ->
        while (cardIter.hasNext()) {
            val card = cardIter.next()
            count++
            infos.forEach { info ->
                if (card.hasContest(info.id)) {
                    val npop = npops.getOrPut(info.id) { 0 }
                    npops[info.id] = npop + 1
                }
            }
        }
    }
    return Pair(npops, count)
}

// cvrs and the cards reference the same ballots
fun checkHasContest(cvrs: List<Cvr>, cards: CloseableIterator<AuditableCard>, infos: List<ContestInfo>) {
    cards.use { cardIter ->
        var count = 0
        cvrs.forEach { cvr ->
            val card = cardIter.next()
            for (info in infos) {
                if (card.location != cvr.id) {
                    print("${card.location} != ${cvr.id}")
                    break
                }
                if (card.hasContest(info.id) != cvr.hasContest(info.id)) {
                    println(card)
                    println("Cvr: $cvr ($count)")
                    println()
                    break
                }
            }
            count++
        }
    }
}


