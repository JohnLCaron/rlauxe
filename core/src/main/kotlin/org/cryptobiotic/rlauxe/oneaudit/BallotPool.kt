package org.cryptobiotic.rlauxe.oneaudit

data class BallotPool(
    val name: String,
    val id: Int,
    val contest:Int,
    val ncards: Int,
    val votes: Map<Int, Int>, // candid -> nvotes
) {

    // TODO does this really agree with the average assorter?
    // this could go from -1 to 1. TODO shouldnt that be -u to u ??
    fun calcReportedMargin(winner: Int, loser: Int): Double {
        if (ncards == 0) return 0.0
        val winnerVote = votes[winner] ?: 0
        val loserVote = votes[loser] ?: 0
        return (winnerVote - loserVote) / ncards.toDouble()
    }

}