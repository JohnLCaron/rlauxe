package org.cryptobiotic.rlauxe.util

import java.nio.file.Files
import java.nio.file.Path

// read all files in topdir (depth first), iterator pattern.
// arbitrary depth of directory tree
class TreePathIterator(topDir: String,
                       val fileFilter: (Path) -> Boolean,
): CloseableIterator<Path> {
    var topDirectory = DirectoryIterator(Path.of(topDir))

    override fun hasNext(): Boolean = topDirectory.hasNext()
    override fun next(): Path = topDirectory.next()
    override fun close() { topDirectory.close() }

    // a directory with directories or files (not both)
    inner class DirectoryIterator(dirPath: Path) : CloseableIterator<Path> {
        val holdsFiles: Boolean
        val pathIterator: Iterator<Path>
        var subdirectory: DirectoryIterator? = null

        init {
            val dirs = mutableListOf<Path>()
            val files = mutableListOf<Path>()
            // TODO redo to stream rather than hold a list
            Files.newDirectoryStream(dirPath).use { stream ->
                for (path in stream) {
                    if (Files.isDirectory(path)) {
                        dirs.add(path)
                    } else if (fileFilter(path)) {
                        files.add(path)
                    }
                }
            }
            holdsFiles = files.isNotEmpty()
            pathIterator = if (holdsFiles) files.sorted().iterator() else dirs.sorted().iterator()
        }

        override fun hasNext(): Boolean {
            if (holdsFiles) return pathIterator.hasNext()
            val subdirHasNext = (subdirectory != null) && subdirectory!!.hasNext()
            if (subdirHasNext) return true

            if (!pathIterator.hasNext()) return false
            subdirectory = DirectoryIterator(pathIterator.next())
            return subdirectory!!.hasNext()
        }

        override fun next(): Path {
            if (holdsFiles) return pathIterator.next()
            return subdirectory!!.next()
        }

        override fun close() {}
    }
}

// read all files in topdir (depth first). pass each file to the reader.
// TODO: support zip files.
class TreeReaderIterator <T> (
    topDir: String,
    val fileFilter: (Path) -> Boolean,
    val reader: (Path) -> CloseableIterator<T>
): CloseableIterator<T> {
    val paths = TreePathIterator(topDir, fileFilter)
    var base: BaseIterator? = null
    var path: Path? = null
    var count = 0

    override fun hasNext(): Boolean {
        if (base == null) base = getNextBaseIterator()
        if (base == null) return false

        // its possible that the base iterator is empty
        while (!base!!.hasNext()) {
            base = getNextBaseIterator()
            if (base == null) return false
        }
        return true
    }

    override fun next(): T {
        count++
        return base!!.next()
    }

    fun getNextBaseIterator() : BaseIterator? {
        if (base != null) base!!.close()
        if (!paths.hasNext()) {
            paths.close()
            return null
        }
        path = paths.next()
        return BaseIterator(path!!)
    }

    override fun close() {}

    inner class BaseIterator(val path: Path) : CloseableIterator<T> {
        val tIterator = reader(path)
        override fun hasNext(): Boolean = tIterator.hasNext()
        override fun next(): T = tIterator.next()
        override fun close() { tIterator.close() }
    }
}

// read all files in topdir (depth first). pass each file to the visitor.
class TreeReaderTour(val topDir: String, val silent: Boolean = true, val visitor: (Path) -> Unit) {
    var total = 0

    // depth first tour of all files in the directory tree
    fun tourFiles(): Int {
        total += readDirectory(Indent(0), Path.of(topDir))
        if (!silent) println("total = $total")
        return total
    }

    fun readDirectory(indent: Indent, dirPath: Path): Int {
        val paths = mutableListOf<Path>()
        Files.newDirectoryStream(dirPath).use { stream ->
            for (path in stream) {
                paths.add(path)
            }
        }
        paths.sort()
        var count = 0
        paths.forEach { path ->
            if (Files.isDirectory(path)) {
                if (!silent) println("$indent ${path.fileName}")
                val nfiles = readDirectory(indent.incr(), path)
                if (!silent) println("$indent ${path.fileName} has $nfiles files")
                count += nfiles
            } else {
                visitor(path)
                count++
            }
        }
        return count
    }
}

