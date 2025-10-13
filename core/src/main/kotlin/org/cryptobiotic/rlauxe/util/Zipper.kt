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
        logger.info{ "delete file $filename} was successful = $ok"}
    }
    return outputZipFile
}
