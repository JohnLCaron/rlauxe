package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.util.CloseableIterator
import kotlin.collections.get
import kotlin.sequences.plus

// A generalization of Cvr, allowing votes to be null, eg for Polling or OneAudit pools.
// Also, cardStyle/population represents the sample population information.
//
// hasStyle -> cvrsAreComplete
// CLCA and cvrsAreComplete: dont need cardStyles
// CLCA and !cvrsAreComplete: always need cardStyles

// OA and cvrsAreComplete: cardStyles only for pooled data.
// OA and !cvrsAreComplete: always need cardStyles; cant use CvrsWithStylesToCards since poolId has been hijacked

// Polling: always need cardStyles

data class AuditableCard (
    val location: String, // info to find the card for a manual audit. Aka ballot identifier.
    val index: Int,  // index into the original, canonical list of cards
    val prn: Long,   // psuedo random number
    val phantom: Boolean,
    // val possibleContests: IntArray, // remove

    val votes: Map<Int, IntArray>?, // must have this and/or population
    val poolId: Int?,
    val cardStyle: String? = null, // hijacked for population name
    val population: PopulationIF? = null,
): CvrIF {

    init {
        if (population == null && cardStyle == null && votes == null && poolId == null) {
            // you could make this case mean "all". But maybe its better to be explicit ??
            throw RuntimeException("AuditableCard must have poolId, votes, cardStyle, or population")
        }
    }

    // Deprecated
    // if there are no votes, the IntArrays are all empty; looks like all undervotes
    fun cvr() : Cvr {
        /*val useVotes = if (votes != null) votes else {
            possibleContests.mapIndexed { idx, contestId ->
                Pair(contestId, votes?.get(idx) ?: intArrayOf())
            }.toMap()
        } */
        return Cvr(location, votes ?: emptyMap(), phantom, poolId)
    }

    override fun toString() = buildString {
        append("AuditableCard(desc='$location', index=$index, sampleNum=$prn, phantom=$phantom")
        if (poolId != null) append(", poolId=$poolId")
        if (cardStyle != null) append(", cardStyle=$cardStyle")
        if (population != null) append(", population=${population.name()}")
        appendLine(")")
        votes?.forEach { id, vote -> appendLine("   contest $id: ${vote.contentToString()}")}
    }

    override fun isPhantom() = phantom
    override fun location() = location
    override fun poolId() = poolId
    override fun votes(contestId: Int): IntArray? = votes?.get(contestId)

    override fun hasContest(contestId: Int): Boolean {
        return if (cardStyle == "all") true
            else if (population != null) population.hasContest(contestId)
            // else if (possibleContests.isNotEmpty()) possibleContests.contains(contestId)
            else if (votes != null) votes[contestId] != null
            else false
    }

    // TODO deprecated? Dont have a list of "all"
    fun contests(): IntArray {
        return if (population != null) population.contests()
            // else if (possibleContests.isNotEmpty()) possibleContests
            else if (votes != null) votes.keys.toList().sorted().toIntArray()
            else intArrayOf()
    }

    // better if every card has a population
    fun exactContests(): Boolean {
        return if (population != null) population.hasSingleCardStyle()
        else if (cardStyle == "all") false
        else true // else config.cvrsHaveUndervotes

    }

    // Let 1candidate(bi) = 1 if ballot i has a mark for candidate, and 0 if not; SHANGRLA section 2, page 4
    override fun hasMarkFor(contestId: Int, candidateId: Int): Int {
        val contestVotes = votes?.get(contestId)
        return if (contestVotes == null) 0
        else if (contestVotes.contains(candidateId)) 1 else 0
    }

    // Kotlin data class doesnt handle IntArray and List<IntArray> correctly

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuditableCard) return false

        if (index != other.index) return false
        if (prn != other.prn) return false
        if (phantom != other.phantom) return false
        if (poolId != other.poolId) return false
        if (location != other.location) return false
        // if (!possibleContests.contentEquals(other.possibleContests)) return false
        if (cardStyle != other.cardStyle) return false
        if (population != other.population) return false

        if ((votes == null) != (other.votes == null)) return false

        if (votes != null) {
            for ((contestId, candidates) in votes) {
                val otherCands = other.votes!![contestId]
                if (!candidates.contentEquals(otherCands)) return false
            }
        }

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + prn.hashCode()
        result = 31 * result + phantom.hashCode()
        result = 31 * result + (poolId ?: 0)
        result = 31 * result + location.hashCode()
        // result = 31 * result + possibleContests.contentHashCode()
        result = 31 * result + (cardStyle?.hashCode() ?: 0)
        result = 31 * result + (population?.hashCode() ?: 0)
        votes?.forEach { (contestId, candidates) -> result = 31 * result + contestId.hashCode() + candidates.contentHashCode() }
        return result
    }

    companion object {
        fun fromCvr(cvr: Cvr, index: Int, prn: Long): AuditableCard {
            // val sortedVotes = cvr.votes.toSortedMap()
            // val contests = sortedVotes.keys.toList()
            return AuditableCard(cvr.id, index, prn=prn, cvr.phantom, cvr.votes, cvr.poolId)
        }
        fun fromCvrs(cvrs: List<Cvr>): List<AuditableCard> {
            // val sortedVotes = cvr.votes.toSortedMap()
            // val contests = sortedVotes.keys.toList()
            return cvrs.mapIndexed { idx, cvr -> AuditableCard.fromCvr(cvr, idx, 0) }
        }
    }
}

