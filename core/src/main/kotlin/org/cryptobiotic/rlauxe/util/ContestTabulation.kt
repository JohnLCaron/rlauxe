package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.oneaudit.Vunder
import org.cryptobiotic.rlauxe.raire.VoteConsolidator
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.collections.toMap

// tabulate contest votes from cards or cvrs; can handle both regular and irv voting
class ContestTabulation(
    val contestId: Int,
    voteForNin: Int, //
    val isIrv: Boolean,
    val candidateIds: List<Int>
) {
    constructor(info: ContestInfo) : this(info.id, info.voteForN, info.isIrv, info.candidateIds)
    constructor(other: ContestTabulation) : this(other.contestId, other.voteForN, other.isIrv, other.candidateIds)

    val voteForN = if (isIrv) 1 else voteForNin
    val candidateIdToIdx by lazy { candidateIds.mapIndexed { idx, id -> Pair(id, idx) }.toMap() }

    val votes = mutableMapOf<Int, Int>() // cand -> votes
    val irvVotes = VoteConsolidator() // candidate indexes
    val notfound = mutableMapOf<Int, Int>() // candidate -> nvotes; track candidates on the cvr but not in the contestInfo, for debugging

    var ncardsTabulated = 0 // total cards added to the tabulation
    var novote = 0  // how many cards had no vote for this contest?
    var undervotes = 0  // how many undervotes = voteForN - nvotes
    var overvotes = 0  // how many overvotes = (voteForN < cands.size)
    var nphantoms = 0  // how many phantoms

    constructor(info: ContestInfo, votes: Map<Int, Int>, ncards: Int): this(info) {
        votes.forEach{ this.addVote(it.key, it.value) }
        this.ncardsTabulated = ncards
    }

    fun ncards() = ncardsTabulated
    fun undervotes() = undervotes

    fun nvotes() = if (isIrv) irvVotes.nvotes() else votes.map { it.value }.sum()
    fun missing() = voteForN * ncards() - nvotes()

    fun addVotes(cands: IntArray, phantom:Boolean) {
        ncardsTabulated++

        if (phantom) {
            nphantoms++
        } else {
            if (!isIrv) addVotesReg(cands) else addVotesIrv(cands)
        }
    }

    private fun addVotesReg(cands: IntArray) {
        cands.forEach { addVote(it, 1) }
        if (voteForN < cands.size) overvotes++
        undervotes += (voteForN - cands.size)
        if (cands.isEmpty()) novote++
    }

    fun addVote(cand: Int, vote: Int) {
        val accum = votes.getOrPut(cand) { 0 }
        votes[cand] = accum + vote
    }

    private fun addVotesIrv(candidateRanks: IntArray) {
        candidateRanks.forEach {  // track for non IRV also?
            if (candidateIdToIdx[it] == null) {
                notfound[it] = notfound.getOrDefault(it, 0) + 1
            }
        }
        // convert to index for Raire
        val mappedVotes = candidateRanks.map { candidateIdToIdx[it] }
        if (mappedVotes.isNotEmpty()) irvVotes.addVote(mappedVotes.filterNotNull().toIntArray())
        if (candidateRanks.isEmpty()) novote++
        if (candidateRanks.isEmpty()) undervotes++
    }

    // for summing multiple tabs into this one
    fun sum(other: ContestTabulation) {
        require (contestId == other.contestId)
        if (this.isIrv) {
            this.irvVotes.addVotes(other.irvVotes)
        } else {
            other.votes.forEach { (candId, nvotes) -> addVote(candId, nvotes) }
        }
        this.ncardsTabulated += other.ncardsTabulated
        this.novote += other.novote
        this.undervotes += other.undervotes
        this.overvotes += other.overvotes
    }

    fun votesAndUndervotes(poolId: Int, npop: Int, hasSingleCardStyle: Boolean): Vunder {
        if (isIrv) return votesAndUndervotesIrv(poolId, npop, hasSingleCardStyle)

        val voteCounts = votes.map { Pair(intArrayOf(it.key), it.value) }
        val voteSum = votes.values.sum()

        val result = if (hasSingleCardStyle) {
            // if hasSingleCardStyle, then missing has to be zero
            // val missing = npop - (undervotes + contestTab.votes.values.sum()) / contestTab.voteForN
            // 0 = npop - (undervotes + contestTab.votes.values.sum()) / contestTab.voteForN
            val undervotes = npop * voteForN - voteSum
            Vunder(contestId, poolId, voteCounts, undervotes, 0, voteForN)
        } else {
            val missing = npop - (this.undervotes + voteSum) / voteForN
            Vunder(contestId, poolId, voteCounts, this.undervotes, missing, voteForN)
        }

        return result
    }

    fun votesAndUndervotesIrv(poolId: Int, npop: Int, hasSingleCardStyle: Boolean): Vunder {

        val voteCounts = this.irvVotes.votes.map { (hIntArray, count) ->
            // convert indices back to ids
            val idArray: List<Int> = hIntArray.array.map { candidateIds[it] }
            Pair(idArray.toIntArray(), count)
        }

        val result = if (hasSingleCardStyle) {
            // if hasSingleCardStyle, then missing has to be zero
            // val missing = npop - undervotes - irvVotes.nvotes()
            // 0 = npop - undervotes - irvVotes.nvotes()
            val undervotes = npop - irvVotes.nvotes()
            Vunder(contestId, poolId, voteCounts, undervotes, 0, voteForN)
        } else {
            val missing = npop - this.undervotes - this.irvVotes.nvotes()
            Vunder(contestId, poolId, voteCounts, this.undervotes, missing, voteForN)
        }

        return result
    }

    override fun toString(): String {
        val sortedVotes = votes.entries.sortedBy { it.key }
        return "ContestTabulation(id=${contestId} isIrv=$isIrv, voteForN=$voteForN, votes=$sortedVotes, nvotes=${nvotes()} ncards=$ncardsTabulated, undervotes=$undervotes, novote=$novote, overvotes=$overvotes)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContestTabulation) return false

        if (contestId != other.contestId) return false
        if (voteForN != other.voteForN) return false
        if (isIrv != other.isIrv) return false
        if (ncardsTabulated != other.ncardsTabulated) return false
        if (undervotes != other.undervotes) return false
        if (overvotes != other.overvotes) return false
        if (nphantoms != other.nphantoms) return false
        if (isIrv && candidateIds != other.candidateIds) return false
        if (votes != other.votes) return false
        if (irvVotes != other.irvVotes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = contestId
        result = 31 * result + voteForN
        result = 31 * result + isIrv.hashCode()
        result = 31 * result + ncardsTabulated
        result = 31 * result + undervotes
        result = 31 * result + overvotes
        result = 31 * result + nphantoms
        if (isIrv) result = 31 * result + candidateIds.hashCode()
        result = 31 * result + votes.hashCode()
        result = 31 * result + irvVotes.hashCode()
        return result
    }
}

// add other into this
fun MutableMap<Int, ContestTabulation>.sumContestTabulations(other: Map<Int, ContestTabulation>) {
    other.forEach { (contestId, otherTab) ->
        val contestSum = this.getOrPut(contestId) { ContestTabulation(otherTab) }
        contestSum.sum(otherTab)
    }
}

//// also see Vunder

// TODO only accumulates regular votes, not IRV; can we fix that?
fun tabulateOneAuditPools(cardPools: List<OneAuditPoolIF>, infos: Map<Int, ContestInfo>): Map<Int, ContestTabulation> {
    val poolSums = infos.mapValues { ContestTabulation(it.value) }
    cardPools.forEach { cardPool ->
        infos.keys.forEach { contestId ->
            val tab = cardPool.contestTab(contestId)
            if (tab != null) {
                val poolSum = poolSums[contestId]!!
                tab.votes.forEach { (candId, nvotes) -> poolSum.addVote(candId, nvotes) }
                poolSum.ncardsTabulated += tab.ncards()
                poolSum.undervotes += tab.undervotes()
            }
        }
    }
    return poolSums
}

// return contestId -> ContestTabulation
fun tabulateCvrs(cvrs: Iterator<Cvr>, infos: Map<Int, ContestInfo>): Map<Int, ContestTabulation> {
    return tabulateCloseableCvrs(Closer(cvrs), infos)
}

// tabulates both regular and IRV over everything in the cvrs
fun tabulateCloseableCvrs(cvrs: CloseableIterator<Cvr>, infos: Map<Int, ContestInfo>): Map<Int, ContestTabulation> {
    val votes = mutableMapOf<Int, ContestTabulation>()
    cvrs.use { cvrIter ->
        while (cvrIter.hasNext()) {
            val cvr = cvrIter.next()
            for ((contestId, conVotes) in cvr.votes) {
                val info = infos[contestId]
                if (info != null) {
                    val tab = votes.getOrPut(contestId) { ContestTabulation(info) }
                    tab.addVotes(conVotes, cvr.phantom)
                }
            }
        }
    }
    return votes
}

fun tabulateCards(cards: Iterator<AuditableCard>, infos: Map<Int, ContestInfo>): Map<Int, ContestTabulation> {
    return tabulateAuditableCards(Closer(cards), infos)
}

// tabulates both regular and IRV over everything in the cards
fun tabulateAuditableCards(cards: CloseableIterator<AuditableCard>, infos: Map<Int, ContestInfo>): Map<Int, ContestTabulation> {
    val tabs = mutableMapOf<Int, ContestTabulation>()
    cards.use { cardIter ->
        while (cardIter.hasNext()) {
            val card = cardIter.next()
            infos.forEach { (contestId, info) ->
                if (card.hasContest(contestId)) { // TODO note that here, we believe possibleContests ...
                    val tab = tabs.getOrPut(contestId) { ContestTabulation(info) }
                    if (card.phantom) tab.nphantoms++
                    if (card.votes != null && card.votes[contestId] != null) { // happens when cardStyle == all
                        val contestVote = card.votes[contestId]!!
                        tab.addVotes(contestVote, card.phantom)
                    } else {
                        tab.ncardsTabulated++
                    }
                }
            }
        }
    }
    return tabs
}

