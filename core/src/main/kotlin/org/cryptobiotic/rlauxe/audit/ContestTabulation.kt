package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.oneaudit.BallotPool
import org.cryptobiotic.rlauxe.raire.VoteConsolidator
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

private val logger = KotlinLogging.logger("ContestTabulation")

// we need both the vote count and the ncards, to calculate margins; cant be used for IRV
interface RegVotes {
    val votes: Map<Int, Int>
    fun ncards(): Int // including undervotes
}

data class RegVotesImpl(override val votes: Map<Int, Int>, val ncards: Int): RegVotes {
    override fun ncards() = ncards
}

// tabulate contest votes from CVRS; can handle both regular and irv voting
class ContestTabulation(val info: ContestInfo): RegVotes {
    val isIrv = info.isIrv
    val voteForN = if (isIrv) 1 else info.voteForN

    override val votes = mutableMapOf<Int, Int>() // cand -> votes
    val irvVotes = VoteConsolidator() // candidate indexes
    val notfound = mutableMapOf<Int, Int>() // candidate -> nvotes; track candidates on the cvr but not in the contestInfo, for debugging
    val candidateIdToIndex: Map<Int, Int>

    var ncards = 0
    var novote = 0  // how many cards had no vote for this contest?
    var undervotes = 0  // how many undervotes = voteForN - nvotes
    var overvotes = 0  // how many overvotes = (voteForN < cands.size)

    init {
        // The candidate Ids must go From 0 ... ncandidates-1, for Raire; use the ordering from ContestInfo.candidateIds
        candidateIdToIndex = if (isIrv) info.candidateIds.mapIndexed { idx, candidateId -> Pair(candidateId, idx) }.toMap() else emptyMap()
    }

    override fun ncards() = ncards

    fun addVotes(cands: IntArray) {
        if (!isIrv) addVotesReg(cands) else addVotesIrv(cands)
    }

    fun addVotesReg(cands: IntArray) {
        cands.forEach { addVote(it, 1) }

        ncards++
        if (voteForN < cands.size) overvotes++
        undervotes += (voteForN - cands.size)
        if (cands.isEmpty()) novote++
    }

    fun addVote(cand: Int, vote: Int) {
        val accum = votes.getOrPut(cand) { 0 }
        votes[cand] = accum + vote
    }

    fun addVotesIrv(candidateRanks: IntArray) {
        candidateRanks.forEach {  // track for non IRV also?
            if (candidateIdToIndex[it] == null) {
                notfound[it] = notfound.getOrDefault(it, 0) + 1
            }
        }
        // convert to index for Raire
        val mappedVotes = candidateRanks.map { candidateIdToIndex[it] }
        if (mappedVotes.isNotEmpty()) irvVotes.addVote(mappedVotes.filterNotNull().toIntArray())
        ncards++
        if (candidateRanks.isEmpty()) novote++
        if (candidateRanks.isEmpty())
            undervotes++
    }

    fun addJustVotes(other: ContestTabulation) {
        require(info.id == other.info.id)
        if (this.isIrv) {
            this.irvVotes.addVotes(other.irvVotes)
        } else {
            other.votes.forEach { (candId, nvotes) -> addVote(candId, nvotes) }
        }
    }

    // for summing multiple tabs into this one
    fun sum(other: ContestTabulation) {
        require (info.id == other.info.id)
        if (this.isIrv) {
            this.irvVotes.addVotes(other.irvVotes)
        } else {
            other.votes.forEach { (candId, nvotes) -> addVote(candId, nvotes) }
        }
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

    fun toString2() = buildString {
        // append("${votes.toList().sortedBy{ it.second }.reversed().toMap()} ncards=$ncards undervotes=$undervotes novote=$novote")
        append("contest ${info.id} ${votes.toSortedMap()} nvotes=${nvotes()} ncards=$ncards undervotes=$undervotes overvotes=$overvotes novote=$novote")
        if (voteForN != null) {
            val nvotes = votes.map { it.value }.sum()
            val underPct = (100.0 * undervotes / (nvotes + undervotes)).toInt()
            append(" underPct= $underPct%")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContestTabulation

        if (isIrv != other.isIrv) return false
        if (voteForN != other.voteForN) return false
        if (ncards != other.ncards) return false
        if (info != other.info) return false
        if (votes != other.votes) return false
        if (irvVotes != other.irvVotes) return false
        if (candidateIdToIndex != other.candidateIdToIndex) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isIrv.hashCode()
        result = 31 * result + voteForN
        result = 31 * result + ncards
        result = 31 * result + info.hashCode()
        result = 31 * result + votes.hashCode()
        result = 31 * result + irvVotes.hashCode()
        result = 31 * result + candidateIdToIndex.hashCode()
        return result
    }

    override fun toString(): String {
        return "ContestTabulation(isIrv=$isIrv, voteForN=$voteForN, votes=$votes, ncards=$ncards, novote=$novote, undervotes=$undervotes, overvotes=$overvotes)"
    }
}

// add other into this
fun MutableMap<Int, ContestTabulation>.sumContestTabulations(other: Map<Int, ContestTabulation>) {
    other.forEach { (contestId, otherTab) ->
        val contestSum = this.getOrPut(contestId) { ContestTabulation(otherTab.info) }
        contestSum.sum(otherTab)
    }
}

// add other into this TODO needed?
fun MutableMap<Int, ContestTabulation>.addJustVotes(other: Map<Int, ContestTabulation>) {
    other.forEach { (contestId, otherTab) ->
        val contestSum = this.getOrPut(contestId) { ContestTabulation(otherTab.info) }
        contestSum.addJustVotes(otherTab)
    }
}

// return contestId -> ContestTabulation
fun tabulateBallotPools(ballotPools: Iterator<BallotPool>, infos: Map<Int, ContestInfo>): Map<Int, ContestTabulation> {
    val votes = mutableMapOf<Int, ContestTabulation>()
    ballotPools.forEach { pool ->
        val info = infos[pool.contestId]
        if (info != null) {
            val tab = votes.getOrPut(pool.contestId) { ContestTabulation(infos[pool.contestId]!!) }
            pool.votes.forEach { (cand, vote) -> tab.addVote(cand, vote) }
        }
    }
    return votes
}

// return contestId -> ContestTabulation
fun tabulateCvrs(cvrs: Iterator<Cvr>, infos: Map<Int, ContestInfo>): Map<Int, ContestTabulation> {
    val votes = mutableMapOf<Int, ContestTabulation>()
    for (cvr in cvrs) {
        for ((contestId, conVotes) in cvr.votes) {
            val info = infos[contestId]
            if (info != null) {
                val tab = votes.getOrPut(contestId) { ContestTabulation(info) }
                tab.addVotes(conVotes)
            }
        }
    }
    return votes
}