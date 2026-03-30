package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Prng
import kotlin.collections.get
import kotlin.sequences.plus

// The information we have on each physical card in the audit; the complete set is the CardManifest.

// could you write cards independent of what kind of audit?
//  OneAudit uses poolId to indicate cvr vs pool card; uses cardPool to specify the possibleContests() of pooled card.
//  Polling cards never have CVRs, always want poolId or batch to minimize dilution
//  I think CLCA doesnt care about poolId, dont use batches unless cvrsContainUndervotes = false. See CreateSfElection.
//  problem is that merging batches is done when reading; and must be uniform, as theres no CreateElection to process specially.
//  We should be able to write a single MVR, but Cards have to be different.

// maybe AuditableCard must have a batch, but intermediate states could use CardBuilder ??
// If batch exists, dont need poolId
// maybe you write CardBuilder and read AuditableCard?

//// change AuditableCardCsv to CardNoBatchCsv
// cardManifest.csv     // AuditableCardCsv, may be zipped
// sortedCards.csv      // AuditableCardCsv, sorted by prn, may be zipped  TODO change to sortedCardManifest

// private/sortedMvrs.csv       // AuditableCardCsv, sorted by prn, matches sortedCards.csv, may be zipped
// private/unsortedMvrs.csv     // AuditableCardCsv (optional)

// roundX/sampleCardsX.csv     // AuditableCardCsv, complete sorted cards used for this round; matches samplePrnsX.csv
// roundX/sampleMvrsX.csv      // AuditableCardCsv, complete sorted mvrs used for this round; matches samplePrnsX.csv

// does it help if Mvrs dont have to have batch set ?? or even batchName?

data class AuditableCard (
    val location: String, // enough info to find the card for a manual audit.
    val index: Int,  // index into the original, canonical list of cards
    val prn: Long,   // psuedo random number
    val phantom: Boolean,
    val votes: Map<Int, IntArray>?,   // CVRs and phantoms
    val batch: BatchIF,       // batch reference. CLCA dont need unless cvrsContainUndervotes = false
): CvrIF, CardIF {

    init {
        if (Batch.useVotes(batch.name()) && votes == null) {
            throw RuntimeException("batch '${batch.name()}' must have non-null votes")
        }
    }

    constructor(card: CardWithBatchName, batch: BatchIF): this(card.location, card.index, card.prn, card.phantom, card.votes, batch)
    constructor(cvr: Cvr, index: Int, prn: Long): this(cvr.id, index, prn, cvr.phantom, cvr.votes, batch = Batch.fromCvrBatch)

    fun toCvr() = Cvr(location, votes!!, phantom, poolId()) // TODO can we get rid of?

    // "may have contest". Cvr hasContest does not allow missing, ie is not the same as "may have contest"
    override fun hasContest(contestId: Int): Boolean {
        return when {
            Batch.useVotes(batch.name()) -> votes!![contestId] != null // assumes cvrsContainUndervotes, use batch if not.
            else -> batch.hasContest(contestId)
        }
    }

    override fun location() = location
    override fun index() = index
    override fun prn() = prn
    override fun isPhantom() = phantom
    override fun votes() = votes
    override fun votes(contestId: Int): IntArray? = votes?.get(contestId)
    override fun poolId(): Int? = if (batch is CardPoolIF) batch.id() else null // TODO check
    override fun batchName() = batch.name()

    override fun hasMarkFor(contestId: Int, candidateId: Int): Int {
        val contestVotes = votes?.get(contestId)
        return if (contestVotes == null) 0
        else if (contestVotes.contains(candidateId)) 1 else 0
    }

    // return sorted
    fun possibleContests() : IntArray {
        return when {
            Batch.useVotes(batch.name()) -> votes!!.keys.toList().sorted().toIntArray() // assumes cvrsContainUndervotes, use batch if not.
            else -> batch.possibleContests().toList().sorted().toIntArray()
        }
    }

    fun hasStyle() = batch.hasSingleCardStyle()

    //// Kotlin data class doesnt handle IntArray correctly
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuditableCard) return false

        if (index != other.index) return false
        if (prn != other.prn) return false
        if (phantom != other.phantom) return false
        if (location != other.location) return false
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
        result = 31 * result + location.hashCode()
        result = 31 * result + batch.hashCode()
        votes?.forEach { (contestId, candidates) -> result = 31 * result + contestId.hashCode() + candidates.contentHashCode() }
        return result
    }

    override fun toString() = buildString {
        append("AuditableCard(location='$location', index=$index, prn=$prn, phantom=$phantom")
        append(", has batch ${batch.name()} ${batch.id()} possibleContests=${batch.possibleContests().contentToString()} singleStyle=${batch.hasSingleCardStyle()}")
        append(")")
        if (votes != null) {
            appendLine()
            append("  votes: ")
            votes.forEach { id, vote -> append("$id:${vote.contentToString()}, ") }
        }
    }
}

