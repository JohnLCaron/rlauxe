package org.cryptobiotic.rlauxe.raire

import au.org.democracydevelopers.raire.irv.Vote
import kotlin.collections.getOrPut

/**
 * Adapted from au.org.democracydevelopers.raire.util.VoteConsolidator
 * For a single Contest.
 *
 * A utility class for building an array of Vote[] structures
 * from provided preference lists. The main purpose is to convert
 * a large number of weight votes, possibly the same, into a
 * set of unique votes with multiplicities.
 *
 * This is what gets fed into the Raire library.
 */
// Note that the candidates go from index 0 ... ncandidates-1, not candidate ids
class VoteConsolidator {
    private val votes = mutableMapOf<HashableIntArray, Int>() // candidate ranks -> nvotes

    fun addVote(pref: IntArray) {
            val key = HashableIntArray(pref)
            votes[key] = votes.getOrPut(key) { 0 } + 1
    }

    // add other's votes into this
    fun addVotes(other: VoteConsolidator) {
        other.votes.forEach { key, count ->
            votes[key] = votes.getOrPut(key) { 0 } + count
        }
    }

    fun makeVotes(): Array<Vote> {
        val voteList = votes.map { Vote(it.value, it.key.array) }  //n, IntArray
        return Array(voteList.size) { voteList[it] }
    }

    fun makeVoteList(): List<VoteList> {
        return votes.map { VoteList(it.value, it.key.array.toList()) }
    }

    override fun toString() = buildString {
        votes.forEach { (key, value) ->
            appendLine(  "  ${key.array.contentToString()} == $value ")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VoteConsolidator

        return votes == other.votes
    }

    override fun hashCode(): Int {
        return votes.hashCode()
    }
}

// Adapted from au.org.democracydevelopers.raire.util.VoteConsolidator
/** A wrapper around int[] that works as a key in a hash map  */
private class HashableIntArray(val array: IntArray) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as HashableIntArray
        return array.contentEquals(that.array)
    }

    override fun hashCode(): Int {
        return array.contentHashCode()
    }
}

/////////////////////////////////////////////////////////////////////////////////////////
// Added here because I want to show the runoff stages, embodied in nenFirstChoices() and nebFirstChoices()
// Note that the candidates go from 0 ... ncandidates-1, not candidate ids
data class VoteList(val n: Int, val candRanks: List<Int>)
data class VoteSequences(val votes: List<VoteList>) {

    fun nenFirstChoices(winner: Int, loser: Int): Map<Int, Int> {
        val firstChoices = mutableMapOf<Int, Int>()
        votes.forEach { vote ->
            if (vote.candRanks.isNotEmpty()) {
                val firstChoice = vote.candRanks.first()
                if (firstChoice == winner || firstChoice == loser) {
                    firstChoices[firstChoice] = firstChoices.getOrDefault(firstChoice, 0) + vote.n
                }
            }
        }
        return firstChoices
    }

    fun margin(winner: Int, loser: Int, votes: Map<Int, Int>): Int {
        val winnerVotes = votes[winner] ?: 0
        val loserVotes = votes[loser] ?: 0
        return winnerVotes - loserVotes
    }

    fun nebFirstChoices(winner: Int, loser: Int): Map<Int, Int> {
        val firstChoices = nenFirstChoices(winner, loser)
        val minWinner = firstChoices[winner] ?: 0
        var maxLoser = 0
        votes.forEach { vote ->
            val winnerIdx = vote.candRanks.indexOf(winner)
            val loserIdx = vote.candRanks.indexOf(loser)
            if (loserIdx != -1) {
                if ((winnerIdx == -1) || (loserIdx < winnerIdx)) {
                    maxLoser += vote.n
                }
            }
        }
        return mapOf(winner to minWinner, loser to maxLoser)
    }

    companion object {
        fun eliminate(startingVotes: List<VoteList>, continuing:List<Int>): VoteSequences {
            val evotes = startingVotes.map { orgVoteList ->
                val eprefs = orgVoteList.candRanks.filter { continuing.contains(it) }
                VoteList(orgVoteList.n, eprefs)
            }
            return VoteSequences(evotes)
        }
    }
}
