package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.CloseableIterator
import kotlin.collections.get
import kotlin.sequences.plus


////////////////////////////////////////////
// use the set of possible contests to find teh right card style

class CvrsToCardStylesIterator(
    val type: AuditType,
    val cvrs: CloseableIterator<Cvr>,
    val phantomCvrs : List<Cvr>?,
    styles: List<StyleIF>?,  //  either CardPool or CardStyle
): CloseableIterator<CardWithStyleName> {

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

    override fun next(): CardWithStyleName {
        val org = allCvrs.next()
        val style = styleMap[org.votes.keys]
        val hasCvr = type.isClca() || (type.isOA() && org.poolId == null)
        val votes = if (hasCvr) org.votes else null  // removes votes for pooled data

        val styleName = when {
            (style != null) -> style.name()
            org.phantom() -> CardStyle.phantoms
            else -> CardStyle.fromCvr
        }

        return CardWithStyleName(
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
