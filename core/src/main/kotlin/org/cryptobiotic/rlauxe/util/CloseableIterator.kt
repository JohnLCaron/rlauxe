package org.cryptobiotic.rlauxe.util

interface CloseableIterable<out T> {
    fun iterator(): CloseableIterator<T>
}

inline fun <T> CloseableIterable(crossinline iterator: () -> Iterator<T>): CloseableIterable<T> = object : CloseableIterable<T> {
    override fun iterator(): CloseableIterator<T> = Closer(iterator())
}

interface CloseableIterator<out T> : Iterator<T>, AutoCloseable

fun <T> emptyCloseableIterator() = Closer(emptyList<T>().iterator())

class Closer<out T>(val iter: Iterator<T>) : CloseableIterator<T> {
    override fun hasNext() = iter.hasNext()
    override fun next() = iter.next()
    override fun close() {}
}

class SubsetIterator<T>(skip:Int, val limit: Int?, val proxy: CloseableIterator<T>) : CloseableIterator<T> {
    private val iterator = proxy.iterator()
    var count = 0

    init {
        repeat(skip) { iterator.next() }
    }

    override fun next(): T {
        count++
        return iterator.next()
    }

    override fun hasNext(): Boolean {
        return iterator.hasNext() && (limit == null || count < limit)
    }

    override fun close() {
        proxy.close()
    }
}

class TransformingIterator<R, T> (val org: CloseableIterator<R>, val transform: (R) -> T) : CloseableIterator<T> {
    private val iterator: Iterator<R> = org.iterator()
    override fun next(): T {
        return transform(iterator.next())
    }

    override fun hasNext(): Boolean {
        return iterator.hasNext()
    }

    override fun close() {
        org.close()
    }
}
