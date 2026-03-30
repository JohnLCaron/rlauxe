package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.Batch
import org.cryptobiotic.rlauxe.audit.BatchIF
import org.cryptobiotic.rlauxe.audit.CardWithBatchName

// builds one AuditableCard
class AuditableCardBuilder(
    val location: String,
    val index: Int,
    val prn: Long,
    val phantom: Boolean,
    votesIn: Map<Int, IntArray>?,
    val batch: BatchIF
) {
    val votes = mutableMapOf<Int, IntArray>()

    init {
        if (votesIn != null) votes.putAll(votesIn)
    }

    fun possibleContests() = batch.possibleContests()

    fun replaceContestVotes(contestId: Int, contestVotes: IntArray): AuditableCardBuilder  {
        votes[contestId] = contestVotes
        return this
    }

    fun replaceContestVote(id: Int, candidateId: Int?) {
        votes[id] = if (candidateId == null) intArrayOf() else intArrayOf(candidateId)
    }

    fun build() : AuditableCard {
        return AuditableCard(
            location, index, prn, phantom,
            votes = votes,
            batch = batch
        )
    }

    companion object {
        fun fromCard(card: AuditableCard) = AuditableCardBuilder(
            card.location,
            card.index,
            card.prn,
            card.phantom,
            card.votes,
            card.batch
        )

    }
}

// builds one AuditableCard
class CardWithBatchNameBuilder(
    val location: String,
    val index: Int,
    val prn: Long,
    val phantom: Boolean,
    votesIn: Map<Int, IntArray>?,
    val poolId: Int?,
    val batchName: String? = null,
) {
    val votes = mutableMapOf<Int, IntArray>()

    init {
        if (votesIn != null) votes.putAll(votesIn)
    }

    constructor(location: String, index: Int, poolId: Int?, cardStyle: String?):
            this(location, index, 0L, false, null,  poolId, cardStyle)

    fun replaceContestVotes(contestId: Int, contestVotes: IntArray): CardWithBatchNameBuilder  {
        votes[contestId] = contestVotes
        return this
    }

    fun replaceContestVote(id: Int, candidateId: Int?) {
        votes[id] = if (candidateId == null) intArrayOf() else intArrayOf(candidateId)
    }

    fun build(poolId:Int? = null) : CardWithBatchName {
        val useBatchName: String = when {
            batchName != null -> batchName
            !votes.isEmpty() -> Batch.fromCvr
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
            location, index, prn, phantom,
            votes = votes,
            poolId = poolId,
            batchName = useBatchName
        )
    }

    companion object {
        fun from(card: AuditableCard) = CardWithBatchNameBuilder(
            card.location,
            card.index,
            card.prn,
            card.phantom,
            card.votes,
            card.poolId(),
            card.batchName(),
        )

    }
}