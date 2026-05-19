package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.AuditableCardIF
import org.cryptobiotic.rlauxe.audit.AuditableCardM
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.audit.StyleIF

// builds one AuditableCard
class AuditableCardBuilder(
    val id: String,
    val location: String? = null,
    val index: Int,
    val prn: Long,
    val phantom: Boolean,
    val poolId: Int? = null,
    votesIn: Map<Int, IntArray>? = null,
    val styleName: String,
    val style: StyleIF?
) {
    val votes = mutableMapOf<Int, IntArray>()

    init {
        if (votesIn != null) votes.putAll(votesIn)
    }

    fun replaceContestVotes(contestId: Int, contestVotes: IntArray): AuditableCardBuilder  {
        votes[contestId] = contestVotes
        return this
    }

    fun replaceContestVote(id: Int, candidateId: Int?) {
        votes[id] = if (candidateId == null) intArrayOf() else intArrayOf(candidateId)
    }

    fun build() : AuditableCard {
        return AuditableCard(
            id, location, index, prn, phantom,
            poolId = poolId,
            votes = votes,
            style = style!! // TODO
        )
    }

    companion object {
        fun fromCard(card: AuditableCardIF) = AuditableCardBuilder(
            card.id(),
            card.location(),
            card.index(),
            card.prn(),
            card.phantom(),
            card.poolId(),
            card.votes(),
            card.styleName(),
            card.style()
        )

    }
}

// builds one AuditableCardM
class AuditableCardMBuilder(
    val id: String,
    val location: String?,
    val index: Int,
    val prn: Long,
    val phantom: Boolean,
    val styleName: String? = null,
    val poolId: Int? = null,
    votesIn: Map<Int, IntArray>?,
    val style: StyleIF? = null,
) {
    val votes = mutableMapOf<Int, IntArray>()

    init {
        if (votesIn != null) votes.putAll(votesIn)
    }

    constructor(id: String, location: String?, index: Int, poolId: Int?, cardStyle: String?):
            this(id, location, index, 0L, false, cardStyle, poolId, null)

    fun replaceContestVotes(contestId: Int, contestVotes: IntArray): AuditableCardMBuilder  {
        votes[contestId] = contestVotes
        return this
    }

    fun replaceContestVote(id: Int, candidateId: Int?) {
        votes[id] = if (candidateId == null) intArrayOf() else intArrayOf(candidateId)
    }

    fun build() : AuditableCardM {
        val useBatchName: String = when {
            styleName != null -> styleName
            !votes.isEmpty() -> CardStyle.fromCvr
            else -> "unknown"
        }
        val cardm = AuditableCardM.fromVotes(
            id, location, index, prn, phantom,
            votes = votes,
            poolId = poolId,
            styleName = useBatchName
        )
        if (style != null) cardm.setStyle(style)
        return cardm
    }

    companion object {
        fun fromCard(card: AuditableCardIF) = AuditableCardMBuilder(
            card.id(),
            if (card.id() == card.location()) null else card.location(),
            card.index(),
            card.prn(),
            card.phantom(),
            card.styleName(),
            card.poolId(),
            card.votes(),
            style=card.style(),
        )

    }
}