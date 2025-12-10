package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardIF
import org.cryptobiotic.rlauxe.oneaudit.unpooled

// core abstraction for both CVR and MVR
// assumes that a vote is 0 or 1.
data class Cvr(
    val id: String, // ballot identifier
    val votes: Map<Int, IntArray>, // contest -> list of candidates voted for; for IRV, ranked first to last
    val phantom: Boolean = false,
    val poolId: Int? = null,  // or cardStyle.id
): CardIF {
    init {
        require(id.indexOf(',') < 0) { "cvr.id='$id' must not have commas"} // must not have nasty commas
    }

    override fun isPhantom() = phantom
    override fun poolId() = poolId
    override fun location() = id

    override fun hasContest(contestId: Int): Boolean = votes[contestId] != null
    override fun votes(contestId: Int): IntArray? = votes[contestId]
    override fun rankedChoices(contestId: Int): IntArray? = votes[contestId]

    fun contests() = votes.keys.toList().sorted().toIntArray()

    constructor(oldCvr: Cvr, votes: Map<Int, IntArray>) : this(oldCvr.id, votes, oldCvr.phantom)
    constructor(contest: Int, ranks: List<Int>): this( "testing", mapOf(contest to ranks.toIntArray())) // for quick testing

    // Let 1candidate(bi) = 1 if ballot i has a mark for candidate, and 0 if not; SHANGRLA section 2, page 4
    override fun hasMarkFor(contestId: Int, candidateId: Int): Int {
        val contestVotes = votes[contestId]
        return if (contestVotes == null) 0
               else if (contestVotes.contains(candidateId)) 1 else 0
    }

    // Is there exactly one vote in the contest among the given candidates?
    override fun hasOneVoteFor(contestId: Int, candidates: List<Int>): Boolean {
        val contestVotes = this.votes[contestId] ?: return false
        val totalVotes = contestVotes.count { candidates.contains(it) }
        return (totalVotes == 1)
    }

    override fun toString() = buildString {
        append("$id ($phantom) ")
        if (poolId != null) append(" poolId=$poolId: ")
        votes.forEach { (key, value) -> append(" $key: ${value.contentToString()}")}
    }

    //// overriding because of IntArray ??
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Cvr

        if (phantom != other.phantom) return false
        if (id != other.id) return false
        if (votes.size != other.votes.size) return false
        for ((contestId, candidates) in votes) {
            if (!candidates.contentEquals(other.votes[contestId])) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = phantom.hashCode()
        result = 31 * result + id.hashCode()
        votes.forEach { (contestId, candidates) -> result = 31 * result + contestId.hashCode() + candidates.contentHashCode() }
        return result
    }
}

// TODO somewhere else ? cases/src/main/kotlin/org/cryptobiotic/rlauxe/dominion/ ??
// intermediate CVR representation for DominionCvrSummary
data class CvrExport(val id: String, val group: Int, val votes: Map<Int, IntArray>) {
    // constructor(cvr: Cvr) : this(cvr.id, 0, cvr.votes) // TODO the group id is lost when converting to Cvr and back

    // Calculate the pool name from the cvr id. Could pass in a function (CvrExport) -> pool name
    fun poolKey(): String {
        if (group == 2) return unpooled
        val lastIdx = id.lastIndexOf('-')
        return id.substring(0, lastIdx)
    }

    fun toAuditableCard(index: Int, prn: Long, phantom: Boolean = false, pools: Map<String, Int>? = null, showPoolVotes: Boolean = true): AuditableCard {
        val contests = votes.map { it.key }.toIntArray()
        val poolId = if (pools == null || group != 1) null else pools[ poolKey() ]  // TODO not general
        // TODO if you want to delete the votes, add an adapter
        val useVotes = if (poolId == null || showPoolVotes) votes else null
        return AuditableCard(id, index, prn, phantom, contests, useVotes, poolId)
    }

    fun toCvr(phantom: Boolean = false, pools: Map<String, Int>? = null) : Cvr {
        val poolId = if (pools == null || group != 1) null else pools[ poolKey() ] // TODO not general
        return Cvr(id, votes, phantom, poolId)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CvrExport

        if (group != other.group) return false
        if (id != other.id) return false
        if (votes.size != other.votes.size) return false
        for ((contestId, candidates) in votes) {
            if (!candidates.contentEquals(other.votes[contestId])) return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = group
        result = 31 * result + id.hashCode()
        votes.forEach { (contestId, candidates) -> result = 31 * result + contestId.hashCode() + candidates.contentHashCode() }
        return result
    }

}
