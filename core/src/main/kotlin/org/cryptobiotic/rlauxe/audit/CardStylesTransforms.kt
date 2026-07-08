package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.TransformingIterator
import kotlin.collections.get
import kotlin.sequences.plus

// merge styles and mvrs -> cards list
fun mvrsToAuditableCardsList(
    mvrs: List<Cvr>,
    styles: List<StyleIF>?,
): List<AuditableCard> {
    val styleMap = styles?.associateBy{ it.id() } ?: emptyMap()
    var cardIndex = 0 // 0 based index

    return mvrs.map { org ->
        val style = styleMap[org.poolId]  // hijack poolId

        val styleId = when {
            (style != null) -> style.id()
            org.phantom() -> CardStyle.phantomStyle.id()
            else -> CardStyle.fromCvrStyle.id()
        }

        val (contestIds, contestStarts, candidates) = makeFromVotes(org.votes)
        AuditableCard(
            org.id,
            null,
            cardIndex++,
            0,
            phantom = org.phantom,
            styleId = styleId,
            contestIds, contestStarts, candidates,
            poolId = org.poolId,
        )
    }
}

// merge styles and cvrs + phantoms -> cards iterator
// removes votes for pooled data
class CvrsToCardStylesIterator(
    val type: AuditType,
    val cvrs: CloseableIterator<Cvr>,
    phantomCvrs : List<Cvr>?,
    styles: List<StyleIF>?,  //  either CardPool or CardStyle
): CloseableIterator<AuditableCard> {

    val styleMap = styles?.associateBy{ it.contestIdSet() } ?: emptyMap()
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
        val style = styleMap[org.votes.keys]
        val hasCvr = type.isClca() || (type.isOA() && org.poolId == null)
        val votes = if (hasCvr) org.votes else null  // removes votes for pooled data

        val styleId = when {
            (style != null) -> style.id()
            org.phantom() -> CardStyle.phantomStyle.id()
            else -> CardStyle.fromCvrStyle.id()
        }
        val (contestIds, contestStarts, candidates) = makeFromVotes(votes)
        val cardm = AuditableCard(
            id = org.id,
            location = null,
            index = cardIndex++,
            prn = 0,
            phantom=org.phantom,
            styleId = styleId,
            contestIds = contestIds,
            contestStarts = contestStarts,
            candidates = candidates,
            poolId = if (type.isClca()) null else org.poolId,
        )
        if (style != null) cardm.setStyle(style)
        return cardm
    }

    override fun close() = cvrs.close()
}

// merge styles into cards iterable, can be iterated multiple times
class MergeStylesIntoCards(
    val cardsIterable: Iterable<AuditableCard>,
    val styles: List<StyleIF>,
): CloseableIterable<AuditableCard> {
    override fun iterator(): CloseableIterator<AuditableCard> {
        return mergeStylesIntoCards(Closer(cardsIterable.iterator()), styles)
    }
}

// merge styles into cards iterator, can be iterated once
fun mergeStylesIntoCards(cardsIter: CloseableIterator<AuditableCard>, styles: List<StyleIF>): CloseableIterator<AuditableCard> {
    val styleMap = styles.associateBy{ it.id() }
    return TransformingIterator(cardsIter) { org ->
        val style = styleMap[org.styleId]
        val cardStyle = when {
            style != null -> style
            org.styleId == CardStyle.phantomStyle.id() -> CardStyle.phantomStyle
            org.styleId == CardStyle.fromCvrStyle.id() -> CardStyle.fromCvrStyle
            else -> throw RuntimeException()
        }
        org.setStyle(cardStyle)
    }
}

