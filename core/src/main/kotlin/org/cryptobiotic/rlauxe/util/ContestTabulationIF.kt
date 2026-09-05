package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.estimate.Vunder
import org.cryptobiotic.rlauxe.irv.VoteConsolidator

// perhaps split ContestTabulation and TabulationAccumulation, which allows you to scan cvrs and cards
interface ContestTabulationIF {
    val contestId: Int
    val isIrv: Boolean
    val candidateIds: List<Int>
    val voteForN: Int
    val candidateIdToIdx: Map<Int, Int>
    val votes: MutableMap<Int, Int>  // candidateId -> nvotes
    val irvVotes: VoteConsolidator

    fun ncards(): Int
    fun undervotes(): Int
    fun nvotes(): Int

    // for summing multiple tabs into this one
    fun sum(other: ContestTabulationIF)

    fun votesAndUndervotes(poolId: Int?, npop: Int, hasExactContests: Boolean): Vunder
    fun votesAndUndervotesIrv(poolId: Int?, npop: Int, hasExactContests: Boolean): Vunder
}