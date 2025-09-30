package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.oneaudit.BallotPool
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

class ContestTabulationOld(val voteForN: Int?) {
    val votes = mutableMapOf<Int, Int>() // cand -> votes
    var ncards = 0
    var novote = 0  // how many cards had no vote for this contest?
    var undervotes = 0  // how many undervotes = voteForN - nvotes
    var overvotes = 0  // how many overvotes = (voteForN < cands.size)

    fun addVotes(cands: IntArray) {
        cands.forEach { addVote(it, 1) }
        ncards++
        if (voteForN != null) {
            if (voteForN < cands.size) overvotes++
            undervotes += (voteForN - cands.size)
        }
        if (cands.isEmpty()) novote++
    }

    fun addVote(cand: Int, vote: Int) {
        val accum = votes.getOrPut(cand) { 0 }
        votes[cand] = accum + vote
    }

    // for summing multiple tabs together
    fun sum(other: ContestTabulationOld) {
        other.votes.forEach { (candId, nvotes) -> addVote(candId, nvotes) }
        this.ncards += other.ncards
        this.novote += other.novote
        this.undervotes += other.undervotes
        this.overvotes += other.overvotes
    }

    fun undervotePct(): Double {
        val nvotes = votes.map { it.value }.sum()
        return undervotes.toDouble() / (undervotes + nvotes)
    }

    fun nvotes() = votes.map { it.value}.sum()

    override fun toString() = buildString {
        // append("${votes.toList().sortedBy{ it.second }.reversed().toMap()} ncards=$ncards undervotes=$undervotes novote=$novote")
        append("${votes.toSortedMap()} nvotes=${nvotes()} ncards=$ncards undervotes=$undervotes overvotes=$overvotes novote=$novote")
        if (voteForN != null) {
            val nvotes = votes.map { it.value }.sum()
            val underPct = (100.0 * undervotes / (nvotes + undervotes)).toInt()
            append(" underPct= $underPct%")
        }
    }
}

fun MutableMap<Int, ContestTabulationOld>.sumContestTabulations(contestTabulations: Map<Int, ContestTabulationOld>) {
    contestTabulations.forEach { (contestId, contestTab) ->
        val contestSum = this.getOrPut(contestId) { ContestTabulationOld(contestTab.voteForN) }
        contestSum.sum(contestTab)
    }
}

// return contestId -> ContestTabulation
fun tabulateBallotPools(ballotPools: Iterator<BallotPool>, voteForN: Map<Int, Int>): Map<Int, ContestTabulationOld> {
    val votes = mutableMapOf<Int, ContestTabulationOld>()
    ballotPools.forEach { pool ->
        val tab = votes.getOrPut(pool.contestId) { ContestTabulationOld(voteForN[pool.contestId]) }
        pool.votes.forEach { (cand, vote) -> tab.addVote(cand, vote) }
    }
    return votes
}

// return contestId -> ContestTabulation
fun tabulateCvrs(cvrs: Iterator<Cvr>, voteForN: Map<Int, Int>): Map<Int, ContestTabulationOld> {
    val votes = mutableMapOf<Int, ContestTabulationOld>()
    for (cvr in cvrs) {
        for ((contestId, conVotes) in cvr.votes) {
            val tab = votes.getOrPut(contestId) { ContestTabulationOld(voteForN[contestId]) }
            tab.addVotes(conVotes)
        }
    }
    return votes
}