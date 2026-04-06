package org.cryptobiotic.rlauxe.persist.csv

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.CardIF
import org.cryptobiotic.rlauxe.audit.CardWithBatchName
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.ZipReader
import org.cryptobiotic.rlauxe.util.emptyCloseableIterator
import java.io.*
import java.nio.file.Files
import kotlin.io.path.Path

private val logger = KotlinLogging.logger("CardCsv")

// interface CardIF {
//    fun location(): String // enough info to find the card for a manual audit.
//    fun index(): Int  // index into the original, canonical list of cards
//    fun prn(): Long   // psuedo random number
//    fun isPhantom(): Boolean
//
//    fun votes(): Map<Int, IntArray>?   // CVRs and phantoms
//    fun poolId(): Int?                 // must be set if its from a CardPool  TODO verify batch name, poolId
//    fun styleName(): String            // "fromCvr" if no batch and its from a CVR (then votes is non null)
//}

val CardHeader = "id, location, index, prn, phantom, poolId, cardStyle, cvr contests, candidates0, candidates1, ...\n"

fun writeCardCsv(card: CardIF) = buildString {
    append("${card.id()}, ")
    if (card.id() == card.location()) append(", ") else append("${card.location()}, ")
    append("${card.index()}, ${card.prn().toString(radix=16)}, ${if(card.isPhantom()) "yes," else ","} ")
    if (card.poolId() == null) append(", ") else append("${card.poolId()}, ")
    append("${card.styleName()}, ")

    if (card.votes() != null) {
        val votes = card.votes()!!
        val contests = votes.map { it.key }.toIntArray()
        val candidates = votes.map { it.value }
        append("${contests.joinToString(" ")}, ")
        candidates.forEach { append("${it.joinToString(" ")}, ") }
    }
    appendLine()
}

fun writeCardCsvFile(cards: List<CardIF>, outputFilename: String) {
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    writer.write(CardHeader)
    cards.forEach {
        writer.write(writeCardCsv(it))
    }
    writer.close()
}

fun writeCardCsvFile(cards: CloseableIterator<CardIF>, outputFilename: String): Int {
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    writer.write(CardHeader)
    var count = 0
    cards.use { cardIter ->
        while (cardIter.hasNext()) {
            writer.write(writeCardCsv(cardIter.next()))
            count++
        }
    }
    writer.close()
    return count
}

class CardCsvWriter(outputFilename: String) {
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    var countCards = 0
    init {
        writer.write(CardHeader)
    }

    fun write(cards: List<CardIF>) {
        cards.forEach {
            writer.write(writeCardCsv(it))
        }
        countCards += cards.size
    }

    fun close() {
        writer.close()
    }
}

/////////////////////////////////////////////////////////

fun readCardCsv(line: String): CardWithBatchName {
    val tokens = line.split(",")
    val ttokens = tokens.map { it.trim() }

    var idx = 0
    val id = ttokens[idx++]
    val locationToken = ttokens[idx++]
    val location = locationToken.ifEmpty { null }
    val index = ttokens[idx++].toInt()
    val sampleNum = ttokens[idx++].toLong(radix=16)
    val phantom = ttokens[idx++] == "yes"
    val poolIdToken = ttokens[idx++]
    val poolId = if (poolIdToken.isEmpty()) null else poolIdToken.toInt()
    val styleName = ttokens[idx++].trim()

    // if clca, list of actual contests and their votes
    if (idx < ttokens.size-1) {
        val contestsStr = ttokens[idx++].trim()
        val contestsTokenTrimmed = contestsStr.split(" ").map { it.trim() }

        val contests = mutableListOf<Int>()
        contestsTokenTrimmed.forEach { tok ->
            if (tok.isNotEmpty()) contests.add(tok.toInt())
        }

        // detect trailing comma ?
        val hasVotes = (idx + contests.size) < ttokens.size
        val votes = if (!hasVotes) null else {
            val work = mutableListOf<IntArray>()
            while (idx < ttokens.size && (work.size < contests.size)) {
                val vtokens = ttokens[idx]
                val candArray =
                    if (vtokens.isEmpty()) intArrayOf()
                    else vtokens.split(" ").map { it.trim().toInt() }.toIntArray()
                work.add(candArray)
                idx++
            }
            require(contests.size == work.size) { "contests.size (${contests.size}) != votes.size (${work.size})" }
            contests.zip(work).toMap()
        }
        return CardWithBatchName(id, location, index, sampleNum, phantom, votes, poolId, styleName=styleName)
    }
    return CardWithBatchName(id, location, index, sampleNum, phantom, null, poolId, styleName=styleName)
}

class CardCsvReader(filename: String): CloseableIterable<CardWithBatchName> {
    var useFilename = if (Files.exists(Path(filename))) filename
        else if (Files.exists(Path("$filename.zip"))) "$filename.zip" // TODO unzip and leave it unzipped
        else throw RuntimeException("CardsCsvFile $filename or $filename.zip does not exist")

    override fun iterator(): CloseableIterator<CardWithBatchName> {
        return readCardsCsvIterator(useFilename)
    }
}

fun readCardCsvFile(filename: String): List<CardWithBatchName> {
    val reader: BufferedReader = File(filename).bufferedReader()
    reader.readLine() // get rid of header line

    val cards = mutableListOf<CardWithBatchName>()
    while (true) {
        val line = reader.readLine() ?: break
        cards.add(readCardCsv(line))
    }
    reader.close()
    return cards
}

fun readCardsCsvIterator(filename: String): CloseableIterator<CardWithBatchName> {
    val useFilename: String = if (Files.exists(Path(filename))) filename
    else if (Files.exists(Path("$filename.zip"))) "$filename.zip" // TODO unzip
    else {
        logger.warn { "readCardsCsvIterator $filename or $filename.zip does not exist"}
        return emptyCloseableIterator()
    } // throw RuntimeException("readCardsCsvIterator $filename or $filename.zip does not exist")

    return if (useFilename.endsWith(".zip")) {
        val reader = ZipReader(useFilename)
        val input = reader.inputStream()
        IteratorCardsCsvStream(input)
    } else {
        IteratorCardsCsvStream(File(filename).inputStream())
    }
}

class IteratorCardsCsvStream(input: InputStream): CloseableIterator<CardWithBatchName> {
    // was val reader = BufferedReader(InputStreamReader(input, "ISO-8859-1")) for some reason
    val reader = BufferedReader(InputStreamReader(input))
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

    override fun next(): CardWithBatchName {
        if (!hasNext()) throw NoSuchElementException()
        val result =  readCardCsv(nextLine!!)
        nextLine = null
        return result
    }

    override fun close() {
        reader.close()
    }
}



