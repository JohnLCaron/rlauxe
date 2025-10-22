package org.cryptobiotic.rlauxe.persist.csv

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrExport
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.ZipReader
import java.io.*
import java.nio.file.Files
import kotlin.io.path.Path

private val logger = KotlinLogging.logger("AuditableCardCsv")

// data class AuditableCard (
//    val location: String, // info to find the card for a manual audit. Aka ballot identifier.
//    val index: Int,  // index into the original, canonical list of cards
//    val prn: Long,   // psuedo random number
//    val phantom: Boolean,
//    val contests: IntArray, // list of contests on this ballot. TODO optional when !hasStyles ??
//    val votes: List<IntArray>?, // contest -> list of candidates voted for; for IRV, ranked first to last; missing for pooled data
//    val poolId: Int?, // for OneAudit
//)

val AuditableCardHeader = "location, index, prn, phantom, poolId, contests, candidates0, candidates1, ...\n"

fun writeAuditableCardCsv(card: AuditableCard) = buildString {
    append("${card.location}, ${card.index}, ${card.prn}, ${card.phantom}, ")
    if (card.poolId == null) append(", ") else append("${card.poolId}, ")
    append("${card.contests.joinToString(" ")}, ")
    if (card.votes != null)
        card.votes.forEach { candidates -> append("${candidates.joinToString(" ")}, ")}
    appendLine()
}

fun writeAuditableCardCsvFile(cards: List<AuditableCard>, outputFilename: String) {
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    writer.write(AuditableCardHeader)
    cards.forEach {
        writer.write(writeAuditableCardCsv(it))
    }
    writer.close()
}

fun writeAuditableCardCsvFile(cards: CloseableIterator<AuditableCard>, outputFilename: String): Int {
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    writer.write(AuditableCardHeader)
    var count = 0
    cards.use { cardIter ->
        while (cardIter.hasNext()) {
            writer.write(writeAuditableCardCsv(cardIter.next()))
            count++
        }
    }
    writer.close()
    return count
}

class AuditableCardCsvWriter(outputFilename: String) {
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
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
        writer.close()
    }
}

/////////////////////////////////////////////////////////

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

class AuditableCardCsvReader(filename: String): CloseableIterable<AuditableCard> {
    val useFilename: String = if (Files.exists(Path("$filename.zip"))) "$filename.zip"
    else if (Files.exists(Path(filename))) filename
    else throw RuntimeException("CardsCsvFile $filename or $filename.zip does not exist")

    override fun iterator(): CloseableIterator<AuditableCard> {
        return readCardsCsvIterator(useFilename)
    }
}

class AuditableCardCsvReaderSkip(val filename: String, val skip: Int): CloseableIterable<AuditableCard> {
    override fun iterator(): CloseableIterator<AuditableCard> {
        val iter = readCardsCsvIterator(filename)
        repeat(skip) { if (iter.hasNext()) (iter.next()) }
        return iter
    }
}

fun auditableCardCsvIterator(filename: String): CloseableIterator<AuditableCard> {
    return if (filename.endsWith("zip")) {
        val reader = ZipReader(filename)
        val input = reader.inputStream()
        IteratorCardsCsvStream(input)
    } else {
        Closer(readAuditableCardCsvFile(filename).iterator() )
    }
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

fun readCardsCsvIterator(filename: String): CloseableIterator<AuditableCard> {
    val useFilename: String = if (Files.exists(Path("$filename.zip"))) "$filename.zip"
    else if (Files.exists(Path(filename))) filename
    else throw RuntimeException("CardsCsvFile $filename or $filename.zip does not exist")

    return if (useFilename.endsWith(".zip")) {
        val reader = ZipReader(useFilename)
        val input = reader.inputStream()
        IteratorCardsCsvStream(input)
    } else {
        IteratorCardsCsvFile(useFilename)
    }
}

class IteratorCardsCsvStream(input: InputStream): CloseableIterator<AuditableCard> {
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

    override fun close() {
        reader.close()
    }
}

class IteratorCardsCsvFile(filename: String): CloseableIterator<AuditableCard> {
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

    override fun close() {
        reader.close()
    }
}

class AuditableCardToCvrAdapter(val cardIterator: CloseableIterator<AuditableCard>) : CloseableIterator<Cvr> {
    override fun hasNext() = cardIterator.hasNext()
    override fun next() = cardIterator.next().cvr()
    override fun close() {
        cardIterator.close()
    }
}



