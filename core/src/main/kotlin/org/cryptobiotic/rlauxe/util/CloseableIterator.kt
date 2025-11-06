package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.Cvr

interface CloseableIterable<out T> {
    fun iterator(): CloseableIterator<T>
}

inline fun <T> CloseableIterable(crossinline iterator: () -> Iterator<T>): CloseableIterable<T> = object : CloseableIterable<T> {
    override fun iterator(): CloseableIterator<T> = Closer(iterator())
}

interface CloseableIterator<out T> : Iterator<T>, AutoCloseable

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

class CvrToAuditableCardClca(val cvrs: CloseableIterator<Cvr>) : CloseableIterator<AuditableCard> {
    var count = 0
    override fun hasNext() = cvrs.hasNext()
    override fun next() = AuditableCard.fromCvrHasStyle(cvrs.next(), count++, isClca=true)
    override fun close() = cvrs.close()
}

class CvrToCardAdapter(val cvrIterator: CloseableIterator<Cvr>, val pools: Map<String, Int>? = null, startCount: Int = 0) : CloseableIterator<AuditableCard> {
    var count = startCount
    override fun hasNext() = cvrIterator.hasNext()
    override fun next() = AuditableCard.fromCvrHasStyle(cvrIterator.next(), count++, isClca=true)
    override fun close() {
        cvrIterator.close()
    }
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

