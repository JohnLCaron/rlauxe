package org.cryptobiotic.rlauxe.util

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.CardWithBatchName
import org.cryptobiotic.rlauxe.persist.csv.*
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import java.nio.file.*

private val maxChunkDefault = 100000
private val logger = KotlinLogging.logger("SortMerge")

// from Iterator<T>, convert to CardWithBatchName, assign prn, external sort and write sortedCards.
class SortMerge<T>(
    val scratchDirectory: String,
    val outputFile: String,
    val seed: Long,
    val maxChunk: Int = maxChunkDefault) {

    fun run(cardIter: CloseableIterator<T>, toCard: (from: T, index: Int, prn: Long) -> CardWithBatchName) {
        // out of memory sort by sampleNum()
        sortCards(cardIter, scratchDirectory, seed, toCard)
        mergeCards(scratchDirectory, outputFile)
    }

    // out of memory sorting
    fun sortCards(
        cardIterator: CloseableIterator<T>,
        scratchDirectory: String,
        seed: Long,
        toCard: (from: T, index: Int, prn: Long) -> CardWithBatchName
    ) {
        clearDirectory(Path.of(scratchDirectory))
        validateOutputDir(Path.of(scratchDirectory), ErrorMessages("sortCards"))

        val prng = Prng(seed)
        val cardSorter = ChunkSorter<T>(scratchDirectory, prng, maxChunk, toCard)

        cardIterator.use { cardIter ->
            while (cardIter.hasNext()) {
                cardSorter.add(cardIter.next())
            }
        }

        cardSorter.writeSortedChunk()
    }

    fun mergeCards(
        workingDirectory: String,
        outputFile: String,
    ) {
        //// the merging of the sorted chunks, and writing the completely sorted file
        val writer = CardCsvWriter(outputFile)

        val paths = mutableListOf<String>()
        Files.newDirectoryStream(Path.of(workingDirectory)).use { stream ->
            for (path in stream) {
                paths.add(path.toString())
            }
        }
        val merger = CardMerger(paths, writer, maxChunk)
        merger.merge()
        logger.info{"SortMerge wrote ${writer.countCards} to $outputFile"}
    }
}

private class ChunkSorter<T>(val workingDirectory: String, val prng: Prng, val max: Int,
                             val toCard: (from: T, index: Int, prn: Long) -> CardWithBatchName) {
    var index = 0
    var count = 0
    val cards = mutableListOf<CardWithBatchName>()
    var countChunks = 0

    fun add(from: T) {
        val card = toCard(from, index, prng.next()) // , false, pools = pools, showPoolVotes)
        cards.add(card)
        index++
        count++

        if (count > max) {
            writeSortedChunk()
        }
    }

    fun writeSortedChunk() {
        val sortedCards = cards.sortedBy { it.prn }
        val filename = "$workingDirectory/sorted-cards-part-${countChunks}.csv"
        writeCardCsvFile(sortedCards, filename)
        println("write $filename")
        cards.clear()
        count = 0
        countChunks++
    }
}

private class CardMerger(chunkFilenames: List<String>, val writer: CardCsvWriter, val maxChunk: Int) {
    val nextUps = chunkFilenames.map { NextUp(readCardsCsvIterator(it)) }
    val cards = mutableListOf<CardWithBatchName>()
    var total = 0

    fun merge() {
        var moar = true
        while (moar) {
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

    class NextUp(val nextIter: CloseableIterator<CardWithBatchName>) {
        var currentCard: CardWithBatchName? = null
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
                nextIter.close()
            }
        }
    }

}