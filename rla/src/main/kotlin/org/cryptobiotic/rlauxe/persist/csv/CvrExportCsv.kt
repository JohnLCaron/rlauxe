package org.cryptobiotic.rlauxe.persist.csv

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrExport
import org.cryptobiotic.rlauxe.util.ZipReader
import java.io.*
import kotlin.collections.joinToString

const val CvrExportCsvHeader = "id, group, contests, candidates0, candidates1, ...\n"

private val logger = KotlinLogging.logger("CvrExportCvs")

fun readCvrExportCsv(line: String): CvrExport {
    val tokens = line.split(",")
    val ttokens = tokens.map { it.trim() }

    var idx = 0
    val id = ttokens[idx++]
    val group = ttokens[idx++].toInt()
    val contestsStr = ttokens[idx++]
    val contestsTokens = contestsStr.split(" ")
    val contests : List<Int> = contestsTokens.map { it.trim().toInt() }

    val work = mutableListOf<IntArray>()
    while (idx < ttokens.size && (work.size < contests.size)) {
        val vtokens = ttokens[idx]
        val candArray =
            if (vtokens.isEmpty()) intArrayOf() else vtokens.split(" ").map { it.trim().toInt() }.toIntArray()
        work.add(candArray)
        idx++
    }
    require(contests.size == work.size) { "contests.size (${contests.size}) != votes.size (${work.size})" }

    val votes = contests.zip(work).toMap()
    return CvrExport(id, group, votes)
}

fun cvrExportCsvIterator(filename: String): Iterator<CvrExport> {
    return if (filename.endsWith("zip")) {
        val reader = ZipReader(filename)
        val input = reader.inputStream()
        IteratorCvrExportStream(input)
    } else {
        IteratorCvrExportFile(filename)
    }
}

class IteratorCvrExportStream(input: InputStream): Iterator<CvrExport> {
    val reader = BufferedReader(InputStreamReader(input, "ISO-8859-1"))
    var nextLine: String? = reader.readLine() // get rid of header line

    var countLines  = 0
    override fun hasNext() : Boolean {
        countLines++
        nextLine = reader.readLine()
        return nextLine != null
    }

    override fun next(): CvrExport {
        return readCvrExportCsv(nextLine!!)
    }

    fun close() {
        logger.info{"read $countLines lines"}
        reader.close()
    }
}

class IteratorCvrExportFile(filename: String): Iterator<CvrExport> {
    val reader: BufferedReader = File(filename).bufferedReader()
    var nextLine: String? = reader.readLine() // get rid of header line

    var countLines  = 0
    override fun hasNext() : Boolean {
        countLines++
        nextLine = reader.readLine()
        return nextLine != null
    }

    override fun next(): CvrExport {
        return readCvrExportCsv(nextLine!!)
    }

    fun close() {
        logger.info{"read $countLines lines"}
        reader.close()
    }
}

fun writeCvrExportCsvFile(pools: Iterator<CvrExport>, filename: String) {
    val writer: OutputStreamWriter = FileOutputStream(filename).writer()
    writer.write(CvrExportCsvHeader)
    pools.forEach {
        writer.write(it.toCsv())
    }
    writer.close()
}

fun CvrExport.toCsv() = buildString {
    val contests = votes.map { it.key }
    append("$id, $group, ${contests.joinToString(" ")}, ")
    contests.forEach {
        append("${votes[it]!!.joinToString(" ")}, ")
    }
    appendLine()
}

class CvrExportAdapter(val cvrExportIterator: Iterator<CvrExport>) : Iterator<Cvr> {
    override fun hasNext() = cvrExportIterator.hasNext()
    override fun next() = cvrExportIterator.next().toCvr()
}