// put cards into canonical form
class CvrsWithPopulationsToCardManifest(
    val type: AuditType,
    // val cvrsAreComplete: Boolean,       // TODO cvrsAreComplete == false means cardStyles != null and poolId != null;
    // unless theres some default behavior, esp with poolId; maybe "all"
    val cvrs: CloseableIterator<Cvr>,
    val phantomCvrs : List<Cvr>?,
    populations: List<PopulationIF>?,
): CloseableIterator<AuditableCard> {

    val popMap = populations?.associateBy{ it.id() }
    val allCvrs: Iterator<Cvr>
    var cardIndex = 0 // 0 based index

    init {
        allCvrs = if (phantomCvrs == null) {
            cvrs
        } else {
            val cardSeq = cvrs.iterator().asSequence()
            val phantomSeq = phantomCvrs.asSequence()
            (cardSeq + phantomSeq).iterator()
        }
    }

    override fun hasNext() = allCvrs.hasNext()

    override fun next(): AuditableCard {
        val org = allCvrs.next()
        val pop = if (popMap == null) null else popMap[org.poolId] // hijack poolId
        val hasCvr = type.isClca() || (type.isOA() && org.poolId == null)
        val votes = if (hasCvr) org.votes else null

        // TODO make user get this right?
        // if you havent specified a population or votes, then the population = all contests
        val cardStyle = if (votes == null && pop == null) "all" else pop?.name()

        return AuditableCard(org.id, cardIndex++, 0, phantom=org.phantom,
            // intArrayOf(),
            votes,
            org.poolId,
            cardStyle = cardStyle,
            population = pop,
        )
    }

    override fun close() = cvrs.close()
}

class CardsWithPopulationsToCardManifest(
    val type: AuditType,
    val cards: CloseableIterator<AuditableCard>,
    populations: List<PopulationIF>?,
): CloseableIterator<AuditableCard> {

    val popMap = populations?.associateBy{ it.name() }

    override fun hasNext() = cards.hasNext()

    override fun next(): AuditableCard {
        val org = cards.next()
        val pop = if (popMap == null) null else popMap[org.cardStyle]

        // TODO make user get this right?
        // if you havent specified a cardStyle, population or votes, then the population = all contests
        // val cardStyle = if (org.cardStyle == null && org.votes == null && pop == null) "all" else org.cardStyle

        return org.copy(population = pop)
    }

    override fun close() = cards.close()
}