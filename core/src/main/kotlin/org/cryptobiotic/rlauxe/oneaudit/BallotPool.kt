package org.cryptobiotic.rlauxe.oneaudit

data class BallotPool(
    val name: String,
    val id: Int,
    val contest:Int,
    val ncards: Int,
    val votes: Map<Int, Int>, // candid-> nvotes
) {

    fun calcReportedMargin(winner: Int, loser: Int): Double {
        if (ncards == 0) return 0.0
        val winnerVote = votes[winner] ?: 0
        val loserVote = votes[loser] ?: 0
        return (winnerVote - loserVote) / ncards.toDouble()
    }

}