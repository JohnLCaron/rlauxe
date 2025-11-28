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

interface CardIF {
    fun hasContest(contestId: Int): Boolean // "is in P_c".
    fun location(): String
    fun isPhantom(): Boolean
    fun poolId(): Int?

    fun hasMarkFor(contestId: Int, candidateId:Int): Int
    fun hasOneVoteFor(contestId: Int, candidates: List<Int>): Boolean
    fun rankedChoices(contestId: Int): IntArray?
    fun votes(contestId: Int): IntArray? // same?
}

data class AuditableCard (
    val location: String, // info to find the card for a manual audit. Aka ballot identifier.
    val index: Int,  // index into the original, canonical list of cards
    val prn: Long,   // psuedo random number
    val phantom: Boolean,
    val possibleContests: IntArray, // list of contests that might be on the ballot.
                                    // card does not know cvrsAreComplete nor the cardPool. So always fill out possibleContests when cvrsAreComplete = false
                                    // cvrsAreComplete && votes != null means can use votes.
                                    // polling and oa pools need to fill out possibleContests always. unless you want to allow empty = all?
    val votes: Map<Int, IntArray>?, // for CLCA or OneAudit, a map of contest -> the candidate ids voted; must include undervotes; missing for pooled data or polling audits
                                                                                // when IRV, ranked first to last
    val poolId: Int?, // for OneAudit, or for setting style from CVR (so tolerate non-OA poolId)
    val cardStyle: String? = null, // set style in a way that doesnt interfere with oneaudit pool. At the moment, informational.
): CardIF {

    init {
        if (!phantom && (possibleContests.isEmpty() && votes == null)) {
            // you could make this case mean "all". But maybe its better to be explicit ??
            throw RuntimeException("AuditableCard must have votes or possibleContests")
        }
    }

    // if there are no votes, the IntArrays are all empty; looks like all undervotes
    fun cvr() : Cvr {
        val useVotes = if (votes != null) votes else {
            possibleContests.mapIndexed { idx, contestId ->
                Pair(contestId, votes?.get(idx) ?: intArrayOf()) // TODO WTF ??
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
    override fun rankedChoices(contestId: Int): IntArray? = votes?.get(contestId)

    override fun hasContest(contestId: Int): Boolean {
        // if (possibleContests.isEmpty() && votes == null) return true // TODO seems to mean empty == "all"
        return contests().contains(contestId)
    }

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

    // Is there exactly one vote in the contest among the given candidates?
    override fun hasOneVoteFor(contestId: Int, candidates: List<Int>): Boolean {
        val contestVotes = votes?.get(contestId) ?: return false
        val totalVotes = contestVotes.count { candidates.contains(it) }
        return (totalVotes == 1)
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
        votes?.forEach { (contestId, candidates) -> result = 31 * result + contestId.hashCode() + candidates.contentHashCode() }
        return result
    }

    companion object {
        fun fromCvr(cvr: Cvr, index: Int, sampleNum: Long): AuditableCard {
            val sortedVotes = cvr.votes.toSortedMap()
            val contests = sortedVotes.keys.toList()
            return AuditableCard(cvr.id, index, sampleNum, cvr.phantom, contests.toIntArray(), cvr.votes, cvr.poolId)
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
    fun id(): Int // TODO is this the poolId ??
    fun contests() : IntArray
    fun hasContest(contestId: Int): Boolean
}

// essentially, CardStyle factors out the contestIds, which the CardLocation references, so its a form of normalization
data class CardStyle(
    val name: String,
    val id: Int,
    val contestNames: List<String>, // TODO remove?
    val contestIds: List<Int>,
): CardStyleIF {
    // used by MultiContestTestData
    var ncards = 0
    var poolId: Int? = null

    override fun name() = name
    override fun id() = id
    override fun hasContest(contestId: Int) = contestIds.contains(contestId)
    override fun contests() = contestIds.toIntArray()

    override fun toString() = buildString {
        append("CardStyle('$name' ($id), contestIds=$contestIds")
    }

    companion object {
        fun make(styleId: Int, contestNames: List<String>, contestIds: List<Int>, ncards: Int?, poolId: Int? ): CardStyle {
            val cs = CardStyle("style$styleId", styleId, contestNames, contestIds)
            if (ncards != null) cs.ncards = ncards
            if (poolId != null) cs.poolId = poolId
            return cs
        }
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

    val poolMap = styles?.associateBy{ it.id() }
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
            style?.name())
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
            style?.name(),
        )
    }

    override fun close() = cards.close()
}

// TODO maybe go back to CardManifest include the pools ??
fun makeCardManifest(converter: CardsWithStylesToCardManifest): List<AuditableCard> {
    val cardManifest = mutableListOf<AuditableCard>()
    converter.forEach { cardManifest.add(it) }
    return cardManifest
}


