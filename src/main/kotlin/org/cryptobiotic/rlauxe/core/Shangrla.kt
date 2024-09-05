package org.cryptobiotic.rlauxe.core

enum class SocialChoiceFunction{ PLURALITY, APPROVAL, SUPERMAJORITY, IRV }

enum class AuditType { POLLING, CARD_COMPARISON, ONEAUDIT }

interface AssorterFunction {
    fun assort(mvr: Mvr) : Double
}

interface ComparisonAssorterFunction {
    fun assort(mvr: Mvr, cvr: Cvr) : Double
}

data class AuditContest (
    val id: String,
    val idx: Int,
    var candidates: List<Int>,
    val winners: List<Int>,
    val choiceFunction: SocialChoiceFunction = SocialChoiceFunction.PLURALITY,
    val minFraction: Double? = null, // supermajority
) {
    val losers = mutableListOf<Int>()
    init {
        require(choiceFunction != SocialChoiceFunction.SUPERMAJORITY || minFraction != null)
        candidates.forEach { cand ->
            if (!winners.contains(cand)) losers.add(cand)
        }
        require(winners.isNotEmpty())
        require(losers.isNotEmpty())
    }
   //  val candidatesIdx = List<Int>(candidates.size) { it }
}

open class Mvr(
    val id: String,
    val votes: Map<Int, Map<Int, Int>>, // contest : candidate : vote
) {
    fun hasContest(contestIdx: Int): Boolean = votes[contestIdx] != null

    // Let 1candidate(bi) = 1 if ballot i has a mark for candidate, and 0 if not; SHANGRLA section 2, page 4
    fun hasMarkFor(contestIdx: Int, candidateIdx: Int): Int {
        val contestVotes = votes[contestIdx]
        return if (contestVotes == null) 0 else contestVotes[candidateIdx] ?: 0
    }

    // Is there exactly one vote among the candidates in the contest `contest_id`?
    // note this always looks at all the votes, differencent from SHANGRLA code
    fun hasOneVote(contestIdx: Int): Boolean {
        val contestVotes = this.votes[contestIdx] ?: return false
        val totalVotes = contestVotes.values.sum() // assumes >= 0
        return (totalVotes == 1)
    }

    override fun toString(): String {
        return "$id: $votes"
    }
}

class Cvr(
    id: String,
    votes: Map<Int, Map<Int, Int>>, // contest : candidate : vote
    val phantom: Boolean = false
): Mvr(id, votes)


