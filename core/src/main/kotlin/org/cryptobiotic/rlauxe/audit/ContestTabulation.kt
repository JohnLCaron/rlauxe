package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.raire.VoteConsolidator
import kotlin.collections.component1
import kotlin.collections.component2

private val logger = KotlinLogging.logger("ContestTabulation")

// we need both the vote count and the ncards, to calculate margins; cant be used for IRV
interface RegVotes {
    val votes: Map<Int, Int>
    val ncards: Int // including undervotes
}

data class RegVotesImpl(override val votes: Map<Int, Int>, override val ncards: Int): RegVotes

// tabulate contest votes from CVRS; can handle both regular and irv voting
class ContestTabulation(val info: ContestInfo): RegVotes {
    val isIrv = info.choiceFunction == SocialChoiceFunction.IRV
    val voteForN = if (isIrv) 1 else info.voteForN

    override val votes = mutableMapOf<Int, Int>() // cand -> votes
    val irvVotes = VoteConsolidator() // candidate indexes
    val notfound = mutableMapOf<Int, Int>() // candidate -> nvotes; track candidates on the cvr but not in the contestInfo, for debugging
    val candidateIdToIndex: Map<Int, Int>

    override var ncards = 0
    var novote = 0  // how many cards had no vote for this contest?
    var undervotes = 0  // how many undervotes = voteForN - nvotes
    var overvotes = 0  // how many overvotes = (voteForN < cands.size)

    init {
        // The candidate Ids must go From 0 ... ncandidates-1, for Raire; use the ordering from ContestInfo.candidateIds
        candidateIdToIndex = if (isIrv) info.candidateIds.mapIndexed { idx, candidateId -> Pair(candidateId, idx) }.toMap() else emptyMap()
    }

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
        if (candidateRanks.isEmpty()) undervotes++
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

    override fun toString() = buildString {
        // append("${votes.toList().sortedBy{ it.second }.reversed().toMap()} ncards=$ncards undervotes=$undervotes novote=$novote")
        append("contest ${info.id} ${votes.toSortedMap()} nvotes=${nvotes()} ncards=$ncards undervotes=$undervotes overvotes=$overvotes novote=$novote")
        if (voteForN != null) {
            val nvotes = votes.map { it.value }.sum()
            val underPct = (100.0 * undervotes / (nvotes + undervotes)).toInt()
            append(" underPct= $underPct%")
        }
    }
}