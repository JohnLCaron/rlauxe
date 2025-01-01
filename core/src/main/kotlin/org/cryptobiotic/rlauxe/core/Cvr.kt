package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.workflow.BallotOrCard

// immutable
data class Cvr(
    val id: String,
    val votes: Map<Int, IntArray>, // contest : list of candidates voted for; for IRV, ranked hi to lo
    val phantom: Boolean = false,
) {
    fun hasContest(contestId: Int): Boolean = votes[contestId] != null

    constructor(oldCvr: Cvr, votes: Map<Int, IntArray>) : this(oldCvr.id, votes, oldCvr.phantom)
    constructor(contest: Int, ranks: List<Int>): this( "testing", mapOf(contest to ranks.toIntArray())) // for quick testing
    constructor(contest: Int, id: String, ranks: List<Int>, phantom: Boolean): this( id, mapOf(contest to ranks.toIntArray()), phantom) // for quick testing

    // Let 1candidate(bi) = 1 if ballot i has a mark for candidate, and 0 if not; SHANGRLA section 2, page 4
    fun hasMarkFor(contestId: Int, candidateId: Int): Int {
        val contestVotes = votes[contestId]
        return if (contestVotes == null) 0
               else if (contestVotes.contains(candidateId)) 1 else 0
    }

    // Is there exactly one vote in the contest among the given candidates?
    fun hasOneVote(contestId: Int, candidates: List<Int>): Boolean {
        val contestVotes = this.votes[contestId] ?: return false
        val totalVotes = contestVotes.filter{ candidates.contains(it) }.count()
        return (totalVotes == 1)
    }

    override fun toString() = buildString {
        append("$id ($phantom)")
        votes.forEach { (key, value) -> append(" $key: ${value.contentToString()}")}
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Cvr

        if (phantom != other.phantom) return false
        if (id != other.id) return false
        for ((contestId, candidates) in votes) {
            if (!candidates.contentEquals(other.votes[contestId])) return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = phantom.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + votes.hashCode()
        return result
    }

    companion object {
        fun makePhantom(cvrId: String, contestId: Int) = Cvr(cvrId, mapOf(contestId to IntArray(0)), phantom=true)
    }
}

/** Mutable version of Cvr. sampleNum >= 0  */
data class CvrUnderAudit (val cvr: Cvr, var sampleNum: Long = 0L): BallotOrCard {
    var sampled = false //  # is this CVR in the sample?

    val id = cvr.id
    val votes = cvr.votes
    val phantom = cvr.phantom

    override fun hasContest(contestId: Int) = cvr.hasContest(contestId)
    override fun sampleNumber() = sampleNum
    override fun setIsSampled(isSampled: Boolean) {
        this.sampled = isSampled
    }

    override fun toString() = buildString {
        append("$id ($phantom)")
        votes.forEach { (key, value) -> append(" $key: ${value.contentToString()}")}
    }
}
