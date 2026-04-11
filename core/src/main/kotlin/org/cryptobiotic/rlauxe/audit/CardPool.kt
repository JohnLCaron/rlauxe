package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.estimate.Vunder
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

const val unpooled = "unpooled"

interface CardPoolIF: StyleIF {
    val poolName: String
    val poolId: Int
    fun contestTab(contestId: Int): ContestTabulation?
    fun votesAndUndervotes(contestId: Int): Vunder // throws exception if bad contest id
    fun ncards(): Int
}

data class CardPool(
    override val poolName: String,
    override val poolId: Int,
    val hasExactContests: Boolean,
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
        append("CardPool(poolName='$poolName', poolId=$poolId, totalCards=$totalCards")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CardPool) return false

        if (poolId != other.poolId) return false
        if (hasExactContests != other.hasExactContests) return false
        if (totalCards != other.totalCards) return false
        if (poolName != other.poolName) return false
        if (infos != other.infos) return false
        if (contestTabs != other.contestTabs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = poolId
        result = 31 * result + hasExactContests.hashCode()
        result = 31 * result + totalCards
        result = 31 * result + poolName.hashCode()
        result = 31 * result + infos.hashCode()
        result = 31 * result + contestTabs.hashCode()
        return result
    }
}


