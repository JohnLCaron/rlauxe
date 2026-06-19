package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.StyleIF

// builds one AuditableCard
class AuditableCardBuilder(
    val id: String,
    val location: String?,
    val index: Int,
    val prn: Long,
    val phantom: Boolean,
    val styleId: Int,
    val poolId: Int? = null,
    votesIn: Map<Int, IntArray>?,
    val style: StyleIF? = null,
) {
    val votes = mutableMapOf<Int, IntArray>()

    init {
        if (votesIn != null) votes.putAll(votesIn)
    }

    constructor(id: String, location: String?, index: Int, poolId: Int?, styleId: Int):
            this(id, location, index, 0L, false, styleId, poolId, null)

    fun replaceContestVotes(contestId: Int, contestVotes: IntArray): AuditableCardBuilder  {
        votes[contestId] = contestVotes
        return this
    }

    fun replaceContestVote(id: Int, candidateId: Int?) {
        votes[id] = if (candidateId == null) intArrayOf() else intArrayOf(candidateId)
    }

    fun build() : AuditableCard {

        val cardm = AuditableCard.fromVotes(
            id, location, index, prn, phantom,
            styleId = styleId,
            votes = votes,
            poolId = poolId,
        )
        if (style != null) cardm.setStyle(style)
        return cardm
    }

    companion object {
        fun fromCard(card: AuditableCard) = AuditableCardBuilder(
            card.id(),
            if (card.id() == card.location()) null else card.location(),
            card.index(),
            card.prn(),
            card.phantom(),
            card.styleId,
            card.poolId(),
            card.votes(),
            style=card.style(),
        )

    }
}