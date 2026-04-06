package org.cryptobiotic.rlauxe.dominion

import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.audit.CardPoolIF
import org.cryptobiotic.rlauxe.audit.CardWithBatchName
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.ZipReader
import java.io.*
import java.nio.file.Files
import kotlin.collections.associateBy
import kotlin.collections.joinToString
import kotlin.io.path.Path

const val CvrExportCsvHeader = "id, group, style, precinct, contests, candidates0, candidates1, ...\n"

// private val logger = KotlinLogging.logger("CvrExportCvs")

fun readCvrExportCsv(line: String): CvrExport {
    val tokens = line.split(",")
    val ttokens = tokens.map { it.trim() }

    var idx = 0
    val id = ttokens[idx++]
    val group = ttokens[idx++].toInt()
    val style = ttokens[idx++].toInt()
    val precinct = ttokens[idx++].toInt()
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
    return CvrExport(id, group, style, precinct, votes)
}

fun cvrExportCsvIterator(filename: String): CloseableIterator<CvrExport> {
    val useFilename = if (Files.exists(Path("$filename.zip"))) "$filename.zip" else filename

    return if (useFilename.endsWith("zip")) {
        val reader = ZipReader(useFilename)
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
    append("$id, $group, $ballotStyleId, $precinctPortionId, ${contests.joinToString(" ")}, ")
    contests.forEach {
        append("${votes[it]!!.joinToString(" ")}, ")
    }
    appendLine()
}

// converts CvrExport to CardWithBatchName, adds poolId, styleName, location
class CvrExportToCardAdapter(val cvrExportIterator: CloseableIterator<CvrExport>, val pools: List<CardPoolIF>?, val convertPoolIds: Boolean) : CloseableIterator<CardWithBatchName> {
    val poolMap = pools?.associateBy { it.name() } ?: emptyMap()
    val poolCounts = mutableMapOf<String, Int>() // to assign index within the poool
    var countIndex = 0

    override fun hasNext() = cvrExportIterator.hasNext()
    override fun next(): CardWithBatchName {
        val cvrExport = cvrExportIterator.next()
        val pool = if (pools == null || cvrExport.group != 1) null else poolMap[ cvrExport.poolKey() ]

        // TODO this is location. Perhaps we need to also store original id ?? So maybe output card ?
        val location = if (!convertPoolIds || pool == null) null else {
            val poolName = cvrExport.poolKey()
            val poolCount =  poolCounts.getOrPut(poolName) { 0 }
            poolCounts[poolName] = poolCount + 1
            "pool ${pool.name()} position${poolCount+1}"
        }

        val result = CardWithBatchName(
            cvrExport.id,
            location,
            countIndex,
            0,
            phantom = false,
            votes =  cvrExport.votes,
            poolId = pool?.poolId,
            styleName = pool?.name() ?: CardStyle.fromCvr,
        )

        countIndex++
        return result
    }
    override fun close() = cvrExportIterator.close()
}
