package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.CardPoolIF
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.estimate.Vunder
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.roundUp
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.math.max

// used when you dont have CVRs, just pool totals.
// the ContestTabulations are passed in
data class OneAuditPoolFromBallotStyle(
    override val poolName: String,
    override val poolId: Int,
    val hasExactContests: Boolean,
    val voteTotals: Map<Int, ContestTabulation>, // contestId -> candidateId -> nvotes; must include contests and candidates with no votes
    val infos: Map<Int, ContestInfo>, // all contests
    val ncards: Int? = null
): CardPoolIF, StyleIF {

    val minCardsNeeded = mutableMapOf<Int, Int>() // contestId -> minCardsNeeded
    val maxMinCardsNeeded: Int
    var adjustCards = 0 // adjusted number of cards, using distributeExpectedOvervotes() on one or more contests

    init {
        voteTotals.forEach { (contestId, contestTab) ->
            val voteSum = contestTab.nvotes()
            val info = infos[contestId]!!
            // based on the contest's votes, you need at least this many cards for this contest
            minCardsNeeded[contestId] = roundUp(voteSum.toDouble() / info.voteForN)
        }
        // you need at least this many cards for this pool
        maxMinCardsNeeded = minCardsNeeded.values.max()
    }

    override fun name() = poolName
    override fun id() = poolId
    override fun hasExactContests() = hasExactContests

    override fun hasContest(contestId: Int) = voteTotals.contains(contestId)
    override fun possibleContests() = voteTotals.map { it.key }.toSortedSet().toIntArray()

    override fun ncards() = ncards ?: (maxMinCardsNeeded + adjustCards)

    fun adjustCards(adjust: Int, contestId : Int) {
        if (!hasContest(contestId)) throw RuntimeException("NO CONTEST")
        adjustCards = max( adjust, adjustCards)
    }

    override fun contestTab(contestId: Int) = voteTotals[contestId]

    // TODO move to test
    // undervotes per contest when single BallotStyle, no blanks
    fun undervotesSingleBallotStyle(): Map<Int, Int> {  // contest -> undervote
        val undervote = voteTotals.map { (id, contestTab) ->
            val voteSum = contestTab.nvotes()
            val info = infos[id]!!
            Pair(id, ncards() * info.voteForN - voteSum)
        }
        return undervote.toMap().toSortedMap()
    }

    //        val result = if (hasExactContests) {
    //            // if hasExactContests, then missing has to be zero
    //            // val missing = npop - (undervotes + contestTab.votes.values.sum()) / contestTab.voteForN
    //            // 0 = npop - (undervotes + contestTab.votes.values.sum()) / contestTab.voteForN
    //            val undervotes = npop * voteForN - voteSum
    //            Vunder(contestId, poolId, voteCounts, undervotes, 0, voteForN)
    //        } else {
    //            val missing = npop - (this.undervotes + voteSum) / voteForN
    //            Vunder(contestId, poolId, voteCounts, this.undervotes, missing, voteForN)
    //        }

    // TODO how to distinguish between undervotes and missing ?? You need independent setting for pool ncards
    // this assumes missing = 0; but then should set SingleBallotStyle = true ?
    fun undervoteForContest(contestId: Int): Int {
        val contestTab = voteTotals[contestId] ?: return 0
        val voteSum = contestTab.nvotes()
        val info = infos[contestId]!!
        return ncards() * info.voteForN - voteSum
    }

    // TODO IRV allowed ?
    override fun votesAndUndervotes(contestId: Int): Vunder {
        val poolUndervotes = undervoteForContest(contestId)
        val contestTab = voteTotals[contestId]!!

        // TODO why not use contestTab.votesAndUndervotes() ??

        val voteCounts = contestTab.votes.map { Pair(intArrayOf(it.key), it.value) }
        val voteSum = contestTab.votes.values.sum()

        return if (hasExactContests) {
            // if hasExactContests, then missing has to be zero
            // val missing = npop - (undervotes + contestTab.votes.values.sum()) / contestTab.voteForN
            // 0 = npop - (undervotes + contestTab.votes.values.sum()) / contestTab.voteForN
            val undervotes = ncards() * contestTab.voteForN - voteSum
            Vunder(contestId, poolId, voteCounts, undervotes, 0, contestTab.voteForN)
        } else {
            val missing = ncards() - (poolUndervotes + voteSum) / contestTab.voteForN
            Vunder(contestId, poolId, voteCounts, poolUndervotes, missing, contestTab.voteForN)
        }
    }

    override fun toString(): String {
        return "OneAuditPoolWithBallotStyle(poolName='$poolName', poolId=$poolId, voteTotals=$voteTotals, maxMinCardsNeeded=$maxMinCardsNeeded)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OneAuditPoolFromBallotStyle) return false

        if (poolId != other.poolId) return false
        if (hasExactContests != other.hasExactContests) return false
        if (maxMinCardsNeeded != other.maxMinCardsNeeded) return false
        if (adjustCards != other.adjustCards) return false
        if (poolName != other.poolName) return false
        if (voteTotals != other.voteTotals) return false
        if (minCardsNeeded != other.minCardsNeeded) return false

        return true
    }

    override fun hashCode(): Int {
        var result = poolId
        result = 31 * result + hasExactContests.hashCode()
        result = 31 * result + maxMinCardsNeeded
        result = 31 * result + adjustCards
        result = 31 * result + poolName.hashCode()
        result = 31 * result + voteTotals.hashCode()
        result = 31 * result + minCardsNeeded.hashCode()
        return result
    }

    fun toOneAuditPool(): CardPool {
        return CardPool(this.poolName, this.poolId, this.hasExactContests, this.infos, this.voteTotals, this.ncards())
    }
}
