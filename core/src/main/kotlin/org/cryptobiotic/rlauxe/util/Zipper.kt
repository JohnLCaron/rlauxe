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

// read a zipped file as an input stream
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

    // special case when the name of the file you want is the same as the zip file, without the zip extension
    fun inputStream() : InputStream {
        val lastPart = zipFilename.substringAfterLast("/")
        val innerFilename = lastPart.replace(".zip", "")
        return inputStream(innerFilename)
    }

}

///////////////////////////////////////////////////////////////////////////////
// TODO add ability to zip directories of files
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
