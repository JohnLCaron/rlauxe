package org.cryptobiotic.rlauxe.core

// the contest contains the candidate name -> candidate id
// TODO instead of Map<Int, Int>, could just be List<Int>, the list of candidates voted for. vote is always 1
//  or IntArray,

open class Cvr(
    val id: String,
    val votes: Map<Int, IntArray>, // contest : list of candidates voted for
    val phantom: Boolean = false
) {
    fun hasContest(contestId: Int): Boolean = votes[contestId] != null

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

    override fun toString(): String {
        return "$id: $votes $phantom"
    }
}
