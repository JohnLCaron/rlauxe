package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Cvr

// A generalization of Cvr, allowing votes to be null, eg for Polling or OneAudit
data class AuditableCard (
    val location: String, // info to find the card for a manual audit. Aka ballot identifier.
    val index: Int,  // index into the original, canonical list of cards
    val prn: Long,   // psuedo random number
    val phantom: Boolean,
    val contests: IntArray, // list of contests on this ballot. TODO optional when !hasStyles
    val votes: List<IntArray>?, // for each contest, an array of the candidate ids voted for; for IRV, ranked first to last; missing for pooled data
    val poolId: Int?, // for OneAudit
) {
    // if there are no votes, the IntArrays are all empty; looks like all undervotes
    fun cvr() : Cvr {
        val votePairs = contests.mapIndexed { idx, contestId ->
            Pair(contestId, votes?.get(idx) ?: intArrayOf())
        }
        return Cvr(location, votePairs.toMap(), phantom, poolId)
    }

    override fun toString() = buildString {
        appendLine("AuditableCard(desc='$location', index=$index, sampleNum=$prn, phantom=$phantom, contests=${contests.contentToString()}, poolId=$poolId)")
        votes?.forEachIndexed { idx, vote -> appendLine("   contest $idx: ${vote.contentToString()}")}
    }

    fun hasContest(contestId: Int): Boolean {
         // TODO shit cant tell if we have styles or not.
        return contests.contains(contestId)
    }

    // Kotlin data class doesnt handle IntArray and List<IntArray> correctly
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AuditableCard

        if (index != other.index) return false
        if (prn != other.prn) return false
        if (phantom != other.phantom) return false
        if (poolId != other.poolId) return false
        if (location != other.location) return false
        if (!contests.contentEquals(other.contests)) return false
        if ((votes == null) != (other.votes == null)) return false
        if (votes != null) {
            if (votes.size != other.votes!!.size) return false
            votes.forEachIndexed { idx, vote ->
                if (!vote.contentEquals(other.votes[idx])) return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + prn.hashCode()
        result = 31 * result + phantom.hashCode()
        result = 31 * result + (poolId ?: 0)
        result = 31 * result + location.hashCode()
        result = 31 * result + contests.contentHashCode()
        votes?.forEach { vote ->
            result = 31 * result + vote.contentHashCode()
        }
        return result
    }

    companion object {
        // dont use this, except for testing and initialization. generally, sampleNum should be set.
        fun fromCvrWithZeros(cvr: Cvr): AuditableCard {
            // store the contest separate from the candidates
            val sortedVotes = cvr.votes.toSortedMap()
            val contests = sortedVotes.keys.toList()
            return AuditableCard(cvr.id, 0, 0L, cvr.phantom, contests.toIntArray(), sortedVotes.values.toList(), null)
        }

        // TODO dont throw away the votes; used for both cvr and mvr.
        //      if you want to throw away the votes use fromCvrWithPool()
        fun fromCvr(cvr: Cvr, index: Int, sampleNum: Long): AuditableCard {
            // store the contests separate from the candidates
            val sortedVotes = cvr.votes.toSortedMap()
            val contests = sortedVotes.keys.toList()
            return AuditableCard(cvr.id, index, sampleNum, cvr.phantom, contests.toIntArray(), sortedVotes.values.toList(), cvr.poolId)
        }

        // there are no votes
        fun fromCvrWithPool(cvr: Cvr, index: Int, sampleNum: Long, poolId: Int): AuditableCard {
            val contests = cvr.votes.keys.toSortedSet().toList()
            return AuditableCard(cvr.id, index, sampleNum, cvr.phantom, contests.toIntArray(), null, poolId)
        }

        fun fromCardLocation(cardLocation: CardLocation, index: Int, sampleNum: Long, poolId: Int? = null): AuditableCard {
            return AuditableCard(cardLocation.location, index, sampleNum, cardLocation.phantom, cardLocation.contests(), null, poolId)
        }
    }
}

