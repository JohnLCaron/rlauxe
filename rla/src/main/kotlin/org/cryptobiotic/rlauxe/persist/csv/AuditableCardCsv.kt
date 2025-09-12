package org.cryptobiotic.rlauxe.persist.csv


import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.util.ZipReader
import java.io.*

private val logger = KotlinLogging.logger("AuditableCardCsv")

// data class AuditableCard (
//    val desc: String, // info to find the card for a manual audit. Part of the info the Prover commits to before the audit.
//    val index: Int,  // index into the original, canonical (committed-to) list of cards
//    val sampleNum: Long,
//    val phantom: Boolean,
//    val contests: IntArray, // aka ballot style
//    val votes: List<IntArray>?, // contest -> list of candidates voted for; for IRV, ranked first to last
//    val poolId: Int?, // for OneAudit
//)

val AuditableCardHeader = "location, index, prn, phantom, poolId, contests, candidates0, candidates1, ...\n"

fun writeAuditableCardCsv(card: AuditableCard) = buildString {
    append("${card.location}, ${card.index}, ${card.prn}, ${card.phantom}, ")
    if (card.poolId == null) append(", ") else append("${card.poolId}, ")
    append("${card.contests.joinToString(" ")}, ")
    if (card.votes != null) {
        card.votes!!.forEach { candidates -> append("${candidates.joinToString(" ")}, ")}
    }
    appendLine()
}

fun writeAuditableCardCsvFile(pools: List<AuditableCard>, filename: String) {
    val writer: OutputStreamWriter = FileOutputStream(filename).writer()
    writer.write(AuditableCardHeader)
    pools.forEach {
        writer.write(writeAuditableCardCsv(it))
    }
    writer.close()
}

class AuditableCardCsvWriter(filename: String) {
    val writer: OutputStreamWriter = FileOutputStream(filename).writer()
    var countCards = 0
    init {
        writer.write(AuditableCardHeader)
    }

    fun write(cards: List<AuditableCard>) {
        cards.forEach {
            writer.write(writeAuditableCardCsv(it))
        }
        countCards += cards.size
    }

    fun close() {
        logger.info{ "wrote $countCards cards"}
        writer.close()
    }
}

/////////////////////////////////////////////////////////
class AuditableCardCsvReader(val filename: String): Iterable<AuditableCard> {
    override fun iterator(): Iterator<AuditableCard> {
        return readCardsCsvIterator(filename)
    }
}

class AuditableCardCsvReaderSkip(val filename: String, val skip: Int): Iterable<AuditableCard> {
    override fun iterator(): Iterator<AuditableCard> {
        val iter = readCardsCsvIterator(filename)
        repeat(skip) { if (iter.hasNext()) (iter.next()) }
        return iter
    }
}

fun readAuditableCardCsv(line: String): AuditableCard {
    val tokens = line.split(",")
    val ttokens = tokens.map { it.trim() }

    var idx = 0
    val desc = ttokens[idx++]
    val index = ttokens[idx++].toInt()
    val sampleNum = ttokens[idx++].toLong()
    val phantom = ttokens[idx++] == "true"
    val poolIdToken = ttokens[idx++]
    val poolId = if (poolIdToken.isEmpty()) null else poolIdToken.toInt()
    val contestsStr = ttokens[idx++]
    val contestsTokens = contestsStr.split(" ")
    val contests = contestsTokens.map { it.trim().toInt() }.toIntArray()

    // detect trailing comma ?
    val hasVotes = (idx + contests.size) < ttokens.size
    val votes = if (!hasVotes) null else {
        val work = mutableListOf<IntArray>()
        while (idx < ttokens.size && (work.size < contests.size)) {
            val vtokens = ttokens[idx]
            val candArray =
                if (vtokens.isEmpty()) intArrayOf() else vtokens.split(" ").map { it.trim().toInt() }.toIntArray()
            work.add(candArray)
            idx++
        }
        require(contests.size == work.size) { "contests.size (${contests.size}) != votes.size (${work.size})" }
        work
    }

    return AuditableCard(desc, index, sampleNum, phantom, contests, votes, poolId)
}

fun readAuditableCardCsvFile(filename: String): List<AuditableCard> {
    val reader: BufferedReader = File(filename).bufferedReader()
    val header = reader.readLine() // get rid of header line

    val pools = mutableListOf<AuditableCard>()
    while (true) {
        val line = reader.readLine() ?: break
        pools.add(readAuditableCardCsv(line))
    }
    reader.close()
    return pools
}

fun readCardsCsvIterator(filename: String): Iterator<AuditableCard> {
    return if (filename.endsWith("zip")) {
        val reader = ZipReader(filename)
        val input = reader.inputStream()
        IteratorCardsCsvStream(input)
    } else {
        IteratorCardsCsvFile(filename)
    }
}

class IteratorCardsCsvStream(input: InputStream): Iterator<AuditableCard> {
    val reader = BufferedReader(InputStreamReader(input, "ISO-8859-1"))
    var nextLine: String? = reader.readLine() // get rid of header line

    var countLines  = 0
    override fun hasNext() : Boolean {
        countLines++
        nextLine = reader.readLine()
        return nextLine != null
    }

    override fun next(): AuditableCard {
        return readAuditableCardCsv(nextLine!!)
    }

    fun close() {
        logger.info{"read $countLines lines"}
        reader.close()
    }
}

class IteratorCardsCsvFile(filename: String): Iterator<AuditableCard> {
    val reader: BufferedReader = File(filename).bufferedReader()
    var nextLine: String? = reader.readLine() // get rid of header line

    var countLines  = 0
    override fun hasNext() : Boolean {
        countLines++
        nextLine = reader.readLine()
        return nextLine != null
    }

    override fun next(): AuditableCard {
        return readAuditableCardCsv(nextLine!!)
    }

    fun close() {
        logger.info{"read $countLines lines"}
        reader.close()
    }
}



