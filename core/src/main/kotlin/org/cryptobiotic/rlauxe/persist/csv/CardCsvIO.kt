package org.cryptobiotic.rlauxe.persist.csv

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.ZipReader
import org.cryptobiotic.rlauxe.util.emptyCloseableIterator
import java.io.*
import java.nio.file.Files
import kotlin.io.path.Path

private val logger = KotlinLogging.logger("CardCsv")

val CardHeader = "id, location, index, prn, phantom, styleId, poolId, cvr contests, candidates0, candidates1, ...\n"

fun writeCardCsv(card: AuditableCard) = buildString {
    append("${card.id()}, ")
    if (card.id() == card.location()) append(", ") else append("${card.location()}, ")
    append("${card.index()}, ${card.prn().toString(radix=16)}, ${if(card.phantom()) "yes," else ","} ")
    append("${card.styleId}, ")
    if (card.poolId() == null) append(", ") else append("${card.poolId()}, ")

    if (card.votes() != null) {
        val votes = card.votes()!!
        val contests = votes.map { it.key }.toIntArray()
        val candidates = votes.map { it.value }
        append("${contests.joinToString(" ")}, ")
        candidates.forEach { append("${it.joinToString(" ")}, ") }
    }
    appendLine()
}

fun writeCardCsvFile(cards: List<AuditableCard>, outputFilename: String) {
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    writer.write(CardHeader)
    cards.forEach {
        writer.write(writeCardCsv(it))
    }
    writer.close()
}

fun writeCardCsvFile(cards: CloseableIterator<AuditableCard>, outputFilename: String): Int {
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

    fun write(cards: List<AuditableCard>) {
        cards.forEach {
            writer.write(writeCardCsv(it))
        }
        countCards += cards.size
    }

    fun close() {
        writer.close()
    }
}

////////////////////////////////////////////////////////////

fun readCardCsv(line: String): AuditableCard {
    val tokens = line.split(",")
    val ttokens = tokens.map { it.trim() }

    try {
        var idx = 0
        val id = ttokens[idx++]
        val locationToken = ttokens[idx++]
        val location = locationToken.ifEmpty { null }
        val index = ttokens[idx++].toInt()
        val sampleNum = ttokens[idx++].toLong(radix = 16)
        val phantom = ttokens[idx++] == "yes"
        val styleId = ttokens[idx++].trim().toInt()
        val poolIdToken = ttokens[idx++]
        val poolId = if (poolIdToken.isEmpty()) null else poolIdToken.toInt()

        // if clca, list of actual contests and their votes
        if (idx < ttokens.size - 1) {
            val contestsStr = ttokens[idx++].trim()
            val contestsTokenTrimmed = contestsStr.split(" ").map { it.trim() }

            val contestIds = mutableListOf<Int>()
            contestsTokenTrimmed.forEach { tok ->
                if (tok.isNotEmpty()) contestIds.add(tok.toInt())
            }

            val candidates = mutableListOf<Int>()
            val contestStarts = mutableListOf<Int>()
            var start = 0

            contestIds.forEach {
                contestStarts.add(start)
                val vtokens = ttokens[idx++]
                val cands: List<Int> =
                    if (vtokens.isEmpty()) emptyList()
                    else vtokens.split(" ").map { it.trim().toInt() }
                candidates.addAll(cands)
                start += cands.size
            }
            return AuditableCard(
                id, location, index, sampleNum, phantom, styleId,
                contestIds.toIntArray(), contestStarts.toIntArray(), candidates.toIntArray(), poolId,
            )
        }
        return AuditableCard(id, location, index, sampleNum, phantom, styleId,
            intArrayOf(), intArrayOf(), intArrayOf(), poolId,)

    } catch (e: Throwable) {
        logger.error(e) { "Error while reading $line tokens=$tokens" }
        throw e
    }

}

class IteratorCardsCsvStream(input: InputStream, bufferSize: Int, val styles: List<StyleIF>?): CloseableIterator<AuditableCard> {
    val styleMap = if (styles == null) null else styles.associateBy { it.id() }
    val reader = BufferedReader(InputStreamReader(input),bufferSize)
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

    override fun next(): AuditableCard {
        if (!hasNext()) throw NoSuchElementException()
        val cardm: AuditableCard =  readCardCsv(nextLine!!)
        val style = styleMap?.get(cardm.styleId)
        if (style != null)
            cardm.setStyle(style)
        nextLine = null
        return cardm
    }

    override fun close() {
        reader.close()
    }
}

class CardCsvReader(filename: String, val styles: List<StyleIF>?): CloseableIterable<AuditableCard> {
    var useFilename = if (Files.exists(Path(filename))) filename
    else if (Files.exists(Path("$filename.zip"))) "$filename.zip" // TODO unzip and leave it unzipped
    else throw RuntimeException("CardsCsvFile $filename or $filename.zip does not exist")

    override fun iterator(): CloseableIterator<AuditableCard> {
        return readCardsCsvIterator(useFilename, styles)
    }
}

// replace readCardsCsvIterator
fun readCardsCsvIterator(csvFile: String, styles: List<StyleIF>?): CloseableIterator<AuditableCard> {
    val useFilename: String = if (Files.exists(Path(csvFile))) csvFile
    else if (Files.exists(Path("$csvFile.zip"))) "$csvFile.zip" // TODO unzip
    else {
        println("readAndMergeCards $csvFile or $csvFile.zip does not exist")
        return emptyCloseableIterator()
    }

    // TODO time with and without zip
    return if (useFilename.endsWith(".zip")) {
        val reader = ZipReader(useFilename)
        val input = reader.inputStream()
        IteratorCardsCsvStream(input, 8192, styles)
    } else {
        IteratorCardsCsvStream(File(useFilename).inputStream(), 8192, styles)
    }
}


fun readCardsAndMergeToList(filename: String, styles: List<StyleIF>?): List<AuditableCard> {
    val mergedMvrIter = readCardsCsvIterator(filename, styles)
    val mvrCards = mutableListOf<AuditableCard>()
    while (mergedMvrIter.hasNext()) { mvrCards.add(mergedMvrIter.next())}
    return mvrCards
}






