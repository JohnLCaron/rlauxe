package org.cryptobiotic.rlauxe.raire

import au.org.democracydevelopers.raire.irv.Vote
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import kotlin.collections.getOrPut

private val quiet = true

// recreate raire's elimination paths, so that we can display to the user

// this is called from Viewer in "Show Contest" for IRV
fun showIrvCountResult(result: IrvCountResult, info: ContestInfo) = buildString {
    val multipleRounds = result.ivrRoundsPaths.size > 1

    result.ivrRoundsPaths.forEachIndexed { idx, roundsPath ->
        if (multipleRounds) appendLine("   Alternative IrvCountResult ${'A' + idx} --------------------")

        val rounds = roundsPath.rounds
        append(sfn("round", 30))
        repeat(rounds.size) { append("${nfn(it, 8)} ") }
        appendLine()

        info.candidateNames.forEach { (name, candId) ->
            append(sfn(name, 30))
            rounds.forEachIndexed { idx, round ->
                append("${nfn(round.countFor(candId), 8)} ")
            }
            if (roundsPath.irvWinner.winners.contains(candId)) {
                append(" (winner)")
            }
            appendLine()
        }
    }
}

// one for each round, in order, for one elimination path
data class IrvRoundsPath(val rounds: List<IrvRound>, val irvWinner: IrvWinners)

data class IrvRound(val count: Map<Int, Int>) { // count is candidate -> nvotes for one round
    fun countFor(cand: Int): Int {
        return count.getOrDefault(cand, 0)
    }
    fun convert(candidateIds: List<Int>): IrvRound {
        val mapById = count.map { Pair(candidateIds[it.key], it.value) }.toMap()
        return IrvRound(mapById)
    }
}

// there may be multiple paths through the elimination tree when there are ties
data class IrvCountResult(val ivrRoundsPaths: List<IrvRoundsPath>)

data class IrvWinners(val done:Boolean = false, val winners: Set<Int> = emptySet()) {
    fun convert(candidateIds: List<Int>): IrvWinners {
        val mapById = winners.map { candidateIds[it] }.toSet()
        return IrvWinners(done, mapById)
    }
}

/** The IRV elimination algorithm. */
class IrvCount(val votes: Array<Vote>, val candidates: List<Int>) {
    val rootPath = EliminationPath(1, emptyList(), candidates.toSet(), votes)

    init {
        rootPath.removeZeros()
    }

    fun runRounds(): IrvCountResult {
        var done = false
        while (!done) {
            done = rootPath.nextRoundCount()
        }
        return IrvCountResult(rootPath.paths(emptyList()))
    }
}

class EliminationPath(startingRound: Int, startingElimination: List<Int>, startingViable: Set<Int>, val votes: Array<Vote>) {
    var round = startingRound
    val elimination = MutableList(startingElimination.size) { startingElimination[it] }
    val viable: MutableSet<Int> = startingViable.toMutableSet()
    val rounds = mutableListOf<IrvRound>()

    var candVotes: MutableMap<Int, Int>
    val isRoot = startingElimination.isEmpty()

    var subpaths: List<EliminationPath> = emptyList()
    var roundWinners = IrvWinners()

    init {
        candVotes = countLeadingVote(votes)

        // check if theres 2 or less candidates
        if (viable.size <= 2) {
            this.roundWinners = IrvWinners(true, findWinningCandidates())
        }
    }

    fun name() = if (elimination.isEmpty()) "root" else "elimPath=${elimination}"

    // get first vote that  if for a viable candidate
    fun countLeadingVote(votes: Array<Vote>): MutableMap<Int, Int> {
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
        if (!quiet) {
            println(" ${name()} round $round count: ${sworking}")
            println("   viable: ${viable}")
        }
        rounds.add(IrvRound(sworking))
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

    fun paths(preceeding : List<IrvRound>): List<IrvRoundsPath> {
        if (subpaths.isEmpty()) return listOf(IrvRoundsPath(preceeding + rounds, roundWinners))

        val paths = mutableListOf<IrvRoundsPath>()
        subpaths.forEach { subpath ->
            val subrounds = subpath.paths(preceeding + rounds)
            paths.addAll(subrounds)
        }

        return paths
    }

    // return winning candidate when done
    fun nextRoundCount(): Boolean {
        if (this.roundWinners.done) return true
        round++

        if (subpaths.isEmpty()) {
            removeLeastCandidate()
            return checkForWinner().done
        } else {
            val roundWinners = subpaths.map { subpath ->
                subpath.nextRoundCount()
                subpath.roundWinners
            }
            val done = roundWinners.map { it.done }.reduce { acc: Boolean, isn -> acc && isn }
            if (done) {
                this.roundWinners = IrvWinners(true, emptySet())
            }
            return done
        }
    }

    // return true if theres a tie
    fun removeLeastCandidate(): Boolean {
        if (candVotes.isEmpty())
            println("huh")
        val minValue = candVotes.map { it.value }.min()
        val minKeys = candVotes.filter { it.value == minValue }.keys
        if (minKeys.size == 1) {
            val last = minKeys.first()
            elimination.add(last)
            viable.remove(last)
            candVotes.remove(last)
            candVotes = countLeadingVote(votes)
            return false
        }
        // otherwise create a path for each
        println("*** tie score: $minKeys isRoot=$isRoot")
        if (!isRoot) {
            println("hey two ties")
        }
        subpaths = minKeys.map { EliminationPath(round, elimination.addNew(it), viable.removeNew(it), votes) }
        return true
    }

    fun checkForWinner(): IrvWinners {
        if (subpaths.isEmpty()) {
            val down2two = viable.size <= 2
            if (down2two) {
                this.roundWinners = IrvWinners(true, findWinningCandidates())
            }
            return this.roundWinners

        } else {
            val roundWinners = subpaths.map { subpath ->
                subpath.checkForWinner()
            }
            val done = roundWinners.map { it.done }.reduce { acc: Boolean, isn -> acc && isn }
            if (done) {
                this.roundWinners = IrvWinners(true, emptySet())
            }
            return this.roundWinners
        }
    }

    fun findWinningCandidates(): Set<Int> {
        val maxValue = candVotes.map { it.value }.max()
        return candVotes.filter { it.value == maxValue }.keys
    }
}

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

