package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.util.VotesAndUndervotes
import kotlin.random.Random

data class BallotPool(
    val name: String,
    val poolId: Int,
    val contest:Int,
    val ncards: Int,          // ncards for this contest in this pool; TODO hasStyles = false?
    val votes: Map<Int, Int>, // candid -> nvotes // the diff from ncards tell you the undervotes
) {

    // TODO does this really agree with the average assorter?
    // this could go from -1 to 1. TODO shouldnt that be -u to u ??
    fun calcReportedMargin(winner: Int, loser: Int): Double {
        if (ncards == 0) return 0.0
        val winnerVote = votes[winner] ?: 0
        val loserVote = votes[loser] ?: 0
        return (winnerVote - loserVote) / ncards.toDouble()
    }

    fun votesAndUndervotes(voteForN: Int, ncandidates: Int): Map<Int, Int> {
        val poolVotes = votes.values.sum()
        val poolUndervotes = ncards * voteForN - poolVotes
        return (votes.map { Pair(it.key, it.value)} + Pair(ncandidates, poolUndervotes)).toMap()
    }

    fun votesAndUndervotes(voteForN: Int): VotesAndUndervotes {
        val poolUndervotes = ncards * voteForN - votes.values.sum()
        return VotesAndUndervotes(votes, poolUndervotes, voteForN)
    }
}

class VotesAndUndervotes0(val candVotes: Map<Int, Int>, val undervotes: Int, val voteForN: Int) {
    val votes: IntArray = candVotes.map { it.value }.toIntArray()
    private val candidateIds = candVotes.keys.toList()
    var undervotesUsed = 0
    private var unchosenVotes = candVotes.map { it.value }.sum()

    fun isNotEmpty(): Boolean {
        return votes.any { it != 0 } || (undervotesUsed < undervotes)
    }

    // pick voteForN candidates that have votes left
    // wait to the end to add undervotes
    fun pickRandomCandidatesAndDecrement() : List<Int> {
        val result = mutableListOf<Int>()

        var count = 0
        var miss = 0
        while (count < voteForN && isNotEmpty()) {
            // when you run out of votes, use undervotes
            if (votes.sum() == 0 ) {
                undervotesUsed += (voteForN - count)
                return result
            }
            // otherwise pick a vote
            val candIdx = pickOneCandidate(votes)
            if (!result.contains(candIdx)) { // no duplicates
                result.add(candIdx)
                votes[candIdx] = votes[candIdx] - 1
                unchosenVotes--
                count++
            } else if ( votes.filter{ it > 0 }.count() <= 1) {
                // use an undervote when all other candidates are 0
                undervotesUsed++
                count++
            } else {
                // debugging
                miss++
                if (miss > 10) {
                    // remove already chosen candidates
                    val dvotes = votes.copyOf()
                    result.forEach { dvotes[it] = 0 }
                    val candIdx = pickOneCandidate(dvotes)
                    result.add(candIdx)
                    votes[candIdx] = votes[candIdx] - 1
                    unchosenVotes--
                    count++
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
        append("VotesAndUndervotes(undervotes=$undervotes, voteForN=$voteForN, votes=${votes.contentToString()} candidateIds=$candidateIds)")
    }

    fun votesAndUndervotes(): Map<Int, Int> {
        return (candVotes.map { Pair(it.key, it.value)} + Pair(votes.size, undervotes)).toMap().toSortedMap()
    }
}