// lets us serialize either CardNoStyle or AuditableCard
interface CardIF {
    fun location(): String // enough info to find the card for a manual audit.
    fun index(): Int  // index into the original, canonical list of cards
    fun prn(): Long   // psuedo random number
    fun isPhantom(): Boolean

    fun votes(): Map<Int, IntArray>?   // CVRs and phantoms
    fun poolId(): Int?                 // must be set if its from a CardPool  TODO verify batch name, poolId
    fun batchName(): String            // batch name: "fromCvr" if no batch and its from a CVR (then votes is non null)
}

// for serialization and ElectionBuilder
data class CardWithBatchName (
    val location: String, // enough info to find the card for a manual audit.
    val index: Int,  // index into the original, canonical list of cards
    val prn: Long,   // psuedo random number
    val phantom: Boolean,

    val votes: Map<Int, IntArray>?,   // CVRs and phantoms
    val poolId: Int?,                 // must be set if its from a CardPool  TODO verify batch name, poolId
    val batchName: String,            // batch name: "fromCvr" if no batch and its from a CVR (then votes is non null)
): CardIF {

    constructor(card: AuditableCard): this(card.location, card.index, card.prn, card.phantom, card.votes, card.poolId(), card.batchName())

    override fun location() = location
    override fun index() = index
    override fun prn() = prn
    override fun isPhantom() = phantom
    override fun votes() = votes
    override fun poolId() = poolId
    override fun batchName() = batchName

    fun toCvr() = Cvr(location, votes!!, phantom, poolId) // TODO get rid of

    //// Kotlin data class doesnt handle IntArray and List<IntArray> correctly
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CardWithBatchName) return false

        if (index != other.index) return false
        if (prn != other.prn) return false
        if (phantom != other.phantom) return false
        if (poolId != other.poolId) return false
        if (location != other.location) return false
        if (batchName != other.batchName) return false

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
        votes?.forEach { (contestId, candidates) -> result = 31 * result + contestId.hashCode() + candidates.contentHashCode() }
        return result
    }

    override fun toString() = buildString {
        append("CardWithBatchName(location='$location', index=$index, prn=$prn, phantom=$phantom")
        if (poolId != null) append(", poolId=$poolId")
        append(", batchName='$batchName'")
        append(")")
        if (votes != null) {
            appendLine()
            append("  votes: ")
            votes.forEach { id, vote -> append("$id:${vote.contentToString()}, ") }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger("CardWithBatchName")
    }
}

//// Add batch reference when reading in CardNoBatch

fun merge(cards: CloseableIterator<CardWithBatchName>, batches: List<BatchIF>?) : CloseableIterator<AuditableCard> {
    return MergeBatchesIntoCardManifestIterator(cards, batches ?: emptyList())
}

fun merge(cards: List<CardWithBatchName>, batches: List<BatchIF>?) : List<AuditableCard> {
    val iter = MergeBatchesIntoCardManifestIterator(Closer(cards.iterator()), batches ?: emptyList())
    val result = mutableListOf<AuditableCard>()
    while (iter.hasNext()) result.add(iter.next())
    return result
}

// read CardWithBatchName, add batch, output AuditableCard as Iterator
class MergeBatchesIntoCardManifestIterator(
    val cardsIter: CloseableIterator<CardWithBatchName>,
    batches: List<BatchIF>,
): CloseableIterator<AuditableCard> {
    val batchMap = batches.associateBy{ it.name() }

    override fun hasNext() = cardsIter.hasNext()

    // batchName must be in batchMap
    override fun next(): AuditableCard {
        val org = cardsIter.next()
        val batchy = batchMap[org.batchName]
        val batch = when {
            org.batchName == Batch.phantoms -> Batch.phantomBatch
            org.batchName == Batch.fromCvr -> Batch.fromCvrBatch
            batchy != null -> batchy
            else ->
                throw RuntimeException()
        }
        return AuditableCard(org, batch)
    }

    override fun close() { cardsIter.close() }
}

