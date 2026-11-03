package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.StyleIF

// builds one AuditableCard
class AuditableCardBuilder(
    var id: String,         // corla imprintedId
    var county: String,
    var location: String,
    var index: Int,
    var prn: Long,
    var phantom: Boolean,
    var styleId: Int,  // or style name ??
    var poolId: Int? = null,
    votesIn: Map<Int, IntArray>?,
    val style: StyleIF? = null,
) {
    val votes = mutableMapOf<Int, IntArray>()

    init {
        if (votesIn != null) votes.putAll(votesIn)
    }

    constructor(id: String, county: String, location: String, index: Int, poolId: Int?, styleId: Int):
            this(id, county, location, index, 0L, false, styleId, poolId, null)

    constructor(id: String, county: String, location: String, poolId: Int?, style: StyleIF):
            this(id, county, location, 0, 0L, false, style.id(), poolId, null, style=style)

    //              id = manifestEntry.imprintedId(),
    //                location = manifestEntry.location(),
    //                poolId = null,
    //                style = cardStyle

    fun replaceContestVotes(contestId: Int, contestVotes: IntArray): AuditableCardBuilder  {
        votes[contestId] = contestVotes
        return this
    }

    fun replaceContestVote(id: Int, candidateId: Int?) {
        votes[id] = if (candidateId == null) intArrayOf() else intArrayOf(candidateId)
    }

    fun build() : AuditableCard {
        val cardm = AuditableCard.fromVotes(
            id, county, location, index, prn, phantom,
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
            card.county,
            card.location(),
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