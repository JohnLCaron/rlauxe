package org.cryptobiotic.rlauxe.dominion

import org.cryptobiotic.rlauxe.audit.CardWithBatchName
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.audit.unpooled

// intermediate CVR representation for DominionCvrSummary
data class CvrExport(val id: String, val group: Int, val ballotStyleId: Int, val precinctPortionId: Int, val votes: Map<Int, IntArray>) {

    // Calculate the pool name from the cvr id. Could pass in a function (CvrExport) -> pool name
    fun poolKey(): String {
        if (group == 2) return unpooled
        val lastIdx = id.lastIndexOf('-')
        return id.substring(0, lastIdx)
    }

    // only used in test
    fun toCardNoBatch(index: Int, prn: Long, phantom: Boolean = false, pools: Map<String, Int>? = null, showPoolVotes: Boolean = true): CardWithBatchName {
        val contests = votes.map { it.key }.toIntArray()
        val poolkey = poolKey()
        val poolId = if (pools == null || group != 1) null else pools[ poolKey() ]  // TODO not general
        // TODO if you want to delete the votes, add an adapter
        val useVotes = if (poolId == null || showPoolVotes) votes else null
        return CardWithBatchName(id, null, index, prn, phantom, useVotes, poolId, styleName=poolKey())
    }

    fun toCvr(pools: Map<String, Int>? = null , convertId: String) : Cvr {
        val poolId = if (pools == null || group != 1) null else pools[ poolKey() ] // TODO not general
        return Cvr(convertId, votes, phantom=false, poolId)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CvrExport

        if (group != other.group) return false
        if (id != other.id) return false
        if (votes.size != other.votes.size) return false
        for ((contestId, candidates) in votes) {
            if (!candidates.contentEquals(other.votes[contestId])) return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = group
        result = 31 * result + id.hashCode()
        votes.forEach { (contestId, candidates) -> result = 31 * result + contestId.hashCode() + candidates.contentHashCode() }
        return result
    }

}
