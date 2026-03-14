package org.cryptobiotic.rlauxe.oneaudit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.PopulationIF
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.core.ContestInfo
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

private val logger = KotlinLogging.logger("OneAuditPool")

const val unpooled = "unpooled"

interface OneAuditPoolIF: PopulationIF {
    val poolName: String
    val poolId: Int
    fun contestTab(contestId: Int): ContestTabulation?
    fun votesAndUndervotes(contestId: Int): Vunder // , voteForN: Int): Vunder
}

// the common class
data class OneAuditPool(
    override val poolName: String,
    override val poolId: Int,
    val hasSingleCardStyle: Boolean,
    val infos: Map<Int, ContestInfo>,
    val contestTabs: Map<Int, ContestTabulation>,  // contestId -> ContestTabulation
    val totalCards: Int,
): OneAuditPoolIF {
    override fun name() = poolName
    override fun id() = poolId
    override fun hasSingleCardStyle() = hasSingleCardStyle
    override fun ncards() = totalCards

    override fun hasContest(contestId: Int) = contestTabs.contains(contestId)
    override fun possibleContests() = (contestTabs.map { it.key }).toSortedSet().toIntArray()

    override fun contestTab(contestId: Int) = contestTabs[contestId]

    override fun votesAndUndervotes(contestId: Int): Vunder {
        val contestTab = contestTabs[contestId]!!
        return contestTab.votesAndUndervotes(poolId, ncards(), hasSingleCardStyle)
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
        appendLine("OneAuditPool(poolName='$poolName', poolId=$poolId, totalCards=$totalCards")
    }

    fun showTabs() = buildString {
        appendLine("OneAuditPool(poolName='$poolName', poolId=$poolId, totalCards=$totalCards")
        contestTabs.values.forEach { appendLine("  $it")}
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OneAuditPool) return false

        if (poolId != other.poolId) return false
        if (hasSingleCardStyle != other.hasSingleCardStyle) return false
        if (totalCards != other.totalCards) return false
        if (poolName != other.poolName) return false
        if (infos != other.infos) return false
        if (contestTabs != other.contestTabs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = poolId
        result = 31 * result + hasSingleCardStyle.hashCode()
        result = 31 * result + totalCards
        result = 31 * result + poolName.hashCode()
        result = 31 * result + infos.hashCode()
        result = 31 * result + contestTabs.hashCode()
        return result
    }
}


