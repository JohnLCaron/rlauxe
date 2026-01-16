package org.cryptobiotic.rlauxe.util

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.verify.checkEquivilentVotes
import kotlin.random.Random

private val logger = KotlinLogging.logger("VunderBar")

// This is a way to create test Cvrs that match known vote totals and undervotes for one contest or pool
// pass2, make choices random, dont assume that you will exhaust votes and then shuffle
// ok for voteForN > 1, but not IRV

// vunder = "votes and undervotes"
data class Vunder(val candVotes: Map<Int, Int>, val undervotes: Int, val voteForN: Int) {
    val candVotesSorted: Map<Int, Int> =
        candVotes.toList().sortedBy { it.second }.reversed().toMap() // reverse sort by largest vote

    // assume undervotes = info.voteForN * Ncast - nvotes
    val undervoteId = if (candVotes.isEmpty()) 1 else candVotes.maxOf { it.key } + 1

    // vunder = "votes and undervotes"
    val vunder: List<Pair<Int, Int>> = candVotes.toList() + Pair(undervoteId, undervotes)
    val nvunder = vunder.size  // ncandidates + 1

    override fun toString() = buildString {
        append("votes=${candVotesSorted} undervotes=$undervotes, voteForN=$voteForN")
    }
}

// call this to create a new set of cvrs
class VunderPicker(val vunder: Vunder) {
    var vunderRemaining = mutableListOf<Pair<Int, Int>>()
    var vunderLeft = 0

    init {
        vunderRemaining = mutableListOf()
        vunderRemaining.addAll(vunder.vunder)
        vunderLeft = vunder.vunder.sumOf { it.second }
    }

    fun isEmpty() = vunderLeft <= 0
    fun isNotEmpty() = vunderLeft > 0

    fun pickRandomCandidatesAndDecrement() : IntArray {

        if (isEmpty()) {
           logger.error{"Vunder called when isEmpty"}
            return intArrayOf()
        }

        val result = if (vunder.voteForN == 1) {
            val voteFor = chooseCandidateAndDecrement(Random.nextInt(vunderLeft))
            if (voteFor == vunder.undervoteId) intArrayOf() else intArrayOf(voteFor)
        } else {
            chooseCandidatesAndDecrement(vunder.voteForN)
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
        while (idx < vunder.nvunder) {
            nvotesLeft = vunderRemaining[idx].second // votes left for this candidate
            sum += nvotesLeft
            if (randomChoice < sum) break
            idx++
        }
        require(nvotesLeft > 0)
        require(idx < vunder.nvunder)

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
            if (candId == vunder.undervoteId) { // multiple undervotes ok, undervote adds nothing to result
                needVotes--

            } else { // remove candidate to prevent duplicates
                result.add(candId)
                useRemaining.removeAt(candIdx)
                needVotes--
            }
        }
        return result.toIntArray()
    }

    // return cardId, candIdx
    private fun chooseFromRemaining(remaining: List<Pair<Int, Int>>) : Pair<Int, Int> {
        val nvotes = remaining.map { it.second }.sum()
        if (nvotes <= 0) {
            // weve run out of votes, including undervotes, only choice is to add another undervote
            return Pair(vunder.undervoteId, remaining.size - 1) // undervote idx always last one in remaining; not actually used
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
}

// combines Vunder for multiple contests into cvrs for one pool
// make cvrs until we exhaust the votes
// this algorithm puts as many contests as possible on each cvr
// the number of cvrs can vary when there are multiple contests

// vunders: contest id -> Vunder
fun makeVunderCvrs(vunders: Map<Int, Vunder>, poolName: String, poolId: Int?): List<Cvr> {
    val vunderPickers = vunders.mapValues { VunderPicker(it.value) }

    val rcvrs = mutableListOf<Cvr>()
    var count = 1
    var usedOne = true
    while (usedOne) {
        usedOne = false
        val cvrId = "${poolName}-${count}"
        val cvb2 = CvrBuilder2(cvrId, phantom = false, poolId = poolId)
        vunderPickers.entries.forEach { (contestId, vunderPicker) ->
            if (vunderPicker.isNotEmpty()) {
                // pick random candidates for the contest
                val useCandidates = vunderPicker.pickRandomCandidatesAndDecrement()
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

    rcvrs.shuffle()
    return rcvrs
}

/////////////////////////////////////////////
//// use these when you dont have ContestInfo yet
// Number of votes in each contest, return contestId -> candidateId -> nvotes
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

fun tabulateVotesWithUndervotes(cvrs: Iterator<Cvr>, contestId: Int, ncands: Int, voteForN: Int = 1): Vunder {
    val result = mutableMapOf<Int, Int>()
    var undervotes = 0
    cvrs.forEach{ cvr ->
        if (cvr.hasContest(contestId) && !cvr.phantom) {
            val candVotes = cvr.votes[contestId] // should always succeed
            if (candVotes != null) {
                if (candVotes.size < voteForN) {  // undervote
                    undervotes += (voteForN - candVotes.size)
                }
                for (cand in candVotes) {
                    val count = result[cand] ?: 0
                    result[cand] = count + 1
                }
            }
        }
    }
    return Vunder(result, undervotes, voteForN)
}