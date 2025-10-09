package org.cryptobiotic.rlauxe.util

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
        println("delete file was successful = $ok")
    }
    return outputZipFile
}