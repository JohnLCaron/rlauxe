package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Cvr

// PS email 3/27/25
// With ONEAudit, things get more complicated because you have to start by adding every contest that appears on any card
// in a tally batch to every card in that tally batch and increase the upper bound on the number of cards in
// the contest appropriately. That's in the SHANGRLA codebase.

/** Generalization of CvrUnderAudit. */
data class AuditableCard (
    val desc: String, // info to find the card for a manual audit. Part of the info the Prover commits to before the audit.
    val index: Int,  // index into the original, canonical (committed-to) list of cards
    val prn: Long,
    val phantom: Boolean,
    val contests: IntArray, // aka ballot style
    val votes: List<IntArray>?, // contest -> list of candidates voted for; for IRV, ranked first to last
    val poolId: Int?, // for OneAudit
) {
    // if there are no votes, the IntArrays are all empty; looks like all undervotes
    fun cvr() : Cvr {
        val votePairs = contests.mapIndexed { idx, contestId ->
            Pair(contestId, votes?.get(idx) ?: intArrayOf())
        }
        return Cvr(desc, votePairs.toMap(), phantom, poolId)
    }

    override fun toString() = buildString {
        appendLine("AuditableCard(desc='$desc', index=$index, sampleNum=$prn, phantom=$phantom, contests=${contests.contentToString()}, poolId=$poolId)")
        votes?.forEachIndexed { idx, vote -> appendLine("   contest $idx: ${vote.contentToString()}")}
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
        if (desc != other.desc) return false
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
        result = 31 * result + desc.hashCode()
        result = 31 * result + contests.contentHashCode()
        result = 31 * result + (votes?.hashCode() ?: 0)
        return result
    }

    companion object {
        fun fromCvr(cvr: Cvr, index: Int, sampleNum: Long): AuditableCard {
            val contests = cvr.votes.keys.toSortedSet().toList()
            return AuditableCard(cvr.id, index, sampleNum, cvr.phantom, contests.toIntArray(), cvr.votes.values.toList(), null)
        }
        fun fromCvrWithPool(cvr: Cvr, index: Int, sampleNum: Long, poolId: Int): AuditableCard {
            val contests = cvr.votes.keys.toSortedSet().toList()
            return AuditableCard(cvr.id, index, sampleNum, cvr.phantom, contests.toIntArray(), null, poolId)
        }
        fun fromCvrWithPoolContests(cvr: Cvr, index: Int, sampleNum: Long, contests: List<Int>, poolId: Int): AuditableCard {
            return AuditableCard(cvr.id, index, sampleNum, cvr.phantom, contests.toIntArray(), null, poolId)
        }
    }
}

// could imagine the cvr is specific to one contest
data class AuditableCvrForContest(
    val id: String,
    val contestId: Int,
    val votes: IntArray, // list of candidates voted for; for IRV, ranked first to last
    val phantom: Boolean = false,
    val poolId: Int?,
)

// older design for polling audits
data class AuditableBallot(
    val desc: String,
    val contests: IntArray?,
    val ballotStyleId: Int?,
    val phantom: Boolean = false,
)

// just factoring out the contest list; more efficient but also may be how info is stored
data class AuditableBallotStyle(
    val id: String,
    val contests: IntArray?,
)