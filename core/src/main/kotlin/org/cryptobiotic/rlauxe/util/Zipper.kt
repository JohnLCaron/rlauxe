package org.cryptobiotic.rlauxe.util

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.*
import java.nio.file.spi.FileSystemProvider
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val logger = KotlinLogging.logger("Zipper")

class ZipReader(val zipFilename: String) {
    val fileSystem : FileSystem
    val fileSystemProvider : FileSystemProvider

    init {
        if (!Files.exists(Path.of(zipFilename))) {
            throw RuntimeException("ZipReader: '$zipFilename' does not exist")
        }
        if (!zipFilename.endsWith(".zip")) {
            throw RuntimeException("ZipReader: '$zipFilename' must end in zip")
        }

        val zipPath = Path.of(zipFilename)
        fileSystem = FileSystems.newFileSystem(zipPath, emptyMap<String, String>())
        fileSystemProvider = fileSystem.provider()
    }

    fun inputStream(filename: String) : InputStream {
        val path = fileSystem.getPath(filename)
        return fileSystemProvider.newInputStream(path, StandardOpenOption.READ)
    }

    // special case when the name of the file you want is the same as the zip file, but with .csv extension
    fun inputStream() : InputStream {
        val lastPart = zipFilename.substringAfterLast("/")
        val innerFilename = lastPart.replace(".zip", "")
        return inputStream(innerFilename)
    }

}

// depth first tour of all files in the directory tree
class ZipReaderTour(zipFile: String, val silent: Boolean = true, val sortPaths: Boolean = true,
                    val filter: (Path) -> Boolean, val visitor: (InputStream) -> Unit) {
    var count = 0
    val zipReader = ZipReader(zipFile)
    val provider: FileSystemProvider = zipReader.fileSystemProvider
    val fileSystem: FileSystem = zipReader.fileSystem

    fun tourFiles() {
        fileSystem.rootDirectories.forEach { root: Path ->
            readDirectory(Indent(0), provider, root)
        }
    }

    fun readDirectory(indent: Indent, provider: FileSystemProvider, dirPath: Path): Int {
        val paths = mutableListOf<Path>()
        Files.newDirectoryStream(dirPath).use { stream ->
            for (path in stream) {
                paths.add(path)
            }
        }
        if (sortPaths) paths.sort()
        paths.forEach { path ->
            if (Files.isDirectory(path)) {
                readDirectory(indent.incr(), provider, path)
            } else {
                if (filter(path)) {
                    if (!silent) println("$indent ${path.fileName}")
                    val input = provider.newInputStream(path, StandardOpenOption.READ)
                    visitor(input)
                    count++
                }
            }
        }
        return count
    }
}

class ZipReaderIterator<T>(zipFile: String, val filter: (Path) -> Boolean, val reader: (InputStream) -> Iterator<T>): CloseableIterator<T> {
    var count = 0
    val zipReader = ZipReader(zipFile)
    val provider: FileSystemProvider = zipReader.fileSystemProvider
    val fileSystem: FileSystem = zipReader.fileSystem

    var rootsIterator: Iterator<DepthFirst>
    var currentRoot: DepthFirst
    var valueIterator: Iterator<T> = emptyList<T>().iterator()

    init {
        rootsIterator = fileSystem.rootDirectories.map { DepthFirst(it) }.iterator()
        currentRoot = rootsIterator.next()

        // this loads the first valueIterator
        hasNext()
    }

    override fun next(): T {
        return valueIterator.next()
    }

    override fun hasNext(): Boolean {
        if (valueIterator.hasNext()) return true
        if (currentRoot.hasNext()) {
            val path = currentRoot.next()
            if (filter(path)) {
                val input = provider.newInputStream(path, StandardOpenOption.READ)
                valueIterator = reader(input)
            }
            return hasNext()
        }
        if (rootsIterator.hasNext()) {
            currentRoot = rootsIterator.next()
            return hasNext()
        }
        return false
    }

    override fun close() { }

    class DepthFirst(dir: Path): Iterator<Path> {
        val stack = ArrayDeque<DirIterator>()
        var current: DirIterator

        init {
            current = DirIterator(dir)
            while (!current.isLeaf) {
                stack.addLast(current)
                current = DirIterator(current.next()) // TODO test empty
            }
        }

        override fun next() = current.next()

        override fun hasNext(): Boolean {
            if (current.hasNext()) return true
            if (stack.isEmpty()) return false
            findNextLeafNode()
            return hasNext()
        }

        fun findNextLeafNode() {
            // go up the stack to next non-empty iterator
            while (!current.hasNext() && !stack.isEmpty()) {
                current = stack.removeLast()
            }
            if (stack.isEmpty()) return

            // go down the stack till we find the leaf nodes that contain files
            while (!current.isLeaf) {
                val next = current.next()
                stack.addLast(current) // put non-leaf back onto the stack
                current = DirIterator(next) // TODO test empty
            }
            // exits when current is leaf and non empty, or stack.isEmpty()
        }
    }

    class DirIterator(val dir: Path): Iterator<Path> {
        var innerIterator: Iterator<Path>
        val isLeaf: Boolean
        val paths = mutableListOf<Path>()

        init {
            Files.newDirectoryStream(dir).use { stream ->
                for (path in stream) {
                    paths.add(path)
                }
            }
            paths.sort()
            innerIterator = paths.iterator()
            isLeaf = !paths.isEmpty() && !Files.isDirectory(paths[0]) // assume all the same
        }

        override fun hasNext() = innerIterator.hasNext()
        override fun next(): Path {
            return innerIterator.next()
        }
    }
}

///////////////////////////////////////////////////////////////////////////////
// TODO files only. not directories
fun createZipFile(filename: String, delete: Boolean = false): File {
    val file = File(filename)
    val outputZipFile = File(filename + ".zip")
    ZipOutputStream(FileOutputStream(outputZipFile)).use { zipOut ->
        FileInputStream(file).use { fis ->
            val zipEntry = ZipEntry(file.name)
            zipOut.putNextEntry(zipEntry)
            fis.copyTo(zipOut)
            zipOut.closeEntry()
        }
    }
    if (delete) {
        val ok = file.delete()
        if (!ok) logger.warn{ "delete file $filename} failed"}
    }
    return outputZipFile
}
