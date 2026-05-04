package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Prng
import kotlin.collections.get
import kotlin.sequences.plus

// TODO lots of boilerplate. Can we reduce with TransformingIterator ??
//    see CreateSfprecinctOA.createCards()

//// Add batch reference when reading in CardNoBatch

fun merge(cards: CloseableIterator<CardWithBatchName>, batches: List<StyleIF>?) : CloseableIterator<AuditableCard> {
    return MergeBatchesIntoCardManifestIterator(cards, batches ?: emptyList())
}

fun merge(cards: List<CardWithBatchName>, batches: List<StyleIF>?) : List<AuditableCard> {
    val iter = MergeBatchesIntoCardManifestIterator(Closer(cards.iterator()), batches ?: emptyList())
    val result = mutableListOf<AuditableCard>()
    while (iter.hasNext()) result.add(iter.next())
    return result
}

// read CardWithBatchName, add batch, output AuditableCard as Iterator
class MergeBatchesIntoCardManifestIterator(
    val cardsIter: CloseableIterator<CardWithBatchName>,
    batches: List<StyleIF>,
): CloseableIterator<AuditableCard> {
    val batchMap = batches.associateBy{ it.name() }

    override fun hasNext() = cardsIter.hasNext()

    // styleName must be in batchMap
    override fun next(): AuditableCard {
        val org = cardsIter.next()
        val batchy = batchMap[org.styleName]
        val cardStyle = when {
            org.styleName == CardStyle.phantoms -> CardStyle.phantomBatch
            org.styleName == CardStyle.fromCvr -> CardStyle.fromCvrBatch
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
    val batches: List<StyleIF>,
): CloseableIterable<AuditableCard> {

    override fun iterator(): CloseableIterator<AuditableCard> = MergeBatchesIterator(cards.iterator(), batches)

    // TODO use MergeBatchesIntoCardManifestIterator
    private class MergeBatchesIterator(
        val cardsIter: CloseableIterator<CardWithBatchName>,
        batches: List<StyleIF>,
    ): CloseableIterator<AuditableCard> {
        val batchMap = batches.associateBy { it.name() }

        override fun hasNext() = cardsIter.hasNext()

        // styleName must be in batchMap
        override fun next(): AuditableCard {
            val org: CardWithBatchName = cardsIter.next()
            val batchy = batchMap[org.styleName]

            val cardStyle = when {
                org.styleName == CardStyle.phantoms -> CardStyle.phantomBatch
                org.styleName == CardStyle.fromCvr -> CardStyle.fromCvrBatch
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
    val cvrs: CloseableIterator<Cvr>,
    val phantomCvrs : List<Cvr>?,
    batches: List<StyleIF>?,  //  either CardPool or CardStyle
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
            org.isPhantom() -> CardStyle.phantoms
            (batch != null) -> batch.name()
            else -> CardStyle.fromCvr
        }

        return CardWithBatchName(
            id = org.id,
            location = null,
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

// 1. merge in phantoms
// 2. add BatchName: when it has a pool, use the pool name for the batchName

class MvrsToCardsWithBatchNameIterator(
    val mvrs: CloseableIterator<Cvr>,
    batches: List<StyleIF>, //  either CardPool or CardStyle
    phantomCvrs : List<Cvr>? = null,
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

    var cardIndex = 0 // 0 based index

    override fun hasNext() = allMvrs.hasNext()

    override fun next(): CardWithBatchName {
        val org = allMvrs.next()
        val batch = batchMap[org.poolId]  // hijack poolId

        val styleName = when {
            org.isPhantom() -> CardStyle.phantoms
            (batch != null) -> batch.name()
            else -> CardStyle.fromCvr
        }

        return CardWithBatchName(
            org.id,
            null,
            cardIndex++,
            0,
            phantom = org.phantom,
            votes = org.votes,
            poolId = org.poolId,
            styleName = styleName,
        )
    }

    override fun close() = mvrs.close()
}

// This is replacing MvrsToCardsWithBatchNameIterator in ElectionBuilder.createUnsortedMvrsInternal
fun mvrsToAuditableCardsList(
    mvrs: List<Cvr>,
    batches: List<StyleIF>?,
): List<CardWithBatchName> {

    val batchMap = batches?.associateBy{ it.id() } ?: emptyMap()

    var cardIndex = 0 // 0 based index

    return mvrs.map { org ->
        val batch = batchMap[org.poolId]  // hijack poolId

        val useBatchName = when {
            org.isPhantom() -> CardStyle.phantoms
            (batch != null) -> batch.name()
            else -> CardStyle.fromCvr
        }

        CardWithBatchName(
            org.id,
            null,
            cardIndex++,
            0,
            phantom = org.phantom,
            votes =  org.votes,
            poolId = org.poolId,
            styleName = useBatchName,
        )
    }
}

// TODO only used in testing; remove and replace with mvrsToAuditableCardsList where needed
fun mvrsToAuditableCardsTest(
    type: AuditType,
    mvrs: List<Cvr>,
    batches: List<StyleIF>?,
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
            org.isPhantom() -> CardStyle.phantomBatch
            (batch != null) -> batch
            else -> CardStyle.fromCvrBatch
        }

        AuditableCard(
            org.id,
            null,
            cardIndex++,
            prng?.next() ?: 0,
            phantom = org.phantom,
            votes = votes,
            poolId = org.poolId,
            style = useBatch,
        )
    }
}