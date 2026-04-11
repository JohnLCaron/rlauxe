package org.cryptobiotic.rlauxe.audit

import kotlin.collections.contains

/* From SamplePopulations.md
 * CardStyle = the full and exact list of contests on a card.
 * card.exactContests = list of contests that are on this card = CardStyle = "we know exactly what contests are on this card".
 * card.possibleContests = list of contests that might be on this card.
 * Batch = "population batch" = a distinct container of cards, from which we can retreive named cards (even if its just by an index into a sorted list).
 * batch.possibleContests = list of contests that are in this batch.
 * batch.hasExactContests = true if all cards in the batch have a single known CardStyle = "we know exactly what contests are on each card".
 */
interface StyleIF {
    fun name(): String
    fun id(): Int
    fun possibleContests(): IntArray // the set of contests that might be on any card in the batch
    fun hasExactContests(): Boolean // aka hasStyle: if all cards have exactly the contests in possibleContests()
    fun hasContest(contestId: Int): Boolean // "is in possibleContests()"
    // if you have these, then you're a CardPool
    //   fun ncards(): Int
    //   fun votesAndUndervotes(contestId: Int): Vunder
}

data class CardStyle(
    val name: String,
    val id: Int,
    val possibleContests: IntArray,      // the list of possible contests.
    val hasExactContests: Boolean,       // aka hasStyle: if all cards have exactly the contests in possibleContests
) : StyleIF {
    override fun name() = name
    override fun id() = id
    override fun hasExactContests() = hasExactContests
    override fun hasContest(contestId: Int) = possibleContests.contains(contestId)
    override fun possibleContests() = possibleContests

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CardStyle) return false

        if (id != other.id) return false
        if (hasExactContests != other.hasExactContests) return false
        if (name != other.name) return false
        if (!possibleContests.contentEquals(other.possibleContests)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + hasExactContests.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + possibleContests.contentHashCode()
        return result
    }

    companion object {
        // dont use these names for other batches
        val fromCvr = "_fromCvr"
        val phantoms = "_phantoms"

        //  assumes cvrsContainUndervotes, use regular batch if not.
        val fromCvrBatch = CardStyle(fromCvr, -1, intArrayOf(), true)
        val phantomBatch = CardStyle(phantoms, -1, intArrayOf(), true)

        fun useVotes(batchName: String): Boolean = batchName == fromCvr || batchName == phantoms

        fun from(id: Int, contests: Set<Int>) = CardStyle(
            "cardStyle$id", id, contests.toList().sorted().toIntArray(), true)
    }
}


