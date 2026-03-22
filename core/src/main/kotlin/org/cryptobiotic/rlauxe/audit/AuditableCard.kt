package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import kotlin.collections.get
import kotlin.sequences.plus

// The information we have on each physical card in the audit; the complete set is the CardManifest.

// TODO could you write cards independent of what kind of audit?
//  OneAudit uses poolId to indicate cvr vs pool card; uses cardPool to specify the possibleContests() of pooled card.
//  Polling cards never have CVRs, always want poolId or batch to minimize dilution
//  I think CLCA doesnt care about poolId, dont use batches unless cvrsContainUndervotes = false. See CreateSfElection.
//  problem is that merging batches is done when reading; and must be uniform, as theres no CreateElection to process specially.
//  We should be able to write a single MVR, but Cards have to be different.

data class AuditableCard (
    val location: String, // enough info to find the card for a manual audit.
    val index: Int,  // index into the original, canonical list of cards
    val prn: Long,   // psuedo random number
    val phantom: Boolean,

    val votes: Map<Int, IntArray>?,   // CVRs and phantoms
    val poolId: Int?,                 // must be set if its from a CardPool
    val batchName: String,            // batch name.
    val batch: BatchIF? = null,       // batch reference. CLCA dont need unless cvrsContainUndervotes = false
): CvrIF {

    fun cvr() : Cvr {
        return Cvr(location, votes ?: emptyMap(), phantom, poolId)
    }

    override fun toString() = buildString {
        append("AuditableCard(location='$location', index=$index, prn=$prn, phantom=$phantom")
        if (poolId != null) append(", poolId=$poolId")
        append(", batchName='$batchName'")
        if (batch != null) append(", has batch contests=${batch.possibleContests().contentToString()}")
        append(")")
        if (votes != null) {
            appendLine()
            append("  votes: ")
            votes.forEach { id, vote -> append("$id:${vote.contentToString()}, ") }
        }
    }

    override fun isPhantom() = phantom
    override fun location() = location
    override fun poolId() = poolId
    override fun votes(contestId: Int): IntArray? = votes?.get(contestId)

    override fun hasContest(contestId: Int): Boolean {
        return if (batch != null) batch.hasContest(contestId)
            else if (votes != null) votes[contestId] != null // assumes cvrsContainUndervotes, use batch if not.
            else false // wtf ??
    }

    fun contests(): IntArray {
        return if (batch != null) batch.possibleContests().toList().sorted().toIntArray()
            else if (votes != null) votes.keys.toList().sorted().toIntArray()
            else {
                logger.warn { "AuditableCard has no batch nor votes: $this"}
                intArrayOf() // TODO makes no sense, a card with no batch or votes, wtf ?
            }
        }

    fun hasStyle(): Boolean {
        return if (batch != null) batch.hasSingleCardStyle()
        else (batchName != "cvrsIncomplete") // or set a damn batch already
    }

    //// CvrIF
    // Let 1candidate(bi) = 1 if ballot i has a mark for candidate, and 0 if not; SHANGRLA section 2, page 4
    override fun hasMarkFor(contestId: Int, candidateId: Int): Int {
        val contestVotes = votes?.get(contestId)
        return if (contestVotes == null) 0
        else if (contestVotes.contains(candidateId)) 1 else 0
    }

    //// Kotlin data class doesnt handle IntArray and List<IntArray> correctly
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuditableCard) return false

        if (index != other.index) return false
        if (prn != other.prn) return false
        if (phantom != other.phantom) return false
        if (poolId != other.poolId) return false
        if (location != other.location) return false
        // if (!possibleContests.contentEquals(other.possibleContests)) return false
        if (batchName != other.batchName) return false
        if (batch != other.batch) return false

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
        result = 31 * result + batchName.hashCode()
        result = 31 * result + (batch?.hashCode() ?: 0)
        votes?.forEach { (contestId, candidates) -> result = 31 * result + contestId.hashCode() + candidates.contentHashCode() }
        return result
    }

    companion object {
        fun fromCvr(cvr: Cvr, index: Int, prn: Long): AuditableCard {
            return AuditableCard(cvr.id, index, prn=prn, cvr.phantom, cvr.votes, cvr.poolId, batchName="cvr") // TODO
        }
        fun fromCvrs(cvrs: List<Cvr>): List<AuditableCard> {
            return cvrs.mapIndexed { idx, cvr -> AuditableCard.fromCvr(cvr, idx, 0) }
        }

        private val logger = KotlinLogging.logger("AuditableCard")
    }
}

