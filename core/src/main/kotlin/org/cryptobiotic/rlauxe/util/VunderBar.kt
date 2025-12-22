package org.cryptobiotic.rlauxe.util

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.estimate.chooseNewCandidate
import org.cryptobiotic.rlauxe.estimate.switchCandidateRankings
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.verify.checkEquivilentVotes
import kotlin.random.Random

private val logger = KotlinLogging.logger("VunderBar")

////////////////////////////////////////////////////////////////////////////////
// OneAudit Estimation Sampling

class OneAuditVunderBarFuzzer(
    val vunderBar: VunderBar,
    val infos: Map<Int, ContestInfo>,
    val fuzzPct: Double,
) {
    val isIRV = infos.mapValues { it.value.isIrv }

    fun reset() {
        vunderBar.reset()
    }

    fun makePairsFromCards(cards: List<AuditableCard>): List<Pair<AuditableCard, AuditableCard>> {
        val mvrs = cards.map { card ->
            if (card.poolId != null) {
                vunderBar.simulatePooledCard(card)
            } else if (card.votes != null) {
                makeFuzzedCardFromCard(infos, isIRV, card, fuzzPct)
            } else {
                throw RuntimeException("card must be pooled or have votes")
            }
        }
        return mvrs.zip(cards)
    }
}

// claims you can use IRV ?
private fun makeFuzzedCardFromCard(
    infos: Map<Int, ContestInfo>,
    isIRV: Map<Int, Boolean>,
    card: AuditableCard, // must have votes, ie have a Cvr
    fuzzPct: Double,
    undervotes: Boolean = true, // chooseNewCandidateWithUndervotes
) : AuditableCard {
    if (fuzzPct == 0.0 || card.phantom) return card
    val r = Random.nextDouble(1.0)
    if (r > fuzzPct) return card

    val cardb = CardBuilder.fromCard(card)
    cardb.possibleContests.forEach { contestId ->
        val info = infos[contestId]
        if (info != null) {
            if (isIRV[contestId] ?: false) {
                val currentVotes = cardb.votes[contestId]?.toList()?.toMutableList() ?: mutableListOf<Int>()
                switchCandidateRankings(currentVotes, info.candidateIds)
                cardb.replaceContestVotes(contestId, currentVotes.toIntArray())
            } else {
                val votes = cardb.votes[contestId]
                val currId: Int? = if (votes == null || votes.size == 0) null else votes[0] // only one vote allowed
                // choose a different candidate, or none.
                val ncandId = chooseNewCandidate(currId, info.candidateIds, undervotes)
                cardb.replaceContestVote(contestId, ncandId)
            }
        }
    }

    return cardb.build()
}

// simulate pooled data from the pool values; not for IRV
class VunderBar(val pools: List<OneAuditPoolIF>, infos: Map<Int, ContestInfo>) {
    val vunderPools: Map<Int, VunderPool>

    init {
        vunderPools = pools.map { pool ->
            val vunders = pool.contests().associate { contestId ->
                Pair( contestId, pool.votesAndUndervotes(contestId, infos[contestId]?.voteForN ?: 1))
            }
            VunderPool(vunders, pool.poolName, pool.poolId)
        }.associateBy { it.poolId }
    }

    fun reset() {
        vunderPools.forEach{ it.value.reset() }
    }

    // for the given pooled card with no votes, simulate one with votes, staying within the pool vote totals.
    fun simulatePooledCard(card: AuditableCard): AuditableCard {
        val poolId = card.poolId!!
        val vunderPool = vunderPools[poolId]!!
        return vunderPool.simulatePooledCard(card)
    }
}

// for one pool
class VunderPool(val vunders: Map<Int, Vunder>, val poolName: String, val poolId: Int ) {

    fun reset() {
        vunders.forEach{ it.value.reset() }
    }

