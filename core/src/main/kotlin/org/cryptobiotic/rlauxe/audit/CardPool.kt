package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.estimate.Vunder
import org.cryptobiotic.rlauxe.util.roundUp
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.set
import kotlin.math.max
import kotlin.text.get
import kotlin.text.toDouble

const val unpooled = "unpooled"

interface CardPoolIF: StyleIF {
    val poolName: String
    val poolId: Int
    fun contestTab(contestId: Int): ContestTabulation?
    fun votesAndUndervotes(contestId: Int): Vunder // throws exception if bad contest id
}

// A card pool could have multiple card styles, but doesnt know what they are.
// Its main feature is that it knows the contest subtotals for the cards it contains.
// So it was mainly developed for OneAudit pools.

// CardPool is immutable
data class CardPool(
    override val poolName: String,
    override val poolId: Int,
    val hasExactContests: Boolean,    // aka single style
    val infos: Map<Int, ContestInfo>, // do we really need this ??
    val contestTabs: Map<Int, ContestTabulation>,  // contestId -> ContestTabulation
    val totalCards: Int,
): CardPoolIF {
    override fun name() = poolName
    override fun id() = poolId
    override fun hasExactContests() = hasExactContests
    override fun ncards() = totalCards

    override fun hasContest(contestId: Int) = contestTabs.contains(contestId)
    override fun possibleContests() = (contestTabs.map { it.key }).toSortedSet().toIntArray()
    override fun contestTab(contestId: Int) = contestTabs[contestId]

    override fun votesAndUndervotes(contestId: Int): Vunder {
        val contestTab = contestTabs[contestId]!!
        return contestTab.votesAndUndervotes(poolId, ncards(), hasExactContests)
    }

    fun addTo(sumTab: MutableMap<Int, ContestTabulation>) {
        this.contestTabs.forEach { (contestId, contestTab) ->
            val info = infos[contestId]
            if (info != null) { // skip IRV
                val contestSumTab = sumTab.getOrPut(contestId) { ContestTabulation(info) }
                contestSumTab.sum(contestTab)
            }
        }
    }

    override fun toString() = buildString {
        append("CardPool(poolName='$poolName', poolId=$poolId, totalCards=$totalCards)")
    }
}

