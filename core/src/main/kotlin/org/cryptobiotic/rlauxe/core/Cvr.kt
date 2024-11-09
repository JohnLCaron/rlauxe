package org.cryptobiotic.rlauxe.core

// the contest contains the candidate name -> candidate id

interface CvrIF {
    val id: String
    val phantom: Boolean
    val votes: Map<Int, IntArray> // contestId -> list of candidate Ids, ranked when Raire
    fun hasContest(contestId: Int): Boolean
    fun hasMarkFor(contestId: Int, candidateId: Int): Int
    fun hasOneVote(contestId: Int, candidates: List<Int>): Boolean
}

open class Cvr(
    override val id: String,
    override val votes: Map<Int, IntArray>, // contest : list of candidates voted for
): CvrIF {
    override val phantom = false
    override fun hasContest(contestId: Int): Boolean = votes[contestId] != null

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

    override fun toString(): String {
        return "$id: $votes $phantom"
    }
}

// Mutable form of Cvr.
class CvrUnderAudit(val cvr: Cvr, override val phantom: Boolean, var sampleNum: Int = 0): CvrIF {
    override val id = cvr.id
    override val votes = cvr.votes

    var sampled = false //  # is this CVR in the sample?
    var p: Double = 0.0

    override fun hasContest(contestId: Int) = cvr.hasContest(contestId)
    override fun hasMarkFor(contestId: Int, candidateId: Int) = cvr.hasMarkFor(contestId, candidateId)
    override fun hasOneVote(contestId: Int, candidates: List<Int>) = cvr.hasOneVote(contestId, candidates)

    constructor(id: String, contestIdx: Int) : this( Cvr(id, mapOf(contestIdx to IntArray(0))), false)

    companion object {
        fun fromCvrIF(cvr: CvrIF, phantom: Boolean) = if (cvr is CvrUnderAudit) cvr else CvrUnderAudit( cvr as Cvr, phantom)
    }
}
