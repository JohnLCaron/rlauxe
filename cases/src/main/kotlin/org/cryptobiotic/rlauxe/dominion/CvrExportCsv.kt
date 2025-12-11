package org.cryptobiotic.rlauxe.dominion

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.CloseableIterator
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

fun cvrExportCsvIterator(filename: String): CloseableIterator<CvrExport> {
    return if (filename.endsWith("zip")) {
        val reader = ZipReader(filename)
        val input = reader.inputStream()
        IteratorCvrExportStream(input)
    } else {
        IteratorCvrExportStream(File(filename).inputStream())
    }
}

class IteratorCvrExportStream(input: InputStream): CloseableIterator<CvrExport> {
    val reader = BufferedReader(InputStreamReader(input, "ISO-8859-1"))
    var nextLine: String? = null
    var countLines  = 0

    init {
        reader.readLine() // get rid of header line
    }

    override fun hasNext() : Boolean {
        if (nextLine == null) {
            countLines++
            nextLine = reader.readLine()
        }
        return nextLine != null
    }

    override fun next(): CvrExport {
        if (!hasNext())
            throw NoSuchElementException()
        val result =  readCvrExportCsv(nextLine!!)
        nextLine = null
        return result
    }

    override fun close() {
        reader.close()
    }
}

fun writeCvrExportCsvFile(cvrs: Iterator<CvrExport>, filename: String) {
    val writer: OutputStreamWriter = FileOutputStream(filename).writer()
    writer.write(CvrExportCsvHeader)
    cvrs.forEach { writer.write(it.toCsv()) }
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

class CvrExportToCvrAdapter(val cvrExportIterator: CloseableIterator<CvrExport>, val pools: Map<String, Int>? = null) : CloseableIterator<Cvr> {
    override fun hasNext() = cvrExportIterator.hasNext()
    override fun next() = cvrExportIterator.next().toCvr(pools=pools)
    override fun close() = cvrExportIterator.close()
}



