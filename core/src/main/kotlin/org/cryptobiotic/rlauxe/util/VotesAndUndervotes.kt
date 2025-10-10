package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.core.Cvr
import kotlin.random.Random

// This is a way to create test Cvrs that match known vote totals and undervotes for a contest
class VotesAndUndervotes(candVotes: Map<Int, Int>, val undervotes: Int, val voteForN: Int) {
    val candVotesSorted: Map<Int, Int> = candVotes.toList().sortedBy{ it.second }.reversed().toMap() // reverse sort by largest vote
    private val candidateIds = candVotesSorted.keys.toList()

    // use to pick candidates
    val votes: IntArray = candVotesSorted.map { it.value }.toIntArray()
    var undervotesUsed = 0
    var cand0 = 0
    var finishedVotes = false

    fun isNotEmpty(): Boolean {
        return votes.any { it != 0 } || (undervotesUsed < undervotes)
    }

    fun pickRandomCandidatesAndDecrement() : List<Int> {
        val result = mutableListOf<Int>()

        if (finishedVotes) {
            if (undervotesUsed < undervotes) {
                undervotesUsed += voteForN
                return result
            } else {
                println("never because isNotEmpty should have been called?")
            }
        }

        if (votes[cand0] > 0) {
            result.add(cand0)
            votes[cand0] = votes[cand0] - 1
        } else {
            cand0++
            while (cand0 < candVotesSorted.size) { // increment working candidate
                if (votes[cand0] > 0) {
                    result.add(cand0)
                    votes[cand0] = votes[cand0] - 1
                    break
                }
                cand0++
            }
            if (cand0 >= candVotesSorted.size) { // ran out of votes; are there undervotes left ??
                finishedVotes = true
                if (undervotesUsed < undervotes) {
                    undervotesUsed += voteForN
                    return result
                } else {
                    println("never because isNotEmpty should have been called?")
                }
            }
        }

        if (voteForN > 1) {
            val varr = IntArray(votes.size) { if (it <= cand0) 0 else votes[it] }
            var count = 1
            while (count < voteForN && isNotEmpty()) {
                // when you run out of votes, use undervotes
                if (varr.sum() == 0) {
                    undervotesUsed += (voteForN - count)
                    return result.map { candidateIds[it] }
                }
                // otherwise pick a vote
                val candIdx = pickOneCandidate(varr)
                if (!result.contains(candIdx)) { // no duplicates
                    result.add(candIdx)
                    votes[candIdx] = votes[candIdx] - 1
                    varr[candIdx] = 0 // dont pick this one again
                    count++
                } else {
                    println("never because should not be able to pick a duplicate?")
                }
            }
        }

        // convert to Ids
        return result.map { candidateIds[it] }
    }

    // think of varr as a partition of votes
    private fun pickOneCandidate(varr: IntArray) : Int {
        val nvotes = varr.sum()
        // pick a number from 0 to number of votes unchosen
        val choice = Random.nextInt(nvotes)
        // find where that lives in the partition
        var sum = 0
        var candIdx = 0
        while (candIdx < varr.size) {
            sum += varr[candIdx]
            if (choice < sum) break
            candIdx++
        }
        return candIdx
    }

    override fun toString() = buildString {
        append("VotesAndUndervotes(undervotes=$undervotes, voteForN=$voteForN, votes=${candVotesSorted} candidateIds=$candidateIds)")
    }

    fun votesAndUndervotes(): Map<Int, Int> {
        return (candVotesSorted.map { Pair(it.key, it.value)} + Pair(candVotesSorted.size, undervotes)).toMap().toSortedMap()
    }
}

// make cvrs until we exhaust the votes
// this algorithm puts as many contests as possible on each cvr
fun makeVunderCvrs(contestVotes: Map<Int, VotesAndUndervotes>, poolId: Int?): List<Cvr> {
    val rcvrs = mutableListOf<Cvr>()

    var count = 1
    var usedOne = true
    while (usedOne) {
        usedOne = false
        val cvrId = if (poolId == null) "ballot${count}"
                             else "pool${poolId}-${count}"
        val cvb2 = CvrBuilder2(cvrId, phantom = false, poolId = poolId)
        contestVotes.entries.forEach { (contestId, vunders) ->
            if (vunders.isNotEmpty()) {
                // pick random candidates for the contest
                val useCandidates = vunders.pickRandomCandidatesAndDecrement()
                // add it to cvr
                cvb2.addContest(contestId, useCandidates.toIntArray())
                usedOne = true
            }
        }
        if (usedOne) rcvrs.add(cvb2.build())
        count++
    }

    rcvrs.shuffle()
    return rcvrs
}
