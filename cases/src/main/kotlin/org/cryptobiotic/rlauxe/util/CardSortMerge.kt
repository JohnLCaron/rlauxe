package org.cryptobiotic.rlauxe.util

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.persist.csv.*
import org.cryptobiotic.rlauxe.persist.json.Publisher
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.validateOutputDir
import java.nio.file.*

private val maxChunk = 100000

fun sortMergeCards(
    auditDir: String,
    cardFile: String,
) {
    // out of memory sort by sampleNum()
    sortCards(auditDir, cardFile, "$auditDir/sortChunks")
    mergeCards(auditDir, "$auditDir/sortChunks")
}

// out of memory sorting from directory
fun sortCardsInDirectoryTree(
    auditDir: String,
    cardDirectory: String,
    workingDirectory: String,
) {
    val stopwatch = Stopwatch()
    val publisher = Publisher(auditDir)
    val auditConfig = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
    validateOutputDir(Path.of(workingDirectory), ErrorMessages("sortCards"))

    val prng = Prng(auditConfig.seed)
    val cardSorter = CardSorter(workingDirectory, prng, maxChunk)

    //// the reading and sorted chunks
    val cardIter: Iterator<AuditableCard> = TreeReaderIterator(
        cardDirectory,
        fileFilter = { true },
        reader = { path -> readCardsCsvIterator(path.toString()) }
    )
    while (cardIter.hasNext()) {
        cardSorter.add(cardIter.next())
    }
    cardSorter.writeSortedChunk()
    println("writeSortedChunk took $stopwatch")
}

// out of memory sorting
fun sortCards(
    auditDir: String,
    cardFile: String, // may be zipped or not
    workingDirectory: String,
) {
    val stopwatch = Stopwatch()
    val publisher = Publisher(auditDir)
    val auditConfig = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
    validateOutputDir(Path.of(workingDirectory), ErrorMessages("sortCards"))

    val prng = Prng(auditConfig.seed)
    val cardSorter = CardSorter(workingDirectory, prng, maxChunk)

    //// the reading and sorted chunks
    val cardIter: Iterator<AuditableCard> = readCardsCsvIterator(cardFile)
    while (cardIter.hasNext()) {
        cardSorter.add(cardIter.next())
    }
    cardSorter.writeSortedChunk()
    println("writeSortedChunk took $stopwatch")
}

fun mergeCards(
    auditDir: String,
    workingDirectory: String,
) {
    val stopwatch = Stopwatch()

    //// the merging of the sorted chunks, and writing the completely sorted file
    val writer = AuditableCardCsvWriter("$auditDir/sortedCards.csv")

    val paths = mutableListOf<String>()
    Files.newDirectoryStream(Path.of(workingDirectory)).use { stream ->
        for (path in stream) {
            paths.add(path.toString())
        }
    }
    val merger = CardMerger(paths, writer)
    merger.merge()
    println("mergeSortedChunk took $stopwatch")
}

class CardSorter(val workingDirectory: String, val prng: Prng, val max: Int) {
    var index = 0
    var count = 0
    val cards = mutableListOf<AuditableCard>()
    var countChunks = 0

    fun add(card: AuditableCard) {
        cards.add(card.copy(index=index, prn=prng.next()))
        index++
        count++

        if (count > max) {
            writeSortedChunk()
        }
    }

    fun writeSortedChunk() {
        val sortedCards = cards.sortedBy { it.prn }
        val filename = "$workingDirectory/sorted-cards-part-${countChunks}.csv"
        writeAuditableCardCsvFile(sortedCards, filename)
        println("write $filename")
        cards.clear()
        count = 0
        countChunks++
    }
}

class CardMerger(chunkFilenames: List<String>, val writer: AuditableCardCsvWriter) {
    val nextUps = chunkFilenames.map { NextUp(IteratorCardsCsvFile(it)) }
    val cards = mutableListOf<AuditableCard>()
    var total = 0

    fun merge() {
        var moar = true
        while (moar) {
            val nextUppers = nextUps.map { it.sampleNumber }
            val nextUp = nextUps.minBy { it.sampleNumber }
            cards.add ( nextUp.currentCard!! )
            nextUp.pop()
            if (cards.size >= maxChunk) writeMergedCards()
            moar = nextUps.any { it.currentCard != null }
        }
        writeMergedCards()
        writer.close()
    }

    fun writeMergedCards() {
        writer.write(cards)
        total += cards.size
        println("write ${cards.size} total = $total")
        cards.clear()
    }

    class NextUp(val nextIter: Iterator<AuditableCard>) {
        var currentCard: AuditableCard? = null
        var sampleNumber = Long.MAX_VALUE

        init {
            pop()
        }

        fun pop() {
            if (nextIter.hasNext()) {
                currentCard = nextIter.next()
                sampleNumber = currentCard!!.prn
            } else {
                currentCard = null
                sampleNumber = Long.MAX_VALUE
            }
        }
    }

}