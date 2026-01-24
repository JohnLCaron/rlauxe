package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardManifest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.raire.VoteConsolidator
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.collections.toMap

interface ContestVotesIF {
    val contestId: Int
    val voteForN: Int
    val votes: Map<Int, Int> // IRV have empty votes
    fun ncards(): Int // including undervotes
    fun undervotes(): Int // including undervotes
}

data class ContestVotes(
    override val contestId: Int,
    override val voteForN: Int,
    override val votes: Map<Int, Int>,
    val ncards: Int,
    val undervotes: Int
): ContestVotesIF {
    override fun ncards() = ncards
    override fun undervotes() = undervotes
}

// tabulate contest votes from cards or cvrs; can handle both regular and irv voting
class ContestTabulation(
    override val contestId: Int,
    override val voteForN: Int,
    val isIrv: Boolean,
    val candidateIds: List<Int>
): ContestVotesIF {

    constructor(info: ContestInfo) : this(info.id, info.voteForN, info.isIrv, info.candidateIds)
    constructor(other: ContestTabulation) : this(other.contestId, other.voteForN, other.isIrv, other.candidateIds)

    val candidateIdToIdx by lazy { candidateIds.mapIndexed { idx, id -> Pair(id, idx) }.toMap() }

    override val votes = mutableMapOf<Int, Int>() // cand -> votes
    val irvVotes = VoteConsolidator() // candidate indexes
    val notfound = mutableMapOf<Int, Int>() // candidate -> nvotes; track candidates on the cvr but not in the contestInfo, for debugging

    var ncards = 0 // TODO should be "how many cards are in the population"?
    var novote = 0  // how many cards had no vote for this contest?
    var undervotes = 0  // how many undervotes = voteForN - nvotes
    var overvotes = 0  // how many overvotes = (voteForN < cands.size)
    var nphantoms = 0  // how many overvotes = (voteForN < cands.size)

    constructor(info: ContestInfo, votes: Map<Int, Int>, ncards: Int): this(info) {
        votes.forEach{ this.addVote(it.key, it.value) }
        this.ncards = ncards
    }

    override fun ncards() = ncards
    override fun undervotes() = undervotes

    fun addVotes(cands: IntArray, phantom:Boolean) {
        if (!isIrv) addVotesReg(cands) else addVotesIrv(cands)
        if (phantom) nphantoms++
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
            if (candidateIdToIdx[it] == null) {
                notfound[it] = notfound.getOrDefault(it, 0) + 1
            }
        }
        // convert to index for Raire
        val mappedVotes = candidateRanks.map { candidateIdToIdx[it] }
        if (mappedVotes.isNotEmpty()) irvVotes.addVote(mappedVotes.filterNotNull().toIntArray())
        ncards++
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
        this.ncards += other.ncards
        this.novote += other.novote
        this.undervotes += other.undervotes
        this.overvotes += other.overvotes
    }

    fun votesAndUndervotes(poolId: Int): Vunder {
        return if (!isIrv) Vunder.fromNpop(contestId, undervotes, ncards(), votes, voteForN) else {
            val missing = ncards() - undervotes - irvVotes.nvotes()
            val voteCounts = irvVotes.votes.map { (hIntArray, count) ->
                // convert indices back to ids
                val idArray: List<Int> = hIntArray.array.map { candidateIds[it] }
                Pair(idArray.toIntArray(), count)
            }
            Vunder(contestId, poolId, voteCounts, undervotes, missing, 1)
        }
    }

    fun nvotes() = votes.map { it.value}.sum()

    override fun toString(): String {
        val sortedVotes = votes.entries.sortedBy { it.key }
        return "ContestTabulation(id=${contestId} isIrv=$isIrv, voteForN=$voteForN, votes=$sortedVotes, nvotes=${nvotes()} ncards=$ncards, undervotes=$undervotes, novote=$novote, overvotes=$overvotes)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContestTabulation) return false

        if (contestId != other.contestId) return false
        if (voteForN != other.voteForN) return false
        if (isIrv != other.isIrv) return false
        if (ncards != other.ncards) return false
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
        result = 31 * result + ncards
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

// TODO only accumulates regular votes, not IRV
fun tabulateOneAuditPools(cardPools: List<OneAuditPoolIF>, infos: Map<Int, ContestInfo>): Map<Int, ContestTabulation> {
    val poolSums = infos.mapValues { ContestTabulation(it.value) }
    cardPools.forEach { cardPool ->
        cardPool.regVotes().forEach { (contestId, regVotes: ContestVotesIF) ->
            val poolSum = poolSums[contestId]
            if (poolSum != null) {
                regVotes.votes.forEach { (candId, nvotes) -> poolSum.addVote(candId, nvotes) }
                poolSum.ncards += regVotes.ncards()
                poolSum.undervotes += regVotes.undervotes()
            }
        }
    }
    return poolSums
}

// TODO only accumulates regular votes, not IRV
fun tabulateCardManifest(cardManifest: CardManifest, infos: Map<Int, ContestInfo>): Map<Int, ContestTabulation> {
    val poolSums = infos.mapValues { ContestTabulation(it.value) }
    cardManifest.populations.forEach {
        val cardPool = it as OneAuditPoolIF
        cardPool.regVotes().forEach { (contestId, regVotes: ContestVotesIF) ->
            val poolSum = poolSums[contestId]
            if (poolSum != null) {
                regVotes.votes.forEach { (candId, nvotes) -> poolSum.addVote(candId, nvotes) }
                poolSum.ncards += regVotes.ncards()
                poolSum.undervotes += regVotes.undervotes()
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
                        tab.ncards++
                    }
                }
            }
        }
    }
    return tabs
}

fun showTabs(what: String, tabs: Map<Int, ContestTabulation>) = buildString {
    appendLine(what)
    tabs.forEach { (id, tab) ->
        appendLine(" $tab")
    }
    appendLine()
}