class MergeBatchIntoCards(
    val cards: List<AuditableCard>,
    batches: List<BatchIF>,
): CloseableIterator<AuditableCard> {
    val cardsIter = cards.iterator()
    val batchMap = batches.associateBy{ it.name() }

    override fun hasNext() = cardsIter.hasNext()

    // merges the populations into the cards
    override fun next(): AuditableCard {
        val org = cardsIter.next()
        val pop = batchMap[org.batchName]
        return org.copy(batch = pop)
    }

    override fun close() {}
}

////////////////////////////////////////////

// Add batch reference when reading in
class MergeBatchesIntoCards(
    val cards: CloseableIterable<AuditableCard>,
    val batches: List<BatchIF>,  // TODO try not optional
): CloseableIterable<AuditableCard> {

    override fun iterator(): CloseableIterator<AuditableCard> = MergeBatchesIterator(cards.iterator(), batches)

    private class MergeBatchesIterator(
        val cardsIter: CloseableIterator<AuditableCard>,
        batches: List<BatchIF>,
    ): CloseableIterator<AuditableCard> {
        val batchMap = batches.associateBy { it.name() }

        override fun hasNext() = cardsIter.hasNext()

        // merges the batch reference into the cards; could be null
        override fun next(): AuditableCard {
            val org: AuditableCard = cardsIter.next()
            val pop = batchMap[org.batchName]
            return org.copy(batch = pop)
        }

        override fun close() = cardsIter.close()
    }

}

////////////////////////////////////////////
// used in CreateElectionIF
// if you pass in batches, this adds batchName and batch to AuditableCard, keyed on poolId.
// when you serialize, it only saves the batchName.
// then we rehydrate (MergePopulationsFromIterable) and put the batch reference back in.

class CvrsToCardManifest(
    val type: AuditType,
    val cvrs: CloseableIterator<Cvr>,
    val phantomCvrs : List<Cvr>?,
    batches: List<BatchIF>?, // if you allow empty, might as well allow null
): CloseableIterator<AuditableCard> {

    val batchMap = batches?.associateBy{ it.id() } ?: emptyMap()
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
        val batch = batchMap[org.poolId] // hijack poolId
        val hasCvr = type.isClca() || (type.isOA() && org.poolId == null)
        val votes = if (hasCvr || org.isPhantom()) org.votes else null  // removes votes for pooled data

        return AuditableCard(org.id, cardIndex++, 0, phantom=org.phantom,
            votes = votes,
            poolId = if (type.isClca()) null else org.poolId,
            batchName = batch?.name() ?: "cvr",
            batch, // not needed if you are just going to serialize; but does no harm
        )
    }

    override fun close() = cvrs.close()
}

// used only in testing - could move to testFixtures
// was CvrsWithPopulationsToCardManifest
class CvrsAndBatchesToCards(
    val type: AuditType,
    val cvrs: CloseableIterator<Cvr>,  // hmmm fishy
    val phantomCvrs : List<Cvr>?,
    batches: List<BatchIF>?,
): CloseableIterator<AuditableCard> {

    val popMap = batches?.associateBy{ it.id() }
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
        val votes = if (hasCvr) org.votes else null  // removes votes for pooled data

        return AuditableCard(org.id, cardIndex++, 0, phantom=org.phantom,
            votes = votes,
            poolId = if (type.isClca()) null else org.poolId,
            batchName = pop?.name() ?: "cvr", // TODO ??
            batch = pop,
        )
    }

    override fun close() = cvrs.close()
}