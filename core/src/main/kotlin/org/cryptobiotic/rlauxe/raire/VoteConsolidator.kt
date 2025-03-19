package org.cryptobiotic.rlauxe.raire

import au.org.democracydevelopers.raire.irv.Vote
import kotlin.collections.getOrPut

/**
 * For a single Contest.
 *
 * A utility class for building an array of Vote[] structures
 * from provided preference lists. The main purpose is to convert
 * a large number of weight votes, possibly the same, into a
 * set of unique votes with multiplicities.
 *
 * It is also (optionally) capable of converting a preference list of
 * strings into the array of integer preferences used by Raire.
 */
class VoteConsolidator {
    private val votes = mutableMapOf<HashableIntArray, Int>()

    fun addVote(pref: IntArray) {
        val key = HashableIntArray(pref)
        votes[key] = votes.getOrPut(key) { 0 } + 1
    }

    fun makeVotes(): Array<Vote> {
        val voteList = votes.map { Vote(it.value, it.key.array) }  //n, IntArray
        return Array(voteList.size) { voteList[it] }
    }

    fun makeVoteList(): List<VoteList> {
        return votes.map { VoteList(it.value, it.key.array.toList()) }
    }
}

// Used to calculate the margin and assertion-specific votes
data class VoteList(val n: Int, val candRanks: List<Int>)
data class VoteSequences(val votes: List<VoteList>) {

    fun nenChoices(winner: Int, loser: Int): Map<Int, Int> {
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


    /** Compute and return the difficulty estimate associated with this assertion. This method
     * computes the minimum tally of the assertion's winner (its first preference tally) and the
     * maximum tally of the assertion's loser, according to the given set of Votes (votes). This
     * maximum tally contains all votes that preference the loser higher than the winner, or on
     * which the loser appears and the winner does not. The given AuditType, audit, defines the
     * chosen method of computing assertion difficulty given these winner and loser tallies.*/
    fun nebChoices(winner: Int, loser: Int): Map<Int, Int> {
        val firstChoices = nenChoices(winner, loser)
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

////////////////////////////////////////////////////////////////////////////////

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