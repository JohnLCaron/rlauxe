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
    val choiceFunction: SocialChoiceFunction = SocialChoiceFunction.PLURALITY,
    var candidates: List<String>,
    val ncards: Int,                // maximum number of valid cards
    val winners: List<String>,
    val minFraction: Double? = null, // supermajority
) {
    init {
        require(choiceFunction != SocialChoiceFunction.SUPERMAJORITY || minFraction != null)
    }
    val losers = candidates.filter { !winners.contains(it) }

    init {
        require(winners.isNotEmpty())
        require(losers.isNotEmpty())
    }
}

// TODO make this memory efficient
open class Mvr(
    val id: String,
    val votes: Map<String, Map<String, Int>>, // contest : candidate : vote
) {
    fun hasContest(contest_id: String): Boolean = votes[contest_id] != null

    // Let 1candidate(bi) = 1 if ballot i has a mark for candidate, and 0 if not; SHANGRLA section 2, page 4
    fun hasMarkFor(contestId: String, candidate: String): Int {
        val contestVotes = votes[contestId]
        return if (contestVotes == null) 0 else contestVotes[candidate] ?: 0
    }

    // Is there exactly one vote among the candidates in the contest `contest_id`?
    fun hasOneVote(contest_id: String, candidates: List<String>): Boolean {
        val contestVotes = this.votes[contest_id] ?: return false
        val totalVotes = candidates.map{ contestVotes[it] ?: 0 }.sum() // assumes >= 0
        return (totalVotes == 1)
    }
}

class Cvr(
    id: String,
    votes: Map<String, Map<String, Int>>, // contest : candidate : vote
    val phantom: Boolean = false
): Mvr(id, votes)