    fun simulatePooledCard(card: AuditableCard): AuditableCard {
        val cardb = CardBuilder.fromCard(card)
        card.contests().forEach { contestId ->
            val vunders = vunders[contestId]
            if (vunders == null || vunders.isEmpty())
                cardb.replaceContestVotes(contestId, intArrayOf())
            else {
                val cands = vunders.pickRandomCandidatesAndDecrement()
                cardb.replaceContestVotes(contestId, cands)
            }
        }
        return cardb.build()
    }

}

// This is a way to create test Cvrs that match known vote totals and undervotes for one contest or pool
// pass2, make choices random, dont assume that you will exhaust votes and then shuffle
// ok for voteForN > 1, but not IRV
data class Vunder(val candVotes: Map<Int, Int>, val undervotes: Int, val voteForN: Int) {
    val candVotesSorted: Map<Int, Int> = candVotes.toList().sortedBy{ it.second }.reversed().toMap() // reverse sort by largest vote

    // assume undervotes = info.voteForN * Ncast - nvotes
    val undervoteId = if (candVotes.isEmpty()) 1 else candVotes.maxOf{ it.key } + 1
    // vunder = "votes and undervotes"
    val vunder: List<Pair<Int, Int>> = candVotes.toList() + Pair(undervoteId, undervotes)
    val nvunder = vunder.size  // ncandidates + 1

    var vunderRemaining = mutableListOf<Pair<Int, Int>>()
    var vunderLeft = 0

    init {
        reset()
    }

    // call this to create a new set of cvrs
    fun reset() {
        vunderRemaining = mutableListOf()
        vunderRemaining.addAll(vunder)
        vunderLeft = vunder.sumOf { it.second }
    }

    fun isEmpty() = vunderLeft <= 0
    fun isNotEmpty() = vunderLeft > 0

    fun pickRandomCandidatesAndDecrement() : IntArray {

        if (isEmpty()) {
           logger.error{"Vunder called when isEmpty"}
            return intArrayOf()
        }

        val result = if (voteForN == 1) {
            val voteFor = chooseCandidateAndDecrement(Random.nextInt(vunderLeft))
            if (voteFor == undervoteId) intArrayOf() else intArrayOf(voteFor)
        } else {
            chooseCandidatesAndDecrement(voteForN)
        }
        return result
    }

    // this is a uniform sampling over the remaining votes and undervotes
    // randomChoice is a number from 0..votesLeft
    // shrink the partition as votes are taken from it
    // return candidateId
    fun chooseCandidateAndDecrement(randomChoice: Int): Int {
        val check = vunderRemaining.sumOf { it.second }
        require(check == vunderLeft)
        require(randomChoice in 0 until vunderLeft)

        var sum = 0
        var nvotesLeft = 0
        var idx = 0
        while (idx < nvunder) {
            nvotesLeft = vunderRemaining[idx].second // votes left for this candidate
            sum += nvotesLeft
            if (randomChoice < sum) break
            idx++
        }
        require(nvotesLeft > 0)
        require(idx < nvunder)

        return decrementCandidate(idx)
    }

    // select multiple votes over the remaining votes and undervotes
    // can choose multiple undervotes, but no duplicate candidates
    fun chooseCandidatesAndDecrement(voteForN: Int): IntArray {
        var needVotes = voteForN
        val result = mutableListOf<Int>()
        val useRemaining = mutableListOf<Pair<Int, Int>>()
        useRemaining.addAll(vunderRemaining)

        while (needVotes > 0) {
            val (candId, candIdx) = chooseFromRemaining(useRemaining)
            if (candId == undervoteId) { // multiple undervotes ok, undervote adds nothing to result
                needVotes--

            } else { // remove candidate to prevent duplicates
                result.add(candId)
                useRemaining.removeAt(candIdx)
                needVotes--
            }
        }
        //if (needVotes != 0)
        //    print("checkit")
        return result.toIntArray()
    }

