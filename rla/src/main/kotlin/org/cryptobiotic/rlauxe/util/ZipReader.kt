package org.cryptobiotic.rlauxe.util

import java.io.InputStream
import java.nio.file.*
import java.nio.file.spi.FileSystemProvider

// this could be in core
class ZipReader(zipFilename: String) {
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

}