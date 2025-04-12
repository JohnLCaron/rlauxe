package org.cryptobiotic.rlauxe.util

import java.nio.file.Files
import java.nio.file.Path

private val show = false

// read all files in topdir (depth first), iterator pattern.
// arbitrary depth of directory tree
class TreePathIterator(topDir: String,
                       val fileFilter: (Path) -> Boolean,
): Iterator<Path> {
    var topDirectory = DirectoryIterator(Path.of(topDir))

    override fun hasNext(): Boolean = topDirectory.hasNext()
    override fun next(): Path = topDirectory.next()

    // a directory with directories or files (not both)
    inner class DirectoryIterator(dirPath: Path) : Iterator<Path> {
        val holdsFiles: Boolean
        val pathIterator: Iterator<Path>
        var subdirectory: DirectoryIterator? = null

        init {
            val dirs = mutableListOf<Path>()
            val files = mutableListOf<Path>()
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
    }
}

// read all files in topdir (depth first). pass each file to the reader.
// TODO: support zip files.
class TreeReaderIterator <T> (
    topDir: String,
    val fileFilter: (Path) -> Boolean,
    val reader: (Path) -> Iterator<T>
): Iterator<T> {
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
        if (!paths.hasNext()) return null
        path = paths.next()
        return BaseIterator(path!!)
    }

    inner class BaseIterator(val path: Path) : Iterator<T> {
        val tIterator = reader(path)
        override fun hasNext(): Boolean = tIterator.hasNext()
        override fun next(): T = tIterator.next()
    }
}

// read all files in topdir (depth first). pass each file to the visitor.
class TreeReaderTour(val topDir: String, val silent: Boolean = true, val visitor: (Path) -> Unit) {
    var count = 0

    // depth first tour of all files in the directory tree
    fun tourFiles() {
        readDirectory(Indent(0), Path.of(topDir))
        if (!silent) println("count = $count")
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

