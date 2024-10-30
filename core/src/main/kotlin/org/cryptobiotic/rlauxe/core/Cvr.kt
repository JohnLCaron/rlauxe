package org.cryptobiotic.rlauxe.core

// the contest contains the candidate name -> candidate id
// TODO instead of Map<Int, Int>, could just be List<Int>, the list of candidates voted for. or IntArray,
//    vote is always 1


open class Cvr(
    val id: String,
    val votes: Map<Int, Map<Int, Int>>, // contest : candidate : vote
    val phantom: Boolean = false
) {
    fun hasContest(contestId: Int): Boolean = votes[contestId] != null

    // Let 1candidate(bi) = 1 if ballot i has a mark for candidate, and 0 if not; SHANGRLA section 2, page 4
    fun hasMarkFor(contestId: Int, candidateId: Int): Int {
        val contestVotes = votes[contestId]
        return if (contestVotes == null) 0 else contestVotes[candidateId] ?: 0
    }

    // Is there exactly one vote in the contest among the given candidates?
    fun hasOneVote(contestId: Int, candidates: List<Int>): Boolean {
        val contestVotes = this.votes[contestId] ?: return false
        val totalVotes = contestVotes.filter{ candidates.contains(it.key) }.map { it.value }.sum()
        return (totalVotes == 1)
    }

    override fun toString(): String {
        return "$id: $votes $phantom"
    }
}
