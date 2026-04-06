package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.audit.CardStyleIF
import org.cryptobiotic.rlauxe.audit.CardWithBatchName

// builds one AuditableCard
class AuditableCardBuilder(
    val id: String,
    val location: String? = null,
    val index: Int,
    val prn: Long,
    val phantom: Boolean,
    val poolId: Int? = null,
    votesIn: Map<Int, IntArray>? = null,
    val cardStyle: CardStyleIF
) {
    val votes = mutableMapOf<Int, IntArray>()

    init {
        if (votesIn != null) votes.putAll(votesIn)
    }

    fun possibleContests() = cardStyle.possibleContests()

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
            cardStyle = cardStyle
        )
    }

    companion object {
        fun fromCard(card: AuditableCard) = AuditableCardBuilder(
            card.id,
            card.location,
            card.index,
            card.prn,
            card.phantom,
            card.poolId,
            card.votes,
            card.cardStyle
        )

    }
}

// builds one AuditableCard
class CardWithBatchNameBuilder(
    val id: String,
    val location: String?,
    val index: Int,
    val prn: Long,
    val phantom: Boolean,
    votesIn: Map<Int, IntArray>?,
    val poolId: Int?,
    val cardStyle: String? = null,
) {
    val votes = mutableMapOf<Int, IntArray>()

    init {
        if (votesIn != null) votes.putAll(votesIn)
    }

    constructor(id: String, location: String?, index: Int, poolId: Int?, cardStyle: String?):
            this(id, location, index, 0L, false, null,  poolId, cardStyle)

    fun replaceContestVotes(contestId: Int, contestVotes: IntArray): CardWithBatchNameBuilder  {
        votes[contestId] = contestVotes
        return this
    }

    fun replaceContestVote(id: Int, candidateId: Int?) {
        votes[id] = if (candidateId == null) intArrayOf() else intArrayOf(candidateId)
    }

    fun build(poolId:Int? = null) : CardWithBatchName {
        val useBatchName: String = when {
            cardStyle != null -> cardStyle
            !votes.isEmpty() -> CardStyle.fromCvr
            else -> "unknown"
        }
        // data class CardWithBatchName (
        //    val location: String, // enough info to find the card for a manual audit.
        //    val index: Int,  // index into the original, canonical list of cards
        //    val prn: Long,   // psuedo random number
        //    val phantom: Boolean,
        //
        //    val votes: Map<Int, IntArray>?,   // CVRs and phantoms
        //    val poolId: Int?,                 // must be set if its from a CardPool  TODO verify batch name, poolId
        //    val batchName: String,            // batch name: "fromCvr" if no batch and its from a CVR (then votes is non null)
        //)
        return CardWithBatchName(
            id, location, index, prn, phantom,
            votes = votes,
            poolId = poolId,
            styleName = useBatchName
        )
    }

    companion object {
        fun from(card: AuditableCard) = CardWithBatchNameBuilder(
            card.id,
            card.location,
            card.index,
            card.prn,
            card.phantom,
            card.votes,
            card.poolId(),
            card.styleName(),
        )

    }
}