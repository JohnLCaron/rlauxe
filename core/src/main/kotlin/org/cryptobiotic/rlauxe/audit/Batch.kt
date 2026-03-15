package org.cryptobiotic.rlauxe.audit

import kotlin.collections.contains

/* From SamplePopulations.md
 * CardStyle = the full and exact list of contests on a card.
 * card.exactContests = list of contests that are on this card = CardStyle = "we know exactly what contests are on this card".
 * card.possibleContests = list of contests that might be on this card.
 * Batch = "population batch" = a distinct container of cards, from which we can retreive named cards (even if its just by an index into a sorted list).
 * batch.possibleContests = list of contests that are in this batch.
 * batch.hasSingleCardStyle = true if all cards in the batch have a single known CardStyle = "we know exactly what contests are on each card".
 */
// Generalization of a BallotStyle or CardStyle
interface BatchIF {
    fun name(): String
    fun id(): Int
    fun possibleContests(): IntArray // the set of contests that might be on any card in the population
    fun hasSingleCardStyle(): Boolean // aka hasStyle: if all cards have exactly the contests in possibleContests()
    fun ncards(): Int
    fun hasContest(contestId: Int): Boolean
    // if you have this, then you're a Pool
    // fun votesAndUndervotes(contestId: Int): Vunder // , voteForN: Int): Vunder
}

data class Batch(
    val name: String,
    val id: Int,
    val possibleContests: IntArray,      // the list of possible contests.
    val hasSingleCardStyle: Boolean,     // aka hasStyle: if all cards have exactly the contests in hasSingleCardStyle
) : BatchIF {
    var ncards = 0
    fun setNcards(ncards: Int): Batch {
        this.ncards = ncards
        return this
    }

    override fun name() = name
    override fun id() = id
    override fun hasSingleCardStyle() = hasSingleCardStyle
    override fun ncards() = ncards
    override fun hasContest(contestId: Int) = possibleContests.contains(contestId)
    override fun possibleContests() = possibleContests

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Batch) return false

        if (id != other.id) return false
        if (hasSingleCardStyle != other.hasSingleCardStyle) return false
        if (ncards != other.ncards) return false
        if (name != other.name) return false
        if (!possibleContests.contentEquals(other.possibleContests)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + hasSingleCardStyle.hashCode()
        result = 31 * result + ncards
        result = 31 * result + name.hashCode()
        result = 31 * result + possibleContests.contentHashCode()
        return result
    }


}


