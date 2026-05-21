package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.CloseableIterator
import kotlin.collections.get
import kotlin.sequences.plus

// merge styles and mvrs -> cards list
fun mvrsToAuditableCardsListM(
    mvrs: List<Cvr>,
    styles: List<StyleIF>?,
): List<AuditableCardM> {

    val styleMap = styles?.associateBy{ it.id() } ?: emptyMap()

    var cardIndex = 0 // 0 based index

    return mvrs.map { org ->
        val style = styleMap[org.poolId]  // hijack poolId

        val useStyleName = when {
            (style != null) -> style.name()
            org.phantom() -> CardStyle.phantoms
            else -> CardStyle.fromCvr
        }

        val (contestIds, contestStarts, candidates) = makeFromVotes(org.votes)
        AuditableCardM(
            org.id,
            null,
            cardIndex++,
            0,
            phantom = org.phantom,
            styleName = useStyleName,
            poolId = org.poolId,
            contestIds, contestStarts, candidates,
        )
    }
}

// merge styles and mvrs + phantoms -> cards iterator
class MvrsToCardStylesIterator(
    val mvrs: CloseableIterator<Cvr>,
    styles: List<StyleIF>, //  either CardPool or CardStyle
    phantomCvrs : List<Cvr>? = null,
): CloseableIterator<AuditableCardM> {

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

    val styleMap = styles.associateBy{ it.id() }
    var cardIndex = 0 // 0 based index

    override fun hasNext() = allMvrs.hasNext()

    override fun next(): AuditableCardM {
        val org = allMvrs.next()
        val style = styleMap[org.poolId]  // hijack poolId

        val styleName = when {
            org.phantom() -> CardStyle.phantoms
            (style != null) -> style.name()
            else -> CardStyle.fromCvr
        }

        val (contestIds, contestStarts, candidates) = makeFromVotes(org.votes)
        val cardm = AuditableCardM(
            id = org.id,
            location = null,
            index = cardIndex++,
            prn = 0,
            phantom=org.phantom,
            styleName = styleName,
            poolId = org.poolId,
            contestIds = contestIds,
            contestStarts = contestStarts,
            candidates = candidates,
        )
        if (style != null) cardm.setStyle(style)
        return cardm

    }

    override fun close() = mvrs.close()
}

// merge styles and cvrs + phantoms -> cards iterator
// removes votes for pooled data
class CvrsToCardStylesIterator(
    val type: AuditType,
    val cvrs: CloseableIterator<Cvr>,
    phantomCvrs : List<Cvr>?,
    styles: List<StyleIF>?,  //  either CardPool or CardStyle
): CloseableIterator<AuditableCardM> {

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

    override fun next(): AuditableCardM {
        val org = allCvrs.next()
        val style = styleMap[org.votes.keys]
        val hasCvr = type.isClca() || (type.isOA() && org.poolId == null)
        val votes = if (hasCvr) org.votes else null  // removes votes for pooled data

        val styleName = when {
            (style != null) -> style.name()
            org.phantom() -> CardStyle.phantoms
            else -> CardStyle.fromCvr
        }
        val (contestIds, contestStarts, candidates) = makeFromVotes(votes)
        val cardm = AuditableCardM(
            id = org.id,
            location = null,
            index = cardIndex++,
            prn = 0,
            phantom=org.phantom,
            styleName = styleName,
            poolId = if (type.isClca()) null else org.poolId,
            contestIds = contestIds,
            contestStarts = contestStarts,
            candidates = candidates,
        )
        if (style != null) cardm.setStyle(style)
        return cardm
    }

    override fun close() = cvrs.close()
}

// merge styles into cards iterator
class MergeStylesIntoCardsM(
    val cardsIter: CloseableIterator<AuditableCardM>,
    styles: List<StyleIF>,
): CloseableIterator<AuditableCardM> {
    val styleMap = styles.associateBy{ it.name() }

    override fun hasNext() = cardsIter.hasNext()

    // styleName must be in styleMap
    override fun next(): AuditableCardM {
        val org = cardsIter.next()
        val style = styleMap[org.styleName]
        val cardStyle = when {
            style != null -> style
            org.styleName == CardStyle.phantoms -> CardStyle.phantomBatch
            org.styleName == CardStyle.fromCvr -> CardStyle.fromCvrBatch
            else -> throw RuntimeException()
        }
        return org.setStyle(cardStyle)
    }

    override fun close() { cardsIter.close() }
}