// read CardWithBatchName, add batch, output AuditableCard as Iterable
class MergeBatchesIntoCardManifestIterable(
    val cards: CloseableIterable<CardWithBatchName>,
    val batches: List<BatchIF>,
): CloseableIterable<AuditableCard> {

    override fun iterator(): CloseableIterator<AuditableCard> = MergeBatchesIterator(cards.iterator(), batches)

    private class MergeBatchesIterator(
        val cardsIter: CloseableIterator<CardWithBatchName>,
        batches: List<BatchIF>,
    ): CloseableIterator<AuditableCard> {
        val batchMap = batches.associateBy { it.name() }

        override fun hasNext() = cardsIter.hasNext()

        // batchName must be in batchMap
        override fun next(): AuditableCard {
            val org: CardWithBatchName = cardsIter.next()
            val batchy = batchMap[org.batchName]

            val batch = when {
                org.batchName == Batch.phantoms -> Batch.phantomBatch
                org.batchName == Batch.fromCvr -> Batch.fromCvrBatch
                batchy != null -> batchy
                else ->
                    throw RuntimeException()
            }
            return AuditableCard(org, batch)
        }
        override fun close() = cardsIter.close()
    }
}

////////////////////////////////////////////
// used in CreateElectionIF
// relies on cvrs having poolIds that match the batch.id()
// when it has a pool, use the pool name for the batchName
class CvrsToCardsWithBatchNameIterator(
    val type: AuditType,
    val cvrs: CloseableIterator<Cvr>,  // hmmm fishy
    val phantomCvrs : List<Cvr>?,
    batches: List<BatchIF>?,
): CloseableIterator<CardWithBatchName> {

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

    override fun next(): CardWithBatchName {
        val org = allCvrs.next()
        val batch = batchMap[org.poolId] // hijack poolId
        val hasCvr = type.isClca() || (type.isOA() && org.poolId == null)
        val votes = if (hasCvr) org.votes else null  // removes votes for pooled data

        val batchName = when {
            org.isPhantom() -> Batch.phantoms
            (batch != null) -> batch.name()
            else -> Batch.fromCvr
        }

        return CardWithBatchName(
            location = org.id,
            index = cardIndex++,
            prn = 0,
            phantom=org.phantom,
            votes = votes,
            poolId = if (type.isClca()) null else org.poolId,
            batchName = batchName,
        )
    }

    override fun close() = cvrs.close()
}

// we have the mvrs as cvrs and transform them to CardWithBatchName for private storage
// needed for out-of-memory handling (eg Corla)
// relies on cvrs having poolIds that match the batch.id()
// when it has a pool, use the pool name for the batchName
class MvrsToCardsWithBatchNameIterator(
    val mvrs: CloseableIterator<Cvr>,
    batches: List<BatchIF>,
    phantomCvrs : List<Cvr>? = null,
    seed: Long? = null,
): CloseableIterator<CardWithBatchName> {

    val allMvrs: Iterator<Cvr>

    init {
        allMvrs = if (phantomCvrs == null) {
            mvrs
        } else {
            val mvrSeq = mvrs.iterator().asSequence()
            val phantomSeq = phantomCvrs.asSequence()
            (mvrSeq + phantomSeq).iterator()
        }
    }

    val batchMap = batches.associateBy{ it.id() }
    val prng = if (seed != null) Prng(seed) else null

    var cardIndex = 0 // 0 based index

    override fun hasNext() = allMvrs.hasNext()

    override fun next(): CardWithBatchName {
        val org = allMvrs.next()
        val batch = batchMap[org.poolId]  // hijack poolId

        val batchName = when {
            org.isPhantom() -> Batch.phantoms
            (batch != null) -> batch.name()
            else -> Batch.fromCvr
        }

        return CardWithBatchName(
            org.id,
            cardIndex++,
            prng?.next() ?: 0,
            phantom = org.phantom,
            votes = org.votes,
            poolId = org.poolId,
            batchName = batchName,
        )
    }

    override fun close() = mvrs.close()
}

// TODO only used in testing
fun mvrsToAuditableCardsList(
    type: AuditType,
    mvrs: List<Cvr>,
    batches: List<BatchIF>?,
    seed: Long? = null,
): List<AuditableCard> {

    val batchMap = batches?.associateBy{ it.id() } ?: emptyMap()
    val prng = if (seed != null) Prng(seed) else null

    var cardIndex = 0 // 0 based index

    return mvrs.map { org ->
        val batch = batchMap[org.poolId]  // hijack poolId
        val hasCvr = type.isClca() || (type.isOA() && org.poolId == null)
        val votes = if (hasCvr) org.votes else null  // removes votes for pooled data

        val useBatch = when {
            org.isPhantom() -> Batch.phantomBatch
            (batch != null) -> batch
            else -> Batch.fromCvrBatch
        }

        AuditableCard(
            org.id,
            cardIndex++,
            prng?.next() ?: 0,
            phantom = org.phantom,
            votes = votes,
            batch = useBatch,
        )
    }
}