    // return cardId, candIdx
    private fun chooseFromRemaining(remaining: List<Pair<Int, Int>>) : Pair<Int, Int> {
        val nvotes = remaining.map { it.second }.sum()
        if (nvotes <= 0) {
            // weve run out of votes, including undervotes, only choice is to add another undervote
            return Pair(undervoteId, remaining.size - 1) // undervote idx always last one in remaining; not actually used
        }
        // pick a number from 0 to number of votes unchosen
        val randomChoice = Random.nextInt(nvotes)

        // find where that lives in the partition
        var sum = 0
        var nvotesLeft = 0
        var idx = 0
        while (idx < remaining.size) {
            nvotesLeft = remaining[idx].second // votes left for this candidate
            sum += nvotesLeft
            if (randomChoice < sum) break
            idx++
        }
        require(nvotesLeft > 0)
        require(idx < remaining.size)

        val candidateId = remaining[idx].first
        decrementCandidateById(candidateId)
        return Pair(candidateId, idx)
    }

    fun decrementCandidate(candIdx: Int): Int {
        val candidateId = vunderRemaining[candIdx].first
        val nvotesLeft = vunderRemaining[candIdx].second
        if (nvotesLeft > 0) {
            vunderRemaining[candIdx] = Pair(candidateId, nvotesLeft - 1)
            vunderLeft--
        }
        return candidateId
    }

    fun decrementCandidateById(candId: Int): Int {
        val idx = vunderRemaining.indexOfFirst { it.first == candId }
        val vunder = vunderRemaining[idx]
        val candidateId = vunder.first
        val nvotesLeft = vunder.second
        if (nvotesLeft > 0) {
            vunderRemaining[idx] = Pair(candidateId, nvotesLeft - 1) // decr and replace
            vunderLeft--
        }
        return candidateId
    }

    override fun toString() = buildString {
        append("votes=${candVotesSorted} undervotes=$undervotes, voteForN=$voteForN")
    }
}

// combines Vunder for multiple contests into cvrs for one pool
// make cvrs until we exhaust the votes
// this algorithm puts as many contests as possible on each cvr
// the number of cvrs can vary when there are multiple contests
fun makeVunderCvrs(vunders: Map<Int, Vunder>, poolName: String, poolId: Int?): List<Cvr> {
    val rcvrs = mutableListOf<Cvr>()

    var count = 1
    var usedOne = true
    while (usedOne) {
        usedOne = false
        val cvrId = "${poolName}-${count}"
        val cvb2 = CvrBuilder2(cvrId, phantom = false, poolId = poolId)
        vunders.entries.forEach { (contestId, vunder) ->
            if (vunder.isNotEmpty()) {
                // pick random candidates for the contest
                val useCandidates = vunder.pickRandomCandidatesAndDecrement()
                // add it to cvr
                cvb2.addContest(contestId, useCandidates)
                usedOne = true
            }
        }
        if (usedOne) rcvrs.add(cvb2.build())
        count++
    }

    // find bug
    val votesFromCvrs =  tabulateVotesFromCvrs(rcvrs.iterator())
    votesFromCvrs.forEach { (id, voteFromCvrs) ->
        val vunder = vunders[id]!!
        if (!checkEquivilentVotes(vunder.candVotes, voteFromCvrs)) {
            println("candVotes ${vunder.candVotes.toSortedMap()} != ${voteFromCvrs.toSortedMap()} voteFromCvrs")
            println(vunder)
            println()
            throw RuntimeException("candVotes ${vunder.candVotes.toSortedMap()} != ${voteFromCvrs.toSortedMap()} voteFromCvrs")
        }
    }

    //vunders.values.forEach { vunder ->
        // if (!vunder.isEmpty())
    //        println(vunder)
    //}

    rcvrs.shuffle()
    return rcvrs
}

fun tabulateVotesFromCvrs(cvrs: Iterator<Cvr>): Map<Int, Map<Int, Int>> {
    val votes = mutableMapOf<Int, MutableMap<Int, Int>>()
    for (cvr in cvrs) {
        for ((contestId, conVotes) in cvr.votes) {
            val accumVotes = votes.getOrPut(contestId) { mutableMapOf() }
            for (cand in conVotes) {
                val accum = accumVotes.getOrPut(cand) { 0 }
                accumVotes[cand] = accum + 1
            }
        }
    }
    return votes
}
