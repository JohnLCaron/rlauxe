package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestIF
import org.cryptobiotic.rlauxe.core.PluralityAssorter
import org.cryptobiotic.rlauxe.util.VotesAndUndervotes
import org.cryptobiotic.rlauxe.util.margin2mean

data class BallotPool(
    val name: String,
    val poolId: Int,
    val contest:Int,
    val ncards: Int,          // ncards for this contest in this pool; TODO hasStyles = false?
    val votes: Map<Int, Int>, // candid -> nvotes, for plurality. umm do we really need ?
) {

    // TODO does this really agree with the average assorter?
    // this could go from -1 to 1. TODO shouldnt that be -u to u ??
    fun calcReportedMargin(winner: Int, loser: Int): Double {
        if (ncards == 0) return 0.0
        val winnerVote = votes[winner] ?: 0
        val loserVote = votes[loser] ?: 0
        return (winnerVote - loserVote) / ncards.toDouble()
    }

    fun votesAndUndervotes(voteForN: Int, ncandidates: Int): Map<Int, Int> {
        val poolVotes = votes.values.sum()
        val poolUndervotes = ncards * voteForN - poolVotes
        return (votes.map { Pair(it.key, it.value)} + Pair(ncandidates, poolUndervotes)).toMap()
    }

    fun votesAndUndervotes(voteForN: Int): VotesAndUndervotes {
        val poolUndervotes = ncards * voteForN - votes.values.sum()
        return VotesAndUndervotes(votes, poolUndervotes, voteForN)
    }

    fun reportedAverage(winner: Int, loser: Int): Double {
        val winnerVotes = votes[winner] ?: 0
        val loserVotes = votes[loser] ?: 0
        val reportedMargin = (winnerVotes - loserVotes) / ncards.toDouble() // TODO dont know Nc
        return margin2mean(reportedMargin)
    }
}

data class AssortAvgsInPools (
    val contest:Int,
    val assortAverage: Map<Int, Double>, // poolId -> average assort value
)