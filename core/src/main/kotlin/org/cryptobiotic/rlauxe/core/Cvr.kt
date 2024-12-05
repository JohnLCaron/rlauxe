package org.cryptobiotic.rlauxe.core

interface CvrIF {
    val id: String
    val phantom: Boolean
    val votes: Map<Int, IntArray> // contestId -> list of candidate Ids, ranked when Raire
    fun hasContest(contestId: Int): Boolean
    fun hasMarkFor(contestId: Int, candidateId: Int): Int
    fun hasOneVote(contestId: Int, candidates: List<Int>): Boolean
}

// immutable
data class Cvr(
    override val id: String,
    override val votes: Map<Int, IntArray>, // contest : list of candidates voted for; for IRV, ranked hi to lo
    override val phantom: Boolean = false,
): CvrIF {
    override fun hasContest(contestId: Int): Boolean = votes[contestId] != null

    constructor(oldCvr: CvrIF, votes: Map<Int, IntArray>) : this(oldCvr.id, votes, oldCvr.phantom)
    constructor(contest: Int, ranks: List<Int>): this( "testing", mapOf(contest to ranks.toIntArray())) // for quick testing
    constructor(contest: Int, id: String, ranks: List<Int>, phantom: Boolean): this( id, mapOf(contest to ranks.toIntArray()), phantom) // for quick testing

    // Let 1candidate(bi) = 1 if ballot i has a mark for candidate, and 0 if not; SHANGRLA section 2, page 4
    override fun hasMarkFor(contestId: Int, candidateId: Int): Int {
        val contestVotes = votes[contestId]
        return if (contestVotes == null) 0
               else if (contestVotes.contains(candidateId)) 1 else 0
    }

    // Is there exactly one vote in the contest among the given candidates?
    override fun hasOneVote(contestId: Int, candidates: List<Int>): Boolean {
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
}

/** Mutable version of Cvr. sampleNum >= 0  */
class CvrUnderAudit (val cvr: Cvr, var sampleNum: Long = 0L): CvrIF {
    var sampled = false //  # is this CVR in the sample?

    override val id = cvr.id
    override val votes = cvr.votes
    override val phantom = cvr.phantom
    override fun hasContest(contestId: Int) = cvr.hasContest(contestId)
    override fun hasMarkFor(contestId: Int, candidateId: Int) = cvr.hasMarkFor(contestId, candidateId)
    override fun hasOneVote(contestId: Int, candidates: List<Int>) = cvr.hasOneVote(contestId, candidates)

    // constructor(id: String, contestIdx: Int) : this( Cvr(id, mapOf(contestIdx to IntArray(0)), false))

    override fun toString() = buildString {
        append("$id ($phantom)")
        votes.forEach { (key, value) -> append(" $key: ${value.contentToString()}")}
    }

    companion object {
        fun makePhantom(cvrId: String, contestId: Int) = CvrUnderAudit(Cvr(contestId, cvrId, emptyList(), true))
    }
}
