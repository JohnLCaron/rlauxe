package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.util.CloseableIterable

/*
hasStyle: "we know exactly what contests are on each card".
    could be true even when we dont know the votes.
    Npop == Nc ?
    if false, then we expect to see mvrs (and cvrs?) in the clca assorter without the contest.
    if true, then we dont expect to see mvrs (and cvrs?) in the clca assorter without the contest.
    examples: single contest election; all cards have a card style;

CardStyle is the full and exact list of contests on a card.
  (The EA knows this, goddammit. We should insist on it for all types of audits, and put them on the CardManifest.)
  (So, the CardStyle exists and is known by the EA, but the info may not be available at the audit.)
  Privacy: Card styles that could identify voters are called RedactedCardStyles.
  The RedactedCardStyle must include all contests on the card, even if there are no votes for the contest.
  Put all cards (and only those cards) with a RedactedCardStyle into a OneAudit pool.

Batch describes a distinct population of cards.
    batchName: String
    batchSize: Int (?)
    possibleContests: IntArray() are the list of possible contests.
    hasStyle: Boolean = if all cards have exactly the contests in PossibleContests

OneAuditPool is a Batch with vote totals for the batch.
    hasStyle is true when all cards have a single CardStyle.
    regVotes: Map<Int, IntArray> total votes for non-IRV
    irvVotes: VoteConsolidator votes for IRV
 */

data class CardStyle2(
    val name: String,
    val contests: IntArray,
)

interface BatchIF {
    val name: String
    val id: Int
    val possibleContests: IntArray // the list of possible contests.
    val exactContests: Boolean     // if all cards have exactly the contests in possibleContests
    fun ncards(): Int
}

data class PopulationBatch(
    override val name: String,
    override val id: Int,
    override val possibleContests: IntArray, // the list of possible contests.
    override val exactContests: Boolean,     // if all cards have exactly the contests in possibleContests
) : BatchIF {
    var ncards = 0
    override fun ncards() = ncards
}

data class OneAuditPool(
    override val name: String,
    override val id: Int,
    override val possibleContests: IntArray,
    override val exactContests: Boolean,
    val poolId: Int,
): BatchIF {
    var ncards = 0
    override fun ncards() = ncards
}

interface CvrIF {
    fun hasContest(contestId: Int): Boolean // "is in P_c".
    fun location(): String
    fun isPhantom(): Boolean
    fun poolId(): Int?

    fun hasMarkFor(contestId: Int, candidateId:Int): Int
    fun votes(contestId: Int): IntArray?
}

data class CardProxy (
    val location: String, // info to find the card for a manual audit. Aka ballot identifier.
    val index: Int,  // index into the original, canonical list of cards
    val prn: Long,   // psuedo random number
    val phantom: Boolean,

    // must have at least one of:
    val votes: Map<Int, IntArray>?,
    val poolId: Int?, // if not null, then is in a OneAuditPool
    val batchId: Int?, // not needed when votes != null and hasUndervotes=hasStyle=exactContests=true
)

data class CardManifest(
    val cards: CloseableIterable<CardProxy>,
    val batches: List<BatchIF>
)

