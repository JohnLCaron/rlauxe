package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.PopulationIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.roundUp
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.math.max

data class OneAuditPoolFromBallotStyle(
    override val poolName: String,
    override val poolId: Int,
    val hasSingleCardStyle: Boolean,
    val voteTotals: Map<Int, ContestTabulation>, // contestId -> candidateId -> nvotes; must include contests with no votes
    val infos: Map<Int, ContestInfo>, // all infos
): OneAuditPoolIF, PopulationIF {

    val minCardsNeeded = mutableMapOf<Int, Int>() // contestId -> minCardsNeeded
    val maxMinCardsNeeded: Int
    var adjustCards = 0 // TODO simplify relationship with undervotes

    init {
        voteTotals.forEach { (contestId, contestTab) ->
            val voteSum = contestTab.nvotes()
            val info = infos[contestId]!!
            // need at least this many cards would you need for this contest?
            minCardsNeeded[contestId] = roundUp(voteSum.toDouble() / info.voteForN)
        }
        maxMinCardsNeeded = minCardsNeeded.values.max()
    }

    override fun name() = poolName
    override fun id() = poolId
    override fun hasSingleCardStyle() = hasSingleCardStyle

    override fun hasContest(contestId: Int) = voteTotals.contains(contestId)
    override fun possibleContests() = voteTotals.map { it.key }.toSortedSet().toIntArray()

    override fun ncards() = maxMinCardsNeeded + adjustCards

    fun adjustCards(adjust: Int, contestId : Int) {
        if (!hasContest(contestId)) throw RuntimeException("NO CONTEST")
        adjustCards = max( adjust, adjustCards)
    }

    override fun contestTab(contestId: Int) = voteTotals[contestId]

    // undervotes per contest when single BallotStyle, no blanks
    fun undervotes(): Map<Int, Int> {  // contest -> undervote
        val undervote = voteTotals.map { (id, contestTab) ->
            val voteSum = contestTab.nvotes()
            val info = infos[id]!!
            Pair(id, ncards() * info.voteForN - voteSum)
        }
        return undervote.toMap().toSortedMap()
    }

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

        val voteCounts = contestTab.votes.map { Pair(intArrayOf(it.key), it.value) }
        val voteSum = contestTab.votes.values.sum()

        return if (hasSingleCardStyle) {
            // if hasSingleCardStyle, then missing has to be zero
            // val missing = npop - (undervotes + contestTab.votes.values.sum()) / contestTab.voteForN
            // 0 = npop - (undervotes + contestTab.votes.values.sum()) / contestTab.voteForN
            val undervotes = ncards() * contestTab.voteForN - voteSum
            Vunder(contestId, poolId, voteCounts, undervotes, 0, contestTab.voteForN)
        } else {
            val missing = ncards() - (poolUndervotes + voteSum) / contestTab.voteForN
            Vunder(contestId, poolId, voteCounts, poolUndervotes, missing, contestTab.voteForN)
        }
    }

    fun votesAndUndervotesPrev(contestId: Int): Vunder {
        val poolUndervotes = undervoteForContest(contestId)
        val contestTab = voteTotals[contestId]!!
        return Vunder.fromNpop(contestId, poolUndervotes, ncards(), contestTab.votes, contestTab.voteForN)
    }

    override fun toString(): String {
        return "OneAuditPoolWithBallotStyle(poolName='$poolName', poolId=$poolId, voteTotals=$voteTotals, maxMinCardsNeeded=$maxMinCardsNeeded)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OneAuditPoolFromBallotStyle) return false

        if (poolId != other.poolId) return false
        if (hasSingleCardStyle != other.hasSingleCardStyle) return false
        if (maxMinCardsNeeded != other.maxMinCardsNeeded) return false
        if (adjustCards != other.adjustCards) return false
        if (poolName != other.poolName) return false
        if (voteTotals != other.voteTotals) return false
        if (minCardsNeeded != other.minCardsNeeded) return false

        return true
    }

    override fun hashCode(): Int {
        var result = poolId
        result = 31 * result + hasSingleCardStyle.hashCode()
        result = 31 * result + maxMinCardsNeeded
        result = 31 * result + adjustCards
        result = 31 * result + poolName.hashCode()
        result = 31 * result + voteTotals.hashCode()
        result = 31 * result + minCardsNeeded.hashCode()
        return result
    }

    fun toOneAuditPool(): OneAuditPool {
        return OneAuditPool(this.poolName, this.poolId, this.hasSingleCardStyle, this.infos, this.voteTotals, this.ncards())
    }
}
