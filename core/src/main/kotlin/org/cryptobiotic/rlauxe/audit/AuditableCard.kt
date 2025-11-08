package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator

// A generalization of Cvr, allowing votes to be null, eg for Polling or OneAudit.
// Also possibleContests/cardStyle represents sample population information
data class AuditableCard (
    val location: String, // info to find the card for a manual audit. Aka ballot identifier.
    val index: Int,  // index into the original, canonical list of cards
    val prn: Long,   // psuedo random number
    val phantom: Boolean,
    val possibleContests: IntArray, // list of contests that might be on the ballot. TODO replace with cardStyle
    val votes: Map<Int, IntArray>?, // for CLCA, a map of contest -> the candidate ids voted; must include undervotes (??)
                                    // for IRV, ranked first to last; missing for pooled data or polling audits
    val poolId: Int?, // for OneAudit
    val cardStyle: String? = null,
) {
    // if there are no votes, the IntArrays are all empty; looks like all undervotes
    fun cvr() : Cvr {
        val useVotes = if (votes != null) votes else {
            possibleContests.mapIndexed { idx, contestId ->
                Pair(contestId, votes?.get(idx) ?: intArrayOf())
            }.toMap()
        }
        return Cvr(location, useVotes, phantom, poolId)
    }

    override fun toString() = buildString {
        appendLine("AuditableCard(desc='$location', index=$index, sampleNum=$prn, phantom=$phantom, possibleContests=${possibleContests.contentToString()}, poolId=$poolId)")
        votes?.forEach { id, vote -> appendLine("   contest $id: ${vote.contentToString()}")}
    }

    fun hasContest(contestId: Int): Boolean {
        return contests().contains(contestId)
    }

    fun contests(): List<Int> {
        return if (possibleContests.isNotEmpty()) possibleContests.toList()
            else if (votes != null) votes.keys.toList()
            else emptyList()
    }

    // Kotlin data class doesnt handle IntArray and List<IntArray> correctly
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuditableCard) return false

        if (index != other.index) return false
        if (prn != other.prn) return false
        if (phantom != other.phantom) return false
        if (poolId != other.poolId) return false
        if (location != other.location) return false
        if (!possibleContests.contentEquals(other.possibleContests)) return false
        if ((votes == null) != (other.votes == null)) return false

        if (votes != null) {
            for ((contestId, candidates) in votes) {
                val otherCands = other.votes!![contestId]
                if (!candidates.contentEquals(otherCands)) return false
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
        result = 31 * result + possibleContests.contentHashCode()
        votes?.forEach { (contestId, candidates) -> result = 31 * result + contestId.hashCode() + candidates.contentHashCode() }
        return result
    }

    companion object {

        // dont throw away the votes
        fun fromCvr(cvr: Cvr, index: Int, sampleNum: Long): AuditableCard {
            val sortedVotes = cvr.votes.toSortedMap()
            val contests = sortedVotes.keys.toList()
            return AuditableCard(cvr.id, index, sampleNum, cvr.phantom, contests.toIntArray(), cvr.votes, cvr.poolId)
        }

        fun fromCvrHasStyle(cvr: Cvr, index: Int, isClca: Boolean): AuditableCard {
            val contests = if (isClca) intArrayOf() else cvr.votes.keys.toList().sorted().toIntArray()
            val votes = if (isClca) cvr.votes else null
            val poolId = if (isClca) null else cvr.poolId
            return AuditableCard(cvr.id, index, 0, cvr.phantom, contests, votes, poolId)
        }

        fun fromCvrNoStyle(cvr: Cvr, index: Int, possibleContests: IntArray, isClca: Boolean): AuditableCard {
            val votes = if (isClca) cvr.votes else null
            return AuditableCard(cvr.id, index, 0, cvr.phantom, possibleContests, votes = votes, cvr.poolId)
        }
    }
}

data class CardLocationManifest(
    val cardLocations: CloseableIterable<AuditableCard>,
    val cardStyles: List<CardStyle> // empty if style info not available
)

// essentially, CardStyle factors out the contestIds, which the CardLocation references, so its a form of normalization
data class CardStyle(
    val name: String,
    val id: Int,
    val contestNames: List<String>,
    val contestIds: List<Int>,
    val numberOfCards: Int?,
) {
    val ncards = numberOfCards ?: 0
    fun hasContest(contestId: Int) = contestIds.contains(contestId)

    override fun toString() = buildString {
        append("CardStyle('$name' ($id), contestIds=$contestIds")
    }

    companion object {
        fun make(styleId: Int, contestNames: List<String>, contestIds: List<Int>, numberBallots: Int?): CardStyle {
            return CardStyle("style$styleId", styleId, contestNames, contestIds, numberBallots)
        }
    }
}

