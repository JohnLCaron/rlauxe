package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import kotlin.collections.get
import kotlin.sequences.plus

// A generalization of Cvr, allowing votes to be null, eg for Polling or OneAudit pools.
// Also, possibleContests/cardStyle represents the sample population information.
//
// hasStyle -> cvrsAreComplete
// CLCA and cvrsAreComplete: dont need cardStyles
// CLCA and !cvrsAreComplete: always need cardStyles

// OA and cvrsAreComplete: cardStyles only for pooled data.
// OA and !cvrsAreComplete: always need cardStyles; cant use CvrsWithStylesToCards since poolId has been hijacked

// Polling: always need cardStyles

interface CvrIF {
    fun hasContest(contestId: Int): Boolean // "is in P_c".
    fun location(): String
    fun isPhantom(): Boolean
    fun poolId(): Int?

    fun votes(contestId: Int): IntArray?
    fun hasMarkFor(contestId: Int, candidateId:Int): Int

    // fun hasOneVoteFor(contestId: Int, candidates: List<Int>): Boolean
    //     // val cands = cvr.votes(contest.id)
    //    //   val usew2 =  (cands != null && cands.size == 1)
}

data class AuditableCard (
    val location: String, // info to find the card for a manual audit. Aka ballot identifier.
    val index: Int,  // index into the original, canonical list of cards
    val prn: Long,   // psuedo random number
    val phantom: Boolean,
    val possibleContests: IntArray, // remove

    val votes: Map<Int, IntArray>?, // must have this and/or population
    val poolId: Int?,
    val cardStyle: String? = null, // remove
    val population: PopulationIF? = null, // not needed if hasStyle ?
): CvrIF {

    init {
        if (!phantom && (possibleContests.isEmpty() && population == null && cardStyle == null && votes == null)) {
            // you could make this case mean "all". But maybe its better to be explicit ??
            throw RuntimeException("AuditableCard must have votes, possibleContests, or population")
        }
        if (possibleContests.isNotEmpty() && votes != null) {
            votes.keys.forEach { id ->
                if (!possibleContests.contains(id))
                    throw RuntimeException("possible contests must contain vote contest ${id}")
            }
        }
    }

    // if there are no votes, the IntArrays are all empty; looks like all undervotes
    fun cvr() : Cvr {
        val useVotes = if (votes != null) votes else {
            possibleContests.mapIndexed { idx, contestId ->
                Pair(contestId, votes?.get(idx) ?: intArrayOf())
            }.toMap()
        }
        return Cvr(location, useVotes, phantom, poolId)
    }

    override fun toString() = buildString {
        append("AuditableCard(desc='$location', index=$index, sampleNum=$prn, phantom=$phantom, possibleContests=${possibleContests.contentToString()}")
        if (poolId != null) append(", poolId=$poolId")
        if (cardStyle != null) append(", cardStyle=$cardStyle")
        appendLine(")")
        votes?.forEach { id, vote -> appendLine("   contest $id: ${vote.contentToString()}")}
    }

    override fun isPhantom() = phantom
    override fun location() = location
    override fun poolId() = poolId
    override fun votes(contestId: Int): IntArray? = votes?.get(contestId)

    override fun hasContest(contestId: Int): Boolean {
        return if (population != null) population.hasContest(contestId)
            else if (possibleContests.isNotEmpty()) possibleContests.contains(contestId)
            else if (votes != null) votes[contestId] != null
            else false
    }

    // TODO deprecated?
    fun contests(): IntArray {
        return if (possibleContests.isNotEmpty()) possibleContests
            else if (votes != null) votes.keys.toList().sorted().toIntArray()
            else intArrayOf()
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
        if (!possibleContests.contentEquals(other.possibleContests)) return false
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
        result = 31 * result + possibleContests.contentHashCode()
        result = 31 * result + (cardStyle?.hashCode() ?: 0)
        result = 31 * result + (population?.hashCode() ?: 0)
        votes?.forEach { (contestId, candidates) -> result = 31 * result + contestId.hashCode() + candidates.contentHashCode() }
        return result
    }

    companion object {
        fun fromCvr(cvr: Cvr, index: Int, prn: Long): AuditableCard {
            val sortedVotes = cvr.votes.toSortedMap()
            val contests = sortedVotes.keys.toList()
            return AuditableCard(cvr.id, index, prn=prn, cvr.phantom, contests.toIntArray(), cvr.votes, cvr.poolId)
        }
    }
}

// Can derive the Find and CVR committments of Verifiable paper, p.8 and section 4.7 ("the prover’s initial declaration about the cards")
// Using the sorted cards, can verify that the sampled cards are the correct ones.
data class CardLocationManifest(
    val cardLocations: CloseableIterable<AuditableCard>,
    val cardStyles: List<CardStyle>
)

