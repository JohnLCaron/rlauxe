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
    override fun next() = AuditableCard.fromCvrForPolling(cvrs.next(), count++)
    override fun close() = cvrs.close()
}

class CvrToAuditableCardClca(val cvrs: CloseableIterator<Cvr>) : CloseableIterator<AuditableCard> {
    var count = 0
    override fun hasNext() = cvrs.hasNext()
    override fun next() = AuditableCard.fromCvr(cvrs.next(), count++, sampleNum=0)
    override fun close() = cvrs.close()
}
