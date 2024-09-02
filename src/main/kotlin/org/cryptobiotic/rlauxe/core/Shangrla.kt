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
    val choiceFunction: SocialChoiceFunction = SocialChoiceFunction.PLURALITY,
    var candidates: List<String>,
    val ncards: Int,                // maximum number of valid cards
    val winners: List<String>,
    val minFraction: Double? = null, // supermajority
) {
    val winnersIdx : List<Int>
    val losersIdx : List<Int>

    init {
        require(choiceFunction != SocialChoiceFunction.SUPERMAJORITY || minFraction != null)
        val winnersi = mutableListOf<Int>()
        val losersi = mutableListOf<Int>()
        candidates.forEachIndexed { idx, cand ->
            if (winners.contains(cand)) winnersi.add(idx) else losersi.add(idx)
        }
        require(winnersi.isNotEmpty())
        require(losersi.isNotEmpty())

        winnersIdx = winnersi.toList()
        losersIdx = losersi.toList()
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
}

class Cvr(
    id: String,
    votes: Map<Int, Map<Int, Int>>, // contest : candidate : vote
    val phantom: Boolean = false
): Mvr(id, votes)


