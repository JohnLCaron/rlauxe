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

data class NamedCardStyle(
    val name: String,
    val contests: IntArray,
)

interface PopulationIF {
    fun name(): String
    fun id(): Int
    fun contests(): IntArray // any card may have any of these contests
    fun hasSingleCardStyle(): Boolean // aka hasStyle: if all cards have exactly the contests in possibleContests
    fun ncards(): Int
    fun hasContest(contestId: Int): Boolean
}

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
}

class CardManifest(val cards: CloseableIterable<AuditableCard>, val populations: List<PopulationIF>) {
    val popMap = populations.associateBy{ it.id() }
    fun population(populationId: Int) = popMap[populationId]
}

