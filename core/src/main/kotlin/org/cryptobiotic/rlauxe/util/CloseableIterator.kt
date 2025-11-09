package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardStyleIF
import org.cryptobiotic.rlauxe.core.Cvr
import kotlin.collections.get

interface CloseableIterable<out T> {
    fun iterator(): CloseableIterator<T>
}

inline fun <T> CloseableIterable(crossinline iterator: () -> Iterator<T>): CloseableIterable<T> = object : CloseableIterable<T> {
    override fun iterator(): CloseableIterator<T> = Closer(iterator())
}

interface CloseableIterator<out T> : Iterator<T>, AutoCloseable

fun <T> emptyCloseableIterable() : CloseableIterable<T> {
    return CloseableIterable { Closer(emptyList<T>().iterator()) }
}

class Closer<out T>(val iter: Iterator<T>) : CloseableIterator<T> {
    override fun hasNext() = iter.hasNext()
    override fun next() = iter.next()
    override fun close() {}
}

class CvrToAuditableCardPolling(val cvrs: CloseableIterator<Cvr>) : CloseableIterator<AuditableCard> {
    var count = 0
    override fun hasNext() = cvrs.hasNext()
    override fun next() = AuditableCard.fromCvrHasStyle(cvrs.next(), count++, isClca=false)
    override fun close() = cvrs.close()
}

class CvrToAuditableCardClca(val cvrIterator: CloseableIterator<Cvr>, val pools: Map<String, Int>? = null, startCount: Int = 0) : CloseableIterator<AuditableCard> {
    var count = startCount
    override fun hasNext() = cvrIterator.hasNext()
    override fun next() = AuditableCard.fromCvrHasStyle(cvrIterator.next(), count++, isClca=true)
    override fun close() = cvrIterator.close()
}

class FromCvrNoStyle(val cvrs: CloseableIterator<Cvr>, val possibleContests: IntArray, val isClca: Boolean) : CloseableIterator<AuditableCard> {
    var count = 0
    override fun hasNext() = cvrs.hasNext()
    override fun next() = AuditableCard.fromCvrNoStyle(cvrs.next(), count++, possibleContests, isClca)
    override fun close() = cvrs.close()
}

class ToAuditableCardPooled(val cards: CloseableIterator<AuditableCard>) : CloseableIterator<AuditableCard> {
    var count = 0
    override fun hasNext() = cards.hasNext()
    override fun next(): AuditableCard {
        val nextCard = cards.next()
        val useVotes = if (nextCard.poolId != null) null else nextCard.votes
        return nextCard.copy(votes = useVotes)
    }
    override fun close() = cards.close()
}

class ToAuditableCardPolling(val cards: CloseableIterator<AuditableCard>) : CloseableIterator<AuditableCard> {
    var count = 0
    override fun hasNext() = cards.hasNext()
    override fun next() = cards.next().copy(votes = null)
    override fun close() = cards.close()
}

// CLCA or OneAudit TODO test Polling
class CvrsWithStylesToCards(val cvrs: CloseableIterable<Cvr>, styles: List<CardStyleIF>?, val phantomCvrs : List<Cvr>?,
                            val type: AuditType, val hasStyle: Boolean): CloseableIterable<AuditableCard> {
    val poolMap = styles?.associateBy{ it.id() }
    override fun iterator(): CloseableIterator<AuditableCard> {
        val allSeq = if (phantomCvrs == null) {
            cvrs.iterator().asSequence()
        } else {
            val cardSeq = cvrs.iterator().asSequence()
            val phantomSeq = phantomCvrs.asSequence() // late binding to index, port to boulder, sf
            cardSeq + phantomSeq
        }

        return CvrToCardIterator(allSeq.iterator())
    }

    inner class CvrToCardIterator(val orgIter: Iterator<Cvr>): CloseableIterator<AuditableCard> {
        var cardIndex = 1
        override fun hasNext() = orgIter.hasNext()

        override fun next(): AuditableCard {
            val orgCvr = orgIter.next()
            val style = if (poolMap == null) null
                else poolMap[orgCvr.poolId]
            val hasCvr = type.isClca() || (type.isOA() && style == null)
            val poolId = if (!type.isOA()) null else style?.id()
            val contests = when {
                (hasCvr && hasStyle) -> null
                (style != null) -> style.contests()
                hasStyle -> orgCvr.contests()
                else -> null
            }
            val votes = if (hasCvr) orgCvr.votes else null
            return AuditableCard(orgCvr.id, cardIndex++, 0, phantom=orgCvr.phantom, contests ?: intArrayOf(), votes, poolId)
        }

        override fun close() {} // = orgIter.close()
    }
}

class CardsWithStylesToCards(val cards: CloseableIterable<AuditableCard>, val styles: List<CardStyleIF>?,
                             val phantomCvrs : List<AuditableCard>?, val type: AuditType, val hasStyle: Boolean): CloseableIterable<AuditableCard> {
    val poolMap = styles?.associateBy{ it.id() }

    override fun iterator(): CloseableIterator<AuditableCard> {
        val allSeq = if (phantomCvrs == null) {
            cards.iterator().asSequence()
        } else {
            val cardSeq = cards.iterator().asSequence()
            val phantomSeq = phantomCvrs.asSequence() // late binding to index, port to boulder, sf
            cardSeq + phantomSeq
        }

        return CardToCardIterator(allSeq.iterator())
    }

    inner class CardToCardIterator(val orgIter: Iterator<AuditableCard>): CloseableIterator<AuditableCard> {
        var cardIndex = 1
        override fun hasNext() = orgIter.hasNext()

        override fun next(): AuditableCard {
            val orgCard = orgIter.next()
            val style = if (poolMap == null) null
                else poolMap[orgCard.poolId]
            val hasCvr = type.isClca() || (type.isOA() && style == null)
            val poolId = if (!type.isOA()) null else style?.id()
            val contests = when {
                (hasCvr && hasStyle) -> null
                (style != null) -> style.contests()
                hasStyle -> orgCard.contests()
                else -> null
            }
            val votes = if (hasCvr) orgCard.votes else null
            return AuditableCard(orgCard.location, cardIndex++, 0, phantom=orgCard.phantom, contests ?: intArrayOf(), votes, poolId)
        }

        override fun close() {} // = orgIter.close()
    }
}

