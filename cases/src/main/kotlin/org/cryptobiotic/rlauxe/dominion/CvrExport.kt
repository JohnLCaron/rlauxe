package org.cryptobiotic.rlauxe.dominion

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.oneaudit.unpooled


// intermediate CVR representation for DominionCvrSummary
data class CvrExport(val id: String, val group: Int, val votes: Map<Int, IntArray>) {
    // constructor(cvr: Cvr) : this(cvr.id, 0, cvr.votes) // TODO the group id is lost when converting to Cvr and back

    // Calculate the pool name from the cvr id. Could pass in a function (CvrExport) -> pool name
    fun poolKey(): String {
        if (group == 2) return unpooled
        val lastIdx = id.lastIndexOf('-')
        return id.substring(0, lastIdx)
    }

    fun toAuditableCard(index: Int, prn: Long, phantom: Boolean = false, pools: Map<String, Int>? = null, showPoolVotes: Boolean = true): AuditableCard {
        val contests = votes.map { it.key }.toIntArray()
        val poolId = if (pools == null || group != 1) null else pools[ poolKey() ]  // TODO not general
        // TODO if you want to delete the votes, add an adapter
        val useVotes = if (poolId == null || showPoolVotes) votes else null
        return AuditableCard(id, index, prn, phantom, contests, useVotes, poolId)
    }

    fun toCvr(phantom: Boolean = false, pools: Map<String, Int>? = null) : Cvr {
        val poolId = if (pools == null || group != 1) null else pools[ poolKey() ] // TODO not general
        return Cvr(id, votes, phantom, poolId)
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
