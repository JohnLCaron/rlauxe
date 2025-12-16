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
    fun exactContests(): Boolean
    fun ncards(): Int
    fun hasContest(contestId: Int): Boolean
    fun contests(): IntArray
}

data class Population(
    val name: String,
    val id: Int,
    val possibleContests: IntArray, // the list of possible contests.
    val exactContests: Boolean,     // aka hasStyle: if all cards have exactly the contests in possibleContests
) : PopulationIF {
    var ncards = 0

    override fun name() = name
    override fun id() = id
    override fun exactContests() = exactContests
    override fun ncards() = ncards
    override fun hasContest(contestId: Int) = possibleContests.contains(contestId)
    override fun contests() = possibleContests
}

data class AuditCard(
    val location: String, // info to find the card for a manual audit. Aka ballot identifier.
    val index: Int,  // index into the original, canonical list of cards
    val prn: Long,   // psuedo random number
    val phantom: Boolean,
    val poolId: Int?, // if not null, this is in a OneAuditPool

    // must have at least one of:
    val votes: Map<Int, IntArray>?,
    val population: PopulationIF?, // not needed if hasStyle ?
): CvrIF {
    override fun location() = location
    override fun isPhantom() = phantom
    override fun poolId() = poolId

    override fun votes(contestId: Int) = votes?.get(contestId)

    override fun hasMarkFor(contestId: Int, candidateId: Int): Int {
        val contestVotes = votes?.get(contestId)
        return if (contestVotes == null) 0
        else if (contestVotes.contains(candidateId)) 1 else 0
    }

    override fun hasContest(contestId: Int): Boolean {
        return if (population != null) population.hasContest(contestId)
        else if (votes != null) votes[contestId] != null
        else false
    }
}

data class Cvr2 (
    val location: String, // ballot identifier
    val votes: Map<Int, IntArray>, // contest -> list of candidates voted for; for IRV, ranked first to last
    val phantom: Boolean = false, // only on Card ??
    val poolId: Int? = null,
)

class CardManifest(val cards: CloseableIterable<AuditableCard>, val populations: List<PopulationIF>) {
    val popMap = populations.associateBy{ it.id() }
    fun cards() = cards
    fun population(populationId: Int) = popMap[populationId]
}

