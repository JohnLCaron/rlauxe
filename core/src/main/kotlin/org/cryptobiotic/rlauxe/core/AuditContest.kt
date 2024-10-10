package org.cryptobiotic.rlauxe.core

enum class SocialChoiceFunction{ PLURALITY, APPROVAL, SUPERMAJORITY, IRV }

enum class AuditType { POLLING, CARD_COMPARISON, ONEAUDIT }

data class AuditContest (
    val id: String,
    val idx: Int,
    var candidates: List<Int>,
    val winners: List<Int>,
    val choiceFunction: SocialChoiceFunction = SocialChoiceFunction.PLURALITY, // must agree with assorter
    val minFraction: Double? = null, // supermajority only. TODO: make two AuditContest types?
) {
    val losers = mutableListOf<Int>()
    init {
        require(choiceFunction != SocialChoiceFunction.SUPERMAJORITY || minFraction != null)
        candidates.forEach { cand ->
            if (!winners.contains(cand)) losers.add(cand)
        }
    }
}