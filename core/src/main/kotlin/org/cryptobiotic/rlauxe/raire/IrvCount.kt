package org.cryptobiotic.rlauxe.raire

import au.org.democracydevelopers.raire.irv.Vote
import kotlin.collections.getOrPut

/** The IRV elimination algorithm. */
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

