package org.cryptobiotic.rlauxe.corlacvr

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.cryptobiotic.rlauxe.util.ZipReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

private val logger = KotlinLogging.logger("CorlaCvrs")

fun readCorlaCvrs(source: String, redaction: Redaction = Redaction(), showHeaders: Boolean = false): CorlaCvrsIF {
    return if (source.startsWith("/resources/"))
        readCorlaCvrsFromResource(source, redaction = redaction, showHeaders=showHeaders)
    else readCorlaCvrsFromFile(source, redaction = redaction, showHeaders=showHeaders)
}

fun readCorlaCvrsFromFile(filename: String, showHeaders: Boolean = false, showSchema: Boolean = false,
                          redaction: Redaction = Redaction()): CorlaCvrsIF {
    val parser = if (filename.endsWith(".zip")) {
        val zipReader = ZipReader(filename)
        // by convention, the file inside is the filename with zip replaced by csv
        val lastPart = filename.substringAfterLast("/")
        val innerFilename = lastPart.replace(".zip", ".csv")
        val inputStream = zipReader.inputStream(innerFilename)
        val reader: Reader = InputStreamReader(inputStream, "UTF-8")
        CSVParser.parse(reader, CSVFormat.DEFAULT)
        // TODO if we could look ahead, we could give them the first row

    } else {
        CSVParser.parse(File(filename), Charset.forName("UTF-8"), CSVFormat.DEFAULT)
    }

    val corlaRawCvrs = CorlaRawCvrs(filename, parser, showHeaders, showSchema, redaction = redaction)
    corlaRawCvrs.readRows()
    return corlaRawCvrs
}

fun readCorlaCvrsFromResource(resourcePath: String, showHeaders: Boolean = false, showSchema: Boolean = false,
                              redaction: Redaction = Redaction()): CorlaCvrsIF {
    val resourceStream = getCsvStreamFromResource(resourcePath)
    val reader: Reader = InputStreamReader(resourceStream, "UTF-8")
    val parser =  CSVParser.parse(reader, CSVFormat.DEFAULT)
    val corlaRawCvrs = CorlaRawCvrs(resourcePath, parser, showHeaders, showSchema, redaction = redaction)
    corlaRawCvrs.readRows()
    return corlaRawCvrs
}

fun getCsvStreamFromResource(resourcePath: String): InputStream {
    var resourceStream =
        object {}.javaClass.getResourceAsStream(resourcePath) ?: throw IOException("$resourcePath does not exist")
    if (resourcePath.endsWith(".zip")) {
        val innerStream = getZippedCsvResourceStream(resourcePath, resourceStream)
        if (innerStream == null) throw IOException("zipped $resourcePath does not have the csv file inside")
        resourceStream = innerStream
    }
    return resourceStream
}

// TODO should InputStream implement Closeable?
fun getZippedCsvResourceStream(resourcePath: String, resourceStream: InputStream): InputStream? {
    val lastPart = resourcePath.substringAfterLast("/")
    val innerFilename = lastPart.replace(".zip", ".csv")
    val zipStream = ZipInputStream(resourceStream)
    var zipEntry = zipStream.nextEntry
    while (zipEntry != null) {
        if (!zipEntry.isDirectory && zipEntry.name == innerFilename) {
            return zipStream
        }
        zipStream.closeEntry()
        zipEntry = zipStream.nextEntry
    }
    return null
}

// read all the cvrs into memory so that it can implement cardStyles, nrows,  redaction, ballotStyleUnique
// TODO perhaps there should be a separate object using cvrs: CloseableIterable ??
interface CorlaCvrsIF {
    val inputSource: String
    val electionName: String
    val versionName: String
    val schema: CvrSchema
    fun cvrs(): Iterable<CvrRow>
    fun redaction(): RedactionIF
    fun cardStyleMap(): Map<CardStyleId, CvrCardStyle>
    fun cardStyles(): List<CvrCardStyle>
    fun nrows(): Int

    fun headers(): List<String>
    fun hasBallotType(): Boolean
    fun countBlankPrecincts(): Int
    fun ballotStyleUnique(): Boolean
    fun csvHeader(row: CvrRow, redactPrecinct: Boolean): String
}