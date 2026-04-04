package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Prng
import kotlin.collections.get
import kotlin.sequences.plus

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

    // styleName must be in batchMap
    override fun next(): AuditableCard {
        val org = cardsIter.next()
        val batchy = batchMap[org.styleName]
        val cardStyle = when {
            org.styleName == Batch.phantoms -> Batch.phantomBatch
            org.styleName == Batch.fromCvr -> Batch.fromCvrBatch
            batchy != null -> batchy
            else ->
                throw RuntimeException()
        }
        return AuditableCard(org, cardStyle)
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

        // styleName must be in batchMap
        override fun next(): AuditableCard {
            val org: CardWithBatchName = cardsIter.next()
            val batchy = batchMap[org.styleName]

            val cardStyle = when {
                org.styleName == Batch.phantoms -> Batch.phantomBatch
                org.styleName == Batch.fromCvr -> Batch.fromCvrBatch
                batchy != null -> batchy
                else ->
                    throw RuntimeException()
            }
            return AuditableCard(org, cardStyle)
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

        val styleName = when {
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
            styleName = styleName,
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

        val styleName = when {
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
            styleName = styleName,
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
            cardStyle = useBatch,
        )
    }
}