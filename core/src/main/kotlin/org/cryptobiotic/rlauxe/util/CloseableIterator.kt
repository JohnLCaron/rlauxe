package org.cryptobiotic.rlauxe.util

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
