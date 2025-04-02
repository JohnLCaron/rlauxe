package org.cryptobiotic.rlauxe.raire

import au.org.democracydevelopers.raire.irv.Vote
import kotlin.collections.getOrPut

data class IrvRound(val count: Map<Int, Int>) {
    fun countFor(cand: Int): Int {
        return count.getOrDefault(cand, 0)
    }
}

/** The IRV elimination algorithm. */
class IrvCount(val votes: Array<Vote>, val candidates: List<Int>) {
    var round = 1
    val rounds = mutableListOf<IrvRound>()
    val rootPath = EliminationPath(round, emptyList(), candidates.toSet(), votes)

    init {
        rounds.add(rootPath.currCount)
        rootPath.removeZeros()
    }

    fun runRounds(): List<IrvRound> {
        var roundWinner = RoundWinner()
        while (!roundWinner.done) {
            roundWinner = nextRoundCount()
        }
        return rounds.toList()
    }

    fun nextRoundCount(): RoundWinner {
        if (rootPath.roundWinner.done) return rootPath.roundWinner

        round++
        val rw =  rootPath.nextRoundCount()
        rounds.add(rootPath.currCount)
        return rw
    }
}

class EliminationPath(startingRound: Int, startingElimination: List<Int>, startingViable: Set<Int>, val votes: Array<Vote>) {
    var round = startingRound
    val elimination = MutableList(startingElimination.size) { startingElimination[it] }
    val viable: MutableSet<Int> = startingViable.toMutableSet()

    var currCount = IrvRound(emptyMap())
    var candVotes = count(votes)
    val isRoot = startingElimination.isEmpty()

    var subpaths: List<EliminationPath> = emptyList()
    var roundWinner = RoundWinner()

    init {
        // immediate check if theres 2 or less candidates
        val down2two = viable.size <= 2
        if (down2two) {
            this.roundWinner = RoundWinner(true, findWinningCandidates())
        }
    }

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
        val sworking = working.toList().sortedBy { (_, v) -> v }.reversed().toMap()
        println(" ${name()} round $round count: ${sworking}")
        println("   viable: ${viable}")
        currCount = IrvRound(sworking)
        return working
    }

    fun removeZeros() {
        val zeros = mutableListOf<Int>()
        viable.forEach { vid ->
            if (candVotes[vid] == null) zeros.add(vid)
        }
        zeros.forEach { viable.remove(it) }
        zeros.forEach { elimination.add(it) }
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

    // return true if theres a tie
    fun removeLeastCandidate(): Boolean {
        if (candVotes.isEmpty())
            print("")
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

    fun findWinningCandidates(): Set<Int> {
        val maxValue = candVotes.map { it.value }.max()
        return candVotes.filter { it.value == maxValue }.keys
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