// CardPoolBuilder is mutable; used by Corla CountyPoolsBuilder, BoulderContestBuilder, OneAuditTest
class CardPoolBuilder(
    val poolName: String,
    val poolId: Int,
    val hasExactContests: Boolean,
    val infos: Map<Int, ContestInfo>, // all contests
    val contestTabs: Map<Int, ContestTabulation>, // contestId -> candidateId -> nvotes; must include contests and candidates with no votes
    val minCardsNeeded: Map<Int, Int>
) {
    var ncards: Int? = null // lame
    var adjustCards = 0 // adjusted number of cards, using distributeExpectedOvervotes() on one or more contests

    // you need at least this many cards for this pool
    val maxMinCardsNeeded: Int = minCardsNeeded.values.max()

    fun setNcards(ncards: Int): CardPoolBuilder {
        this.ncards = ncards
        return this
    }

    fun name() = poolName
    fun id() = poolId
    fun hasExactContests() = hasExactContests
    fun ncards(): Int {
        return ncards ?: (maxMinCardsNeeded + adjustCards)
    }

    fun hasContest(contestId: Int) = contestTabs.contains(contestId)
    fun possibleContests() = contestTabs.map { it.key }.toSortedSet().toIntArray()
    fun contestTab(contestId: Int) = contestTabs[contestId]

    // adjustCards becomes the maximum value of adjust. TODO seems lame
    fun adjustCards(adjust: Int, contestId : Int) {
        if (!hasContest(contestId)) throw RuntimeException("NO CONTEST")
        adjustCards = max( adjust, adjustCards)
    }

    // TODO probably need to use this for Corla
    fun votesAndUndervotesCorla(contestId: Int): Vunder {
        val contestTab = contestTabs[contestId]!!
        return contestTab.votesAndUndervotes(poolId, ncards(), hasExactContests)
    }

    // TODO probably need to use this for Boulder
    fun votesAndUndervotesBoulder(contestId: Int): Vunder {
        val poolUndervotes = undervoteForContest(contestId)
        val contestTab = contestTabs[contestId]!!

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

    // TODO how to distinguish between undervotes and missing ?? You need independent setting for pool ncards
    // this assumes missing = 0; but then should set SingleBallotStyle = true ?
    fun undervoteForContest(contestId: Int): Int {
        val contestTab = contestTabs[contestId] ?: return 0
        val voteSum = contestTab.nvotes()
        val info = infos[contestId]!!
        return ncards() * info.voteForN - voteSum
    }

    override fun toString(): String {
        return "AdjustablePoolBuilder(poolName='$poolName', poolId=$poolId, #contests=${contestTabs.size}, maxMinCardsNeeded=$maxMinCardsNeeded)"
    }

    fun build(): CardPool {
        return CardPool(this.poolName, this.poolId, this.hasExactContests, this.infos, this.contestTabs, this.ncards())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CardPoolBuilder) return false

        if (poolId != other.poolId) return false
        if (hasExactContests != other.hasExactContests) return false
        if (ncards != other.ncards) return false
        if (maxMinCardsNeeded != other.maxMinCardsNeeded) return false
        if (adjustCards != other.adjustCards) return false
        if (poolName != other.poolName) return false
        if (infos != other.infos) return false
        if (contestTabs != other.contestTabs) return false
        if (minCardsNeeded != other.minCardsNeeded) return false

        return true
    }

    override fun hashCode(): Int {
        var result = poolId
        result = 31 * result + hasExactContests.hashCode()
        result = 31 * result + (ncards ?: 0)
        result = 31 * result + maxMinCardsNeeded
        result = 31 * result + adjustCards
        result = 31 * result + poolName.hashCode()
        result = 31 * result + infos.hashCode()
        result = 31 * result + contestTabs.hashCode()
        result = 31 * result + minCardsNeeded.hashCode()
        return result
    }

    companion object {
        // probably corla
        fun fromMinCardsNeeded(
            poolName: String, poolId: Int, hasExactContests: Boolean,    // aka single style
            infos: Map<Int, ContestInfo>, // do we really need this ??
            contestTabs: Map<Int, ContestTabulation>,  // contestId -> ContestTabulation
        ): CardPoolBuilder {

            // you need at least this many cards for this pool
            val minCardsNeeded = mutableMapOf<Int, Int>()
            contestTabs.forEach { (contestId, contestTab) ->
                val ncards = contestTab.ncards() // nvotes was scaled by stylePct
                val info = infos[contestId]!!
                // based on the contest's votes, you need at least this many cards for this contest
                minCardsNeeded[contestId] = roundUp(ncards.toDouble() / info.voteForN)
            }
            return CardPoolBuilder(poolName, poolId, hasExactContests, infos, contestTabs, minCardsNeeded)
        }

        // used by OneAuditTest, probably boulder
        fun fromMinVotesNeeded(
            poolName: String, poolId: Int, hasExactContests: Boolean,    // aka single style
            infos: Map<Int, ContestInfo>, // do we really need this ??
            contestTabs: Map<Int, ContestTabulation>,  // contestId -> ContestTabulation
        ): CardPoolBuilder {

            val minCardsNeeded = mutableMapOf<Int, Int>() // contestId -> minCardsNeeded
            contestTabs.forEach { (contestId, contestTab) ->
                val voteSum = contestTab.nvotes()
                val info = infos[contestId]!!
                // based on the contest's votes, you need at least this many cards for this contest
                minCardsNeeded[contestId] = roundUp(voteSum.toDouble() / info.voteForN)
            }
            return CardPoolBuilder(poolName, poolId, hasExactContests, infos, contestTabs, minCardsNeeded)
        }
    }
}

// CountyPool: pool with multiple CardStyles
data class CountyPools (
    val countyName: String,
    val countyPoolId: Int,
    val contestTabs: Map<Int, ContestTabulation>,
    val cardCount: Int,
    val styles: List<StyleIF>,
) {
    override fun toString() = buildString {
        appendLine("CountyPools(countyName='$countyName', countyPoolId=$countyPoolId, totalCards=$cardCount")
        styles.forEach{ appendLine("cardStyle:  $it")}
        contestTabs.forEach{ appendLine("  $it")}
    }
}

