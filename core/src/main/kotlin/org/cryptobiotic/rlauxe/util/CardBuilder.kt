package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.AuditableCard

// builds one card
class CardBuilder(
    val location: String,
    val index: Int,
    val prn: Long,
    val phantom: Boolean,
    val possibleContests: IntArray,
    votesIn: Map<Int, IntArray>?,
    val poolId: Int?,
    val cardStyle: String? = null,
) {
    val votes = mutableMapOf<Int, IntArray>()

    init {
        if (votesIn != null) votes.putAll(votesIn)
    }

    constructor(location: String, index: Int, poolId: Int?, cardStyle: String?):
            this(location, index, 0L, false, intArrayOf(), null, poolId, cardStyle)

    fun replaceContestVotes(contestId: Int, contestVotes: IntArray): CardBuilder  {
        votes[contestId] = contestVotes
        return this
    }

    fun replaceContestVote(id: Int, candidateId: Int?) {
        votes[id] = if (candidateId == null) intArrayOf() else intArrayOf(candidateId)
    }

    fun build(poolId:Int? = null) : AuditableCard {
        // data class AuditableCard (
        //    val location: String, // info to find the card for a manual audit. Aka ballot identifier.
        //    val index: Int,  // index into the original, canonical list of cards
        //    val prn: Long,   // psuedo random number
        //    val phantom: Boolean,
        //    // val possibleContests: IntArray, // list of contests that might be on the ballot.
        //    val votes: Map<Int, IntArray>?, // for CLCA, a map of contest -> the candidate ids voted; must include undervotes (??)
        //                                    // for IRV, ranked first to last; missing for pooled data or polling audits
        //    val poolId: Int?, // for OneAudit
        //    val cardStyle: String? = null,
        return AuditableCard(location, index, prn, phantom,
            votes= if (votes.isEmpty()) null else votes,
            poolId=poolId ?: this.poolId,
            cardStyle=cardStyle)
    }

    companion object {
        fun fromCard(card: AuditableCard) = CardBuilder(
            card.location,
            card.index,
            card.prn,
            card.phantom,
            card.contests(),
            card.votes,
            card.poolId,
            card.cardStyle
        )

    }
}