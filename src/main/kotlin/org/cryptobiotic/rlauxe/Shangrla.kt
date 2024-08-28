package org.cryptobiotic.rlauxe

enum class SocialChoiceFunction{ PLURALITY, APPROVAL, SUPERMAJORITY, IRV }

enum class AuditType { POLLING, CARD_COMPARISON, ONEAUDIT }

// TODO really just for Polling, since compare needs assort(cvr: Cvr, mvr: Cvr)
interface AssorterFunction {
    fun assort(cvr: Cvr) : Double
}

data class Assertion(
    val contest: AuditContest,
    val winner: String,
    val loser: String,
) {
    val assorter = makeAssorter()

    fun makeAssorter() : AssorterFunction {
        return when (contest.choiceFunction) {
            SocialChoiceFunction.SUPERMAJORITY -> SupermajorityAssorter(contest, winner, loser)
            SocialChoiceFunction.IRV -> throw RuntimeException("IRV Not supported")
            SocialChoiceFunction.PLURALITY,
            SocialChoiceFunction.APPROVAL -> PluralityAssorter(contest, winner, loser)
        }
    }

    override fun toString() = buildString {
        appendLine("Assertion")
        appendLine("   $contest)")
        appendLine("   assorter=$assorter)")
    }

}

data class Audit(
    val auditType: AuditType,
    val riskLimit: Double = 0.05,
    val contests: List<AuditContest>,
) {
    // TODO put in Contest?
    val assertions = contests.associate { makeAssertions(it) } // map of AuditContest -> List<Assertion>

    fun makeAssertions(contest: AuditContest): Pair<AuditContest, List<Assertion>> {
        val assertions = mutableListOf<Assertion>()
        contest.winners.forEach { winner ->
            contest.losers.forEach { loser ->
                assertions.add(Assertion(contest, winner, loser))
            }
        }
        return Pair(contest, assertions)
    }

}

data class AuditContest (
    val id: String,
    val choiceFunction: SocialChoiceFunction = SocialChoiceFunction.PLURALITY,
    var candidates: List<String>,
    val ncards: Int,                // maximum number of valid cards
    val winners: List<String>,
    val minFraction: Double? = null,
) {
    val losers = candidates.filter { !winners.contains(it) }

    init {
        require(winners.isNotEmpty())
        require(losers.isNotEmpty())
    }
}

data class Cvr(
    val id: String,
    val votes: Map<String, Map<String, Int>>, // contest : candidate : vote
    val phantom: Boolean = false
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