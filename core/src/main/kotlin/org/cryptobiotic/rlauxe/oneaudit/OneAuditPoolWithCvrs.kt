package org.cryptobiotic.rlauxe.oneaudit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.Vunder
import kotlin.collections.component1
import kotlin.collections.component2

private val logger = KotlinLogging.logger("OneAuditPoolFromCvrs")

data class OneAuditPoolFromCvrs(
    override val poolName: String,
    override val poolId: Int,
    val hasSingleCardStyle: Boolean,
    val infos: Map<Int, ContestInfo>,
): OneAuditPoolIF {

    val contestTabs = mutableMapOf<Int, ContestTabulation>()  // contestId -> ContestTabulation
    var totalCards = 0

    override fun name() = poolName
    override fun id() = poolId
    override fun hasSingleCardStyle() = hasSingleCardStyle

    override fun hasContest(contestId: Int) = contestTabs.contains(contestId)
    override fun possibleContests() = (contestTabs.map { it.key }).toSortedSet().toIntArray()
    override fun contestTab(contestId: Int) = contestTabs[contestId]

    override fun ncards() = totalCards

    // this is when you have CVRs. (sfoa, sfoans)
    fun accumulateVotes(cvr : Cvr) {
        cvr.votes.forEach { (contestId, candIds) ->
            if (infos[contestId] == null) {
                logger.error { "cvr has unknown contest $contestId" }
            } else {
                val contestTab = contestTabs.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                contestTab.addVotes(candIds, cvr.phantom)
            }
        }
        totalCards++
    }

    override fun votesAndUndervotes(contestId: Int): Vunder {
        val contestTab = contestTabs[contestId]!!
        return contestTab.votesAndUndervotes(poolId, ncards()) // good reason for cardPool to always have contestTabs
    }

    // every cvr has to have every contest in the pool
    fun addUndervotes(cvr: Cvr): Cvr {
        var wasAmended = false
        val votesM= cvr.votes.toMutableMap()
        val needContests = this.contestTabs.keys
        needContests.forEach { contestId ->
            if (!votesM.containsKey(contestId)) {
                votesM[contestId] = IntArray(0)
                wasAmended = true
                addUndervote(contestId)
            }
        }
        return if (!wasAmended) cvr else cvr.copy(votes = votesM)
    }

    fun addUndervote(contestId: Int) {
        val contestTab = contestTabs[contestId]!!
        contestTab.undervotes += if (contestTab.isIrv) 1 else contestTab.voteForN
        contestTab.ncardsTabulated++
        contestTab.novote++
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OneAuditPoolFromCvrs) return false

        if (poolId != other.poolId) return false
        if (totalCards != other.totalCards) return false
        if (poolName != other.poolName) return false
        if (contestTabs != other.contestTabs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = poolId
        result = 31 * result + totalCards
        result = 31 * result + poolName.hashCode()
        result = 31 * result + contestTabs.hashCode()
        return result
    }

    override fun toString() = buildString {
        appendLine("OneAuditPoolFromCvrs(poolName='$poolName', poolId=$poolId, totalCards=$totalCards")
    }

    fun showTabs() = buildString {
        appendLine("OneAuditPoolFromCvrs(poolName='$poolName', poolId=$poolId, totalCards=$totalCards")
        contestTabs.values.forEach { appendLine("  $it")}
    }

    fun toOneAuditPool(): OneAuditPool {
        return OneAuditPool(this.poolName, this.poolId, this.hasSingleCardStyle, this.infos, this.contestTabs, this.totalCards)
    }

}
