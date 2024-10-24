package org.cryptobiotic.rlauxe.core

// If we knew the contests and candidate indices, we could just use a 2dim array: votes[contest][candidate] : Int
// or we could pass in the Map, but create the index array.
// perhaps the audit contains the string -> index map?

class Cvr(
    val id: String,
    val votes: Map<Int, Map<Int, Int>>, // contest : candidate : vote
    val phantom: Boolean = false
) {
    fun hasContest(contestIdx: Int): Boolean = votes[contestIdx] != null

    // Let 1candidate(bi) = 1 if ballot i has a mark for candidate, and 0 if not; SHANGRLA section 2, page 4
    fun hasMarkFor(contestIdx: Int, candidateIdx: Int): Int {
        val contestVotes = votes[contestIdx]
        return if (contestVotes == null) 0 else contestVotes[candidateIdx] ?: 0
    }

    // Is there exactly one vote in the contest among the given candidates?
    fun hasOneVote(contestIdx: Int, candidates: List<Int>): Boolean {
        val contestVotes = this.votes[contestIdx] ?: return false
        val totalVotes = contestVotes.filter{ candidates.contains(it.key) }.map { it.value }.sum()
        return (totalVotes == 1)
    }

    // Is there exactly one vote in the contest among all the legitimate contests?
    // This excludes candidates with index >= ncandidates, which are mistakes? miscodeing?
    // This assumes candidate indexes are sequential starting at 0.
    // This reproduces behavior in SHANGRLA test_Assertion.
    // This is only used my supermajority assorter.
    fun hasOneVote(contestIdx: Int, ncandidates: Int): Boolean {
        val contestVotes = this.votes[contestIdx] ?: return false
        val totalVotes = contestVotes.filter { it.key < ncandidates }.map { it.value }.sum()
        return (totalVotes == 1)
    }

    override fun toString(): String {
        return "$id: $votes $phantom"
    }
}