interface CardStyleIF {
    fun name(): String
    fun contests() : IntArray
    fun hasContest(contestId: Int): Boolean
    fun poolId(): Int?
}

// essentially, CardStyle factors out the contestIds, which the CardLocation references, so its a form of normalization
data class CardStyle(
    val name: String,
    val contestIds: List<Int>,
    val poolId: Int?,
): CardStyleIF {
    // used by MultiContestTestData
    var ncards = 0

    fun setNcards(ncards:Int): CardStyle {
        this.ncards = ncards
        return this
    }

    override fun name() = name
    override fun poolId() = poolId
    override fun hasContest(contestId: Int) = contestIds.contains(contestId)
    override fun contests() = contestIds.toIntArray()

    override fun toString() = buildString {
        append("CardStyle('$name' contestIds=$contestIds poolId=$poolId")
    }
}

/////////////////////////////////////////////////////////////////////////////////////
// CLCA or OneAudit or Polling

// put cards into canonical form
class CvrsWithStylesToCardManifest(
    val type: AuditType,
    val cvrsAreComplete: Boolean,       // TODO cvrsAreComplete == false means cardStyles != null and poolId != null;
                                        // unless theres some default behavior, esp with poolId; maybe "all"
    val cvrs: CloseableIterator<Cvr>,
    val phantomCvrs : List<Cvr>?,
    styles: List<CardStyleIF>?,
): CloseableIterator<AuditableCard> {

    val poolMap = styles?.associateBy{ it.poolId() }
    val allCvrs: Iterator<Cvr>
    var cardIndex = 1

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
        val style = if (poolMap == null) null else poolMap[org.poolId] // hijack poolId
        val hasCvr = type.isClca() || (type.isOA() && style == null)
        val contests = when {
            (hasCvr && cvrsAreComplete) -> null
            (style != null) -> style.contests()
            cvrsAreComplete -> org.contests()
            else -> null
        }
        val votes = if (hasCvr) org.votes else null

        return AuditableCard(org.id, cardIndex++, 0, phantom=org.phantom,
            contests ?: intArrayOf(),
            votes,
            org.poolId,
            null, //style?.name())
        )
    }

    override fun close() = cvrs.close()
}

// put cards into canonical form
class CardsWithStylesToCardManifest(
    val type: AuditType,
    val cvrsAreComplete: Boolean,
    val cards: CloseableIterator<AuditableCard>,
    phantomCards : List<AuditableCard>?,
    styles: List<CardStyleIF>?,
): CloseableIterator<AuditableCard> {

    val poolMap = styles?.associateBy{ it.name() }
    val allCards: Iterator<AuditableCard>
    var cardIndex = 1

    init {
        allCards = if (phantomCards == null) {
            cards
        } else {
            val cardSeq = cards.iterator().asSequence()
            val phantomSeq = phantomCards.asSequence()
            (cardSeq + phantomSeq).iterator()
        }
    }

    override fun hasNext() = allCards.hasNext()

    override fun next(): AuditableCard {
        val org = allCards.next()
        val style = if (poolMap == null) null else poolMap[org.cardStyle]
        val hasCvr = type.isClca() || (type.isOA() && style == null)
        val contests = when {
            (hasCvr && cvrsAreComplete) -> null
            (style != null) -> style.contests()
            cvrsAreComplete -> org.contests()
            else -> null
        }
        val votes = if (hasCvr) org.votes else null

        return AuditableCard(org.location, cardIndex++, 0, phantom=org.phantom,
            contests ?: intArrayOf(),
            votes,
            org.poolId,
            null, // style?.name(),
        )
    }

    override fun close() = cards.close()
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
    var cardIndex = 1

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
        if (pop != null)
            print("")

        // data class AuditableCard (
        //    val location: String, // info to find the card for a manual audit. Aka ballot identifier.
        //    val index: Int,  // index into the original, canonical list of cards
        //    val prn: Long,   // psuedo random number
        //    val phantom: Boolean,
        //    val possibleContests: IntArray, // remove
        //
        //    val votes: Map<Int, IntArray>?, // must have this and/or population
        //
        //    val poolId: Int?,
        //
        //    val cardStyle: String? = null, // remove
        //    val population: PopulationIF? = null, // not needed if hasStyle ?
        //)
        return AuditableCard(org.id, cardIndex++, 0, phantom=org.phantom,
            intArrayOf(),
            votes,
            org.poolId,
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

    val popMap = populations?.associateBy{ "P${it.id()}" }

    override fun hasNext() = cards.hasNext()

    override fun next(): AuditableCard {
        val org = cards.next()
        val pop = if (popMap == null) null else popMap[org.cardStyle]
        return org.copy(population = pop)
    }

    override fun close() = cards.close()
}



