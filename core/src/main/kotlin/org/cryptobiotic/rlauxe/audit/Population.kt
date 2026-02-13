package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.util.CloseableIterable
import kotlin.collections.contains

/*
* CardStyle = the full and exact list of contests on a card.
* card.exactContests = list of contests that are on this card = CardStyle = "we know exactly what contests are on this card".
* card.possibleContests = list of contests that might be on this card.
* "population batch" = batch = a distinct container of cards, from which we can retreive named cards (even if its just by an index into a sorted list).
* batch.possibleContests = list of contests that are in this batch.
* batch.hasCardStyle = true if all cards in the batch have a single known CardStyle = "we know exactly what contests are on each card".
*/

interface PopulationIF {
    fun name(): String
    fun id(): Int
    fun contests(): IntArray // the set of contests that might be on any card in the population
    fun hasSingleCardStyle(): Boolean // aka hasStyle: if all cards have exactly the contests in possibleContests
    fun ncards(): Int
    fun hasContest(contestId: Int): Boolean
}

// serialization turns into this
data class Population(
    val name: String,
    val id: Int,
    val possibleContests: IntArray, // the list of possible contests.
    val hasSingleCardStyle: Boolean,     // aka hasStyle: if all cards have exactly the contests in possibleContests
) : PopulationIF {
    var ncards = 0
    fun setNcards(ncards: Int): Population {
        this.ncards = ncards
        return this
    }

    override fun name() = name
    override fun id() = id
    override fun hasSingleCardStyle() = hasSingleCardStyle
    override fun ncards() = ncards
    override fun hasContest(contestId: Int) = possibleContests.contains(contestId)
    override fun contests() = possibleContests

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Population) return false

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


