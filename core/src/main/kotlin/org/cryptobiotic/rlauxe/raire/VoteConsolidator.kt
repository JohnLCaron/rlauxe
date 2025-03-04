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

//////////////////////////////////////////////////////////////////////////////////
/** Check the IRV elimination algorithm. */
class IrvCount(val votes: Array<Vote>, val candidates: List<Int>) {
    var round = 1
    val rootPath = EliminationPath(round, emptyList(), candidates.toSet(), votes)

    // return winner?
    fun nextRoundCount(): RoundWinner {
        round++
        return rootPath.nextRoundCount()
    }
}

class EliminationPath(startingRound: Int, startingElimination: List<Int>, startingViable: Set<Int>, val votes: Array<Vote>) {
    var round = startingRound
    val elimination = MutableList(startingElimination.size) { startingElimination[it] }
    val viable: MutableSet<Int> = HashSet(startingViable)
    var candVotes = count(votes)
    val isRoot = startingElimination.isEmpty()

    var subpaths: List<EliminationPath> = emptyList()
    var roundWinner = RoundWinner()

    fun name() = if (elimination.isEmpty()) "root" else "elimPath=${elimination}"

    fun count(votes: Array<Vote>): MutableMap<Int, Int> {
        val working = mutableMapOf<Int, Int>()
        votes.forEach { vote ->
            for (cand in vote.prefs) {
                if (viable.contains(cand)) {
                    working[cand] = working.getOrPut(cand) { 0 } + vote.n
                    break
                }
            }
        }
        println(" ${name()} round $round count: ${working}")
        return working
    }

    fun findWinningCandidates(): Set<Int> {
        val maxValue = candVotes.map { it.value }.max()
        return candVotes.filter { it.value == maxValue }.keys
    }

    // return true if theres a tie
    fun removeLeastCandidate(): Boolean {
        val minValue = candVotes.map { it.value }.min()
        val minKeys = candVotes.filter { it.value == minValue }.keys
        if (minKeys.size == 1) {
            val last = minKeys.first()
            elimination.add(last)
            viable.remove(last)
            candVotes.remove(last)
            candVotes = count(votes)
            return false
        }
        // otherwise create a path for each
        println("*** tie score: $minKeys isRoot=$isRoot")
        subpaths = minKeys.map { EliminationPath(round, elimination.addNew(it), viable.removeNew(it), votes) }
        return true
    }

    // return winning candidate when done
    fun nextRoundCount(): RoundWinner {
        if (this.roundWinner.done) return this.roundWinner
        round++

        if (subpaths.isEmpty()) {
            removeLeastCandidate()
            return checkForWinner()
        } else {
            val roundWinners = subpaths.map { subpath ->
                subpath.nextRoundCount()
            }
            val done = roundWinners.map { it.done }.reduce { acc: Boolean, isn -> acc && isn }
            if (done) {
                val winnerSet = mutableSetOf<Int>()
                roundWinners.forEach { winnerSet.addAll(it.winners) }
                return RoundWinner(true, winnerSet)
            }
            return RoundWinner()
        }
    }

    fun checkForWinner(): RoundWinner {
        if (subpaths.isEmpty()) {
            val down2two = viable.size == 2
            if (down2two) {
                this.roundWinner = RoundWinner(true, findWinningCandidates())
            }
            return this.roundWinner

        } else {
            val roundWinners = subpaths.map { subpath ->
                subpath.checkForWinner()
            }
            val done = roundWinners.map { it.done }.reduce { acc: Boolean, isn -> acc && isn }
            if (done) {
                val winnerSet = mutableSetOf<Int>()
                roundWinners.forEach { winnerSet.addAll(it.winners) }
                this.roundWinner = RoundWinner(true, winnerSet)
            }
            return this.roundWinner
        }
    }
}

data class RoundWinner(val done:Boolean = false, val winners: Set<Int> = emptySet())

fun List<Int>.addNew( eliminate: Int): List<Int> {
    val result = mutableListOf<Int>()
    result.addAll(this)
    result.add(eliminate)
    return result
}

fun Set<Int>.removeNew( eliminate: Int): Set<Int> {
    val result = mutableSetOf<Int>()
    result.addAll(this)
    result.remove(eliminate)
    return result
}

