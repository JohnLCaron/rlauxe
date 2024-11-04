package org.cryptobiotic.rlauxe.core

enum class SocialChoiceFunction { PLURALITY, APPROVAL, SUPERMAJORITY, IRV }

data class Contest(
    val name: String,
    val id: Int,
    var candidateNames: Map<String, Int>, // candidate name -> candidate id
    val winnerNames: List<String>,
    val choiceFunction: SocialChoiceFunction,
    val minFraction: Double? = null, // supermajority only.
) {
    val winners: List<Int>
    val losers: List<Int>
    val candidates: List<Int>

    init {
        require(choiceFunction != SocialChoiceFunction.SUPERMAJORITY || minFraction != null)
        val mwinners = mutableListOf<Int>()
        val mlosers = mutableListOf<Int>()
        candidateNames.forEach { (name, id) ->
            if (winnerNames.contains(name)) mwinners.add(id) else mlosers.add(id)
        }
        winners = mwinners.toList()
        losers = mlosers.toList()
        candidates = winners + losers
    }
}
