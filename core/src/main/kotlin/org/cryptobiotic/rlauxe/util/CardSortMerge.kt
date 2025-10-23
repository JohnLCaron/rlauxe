package org.cryptobiotic.rlauxe.util

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.persist.csv.*
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import java.nio.file.*

private val maxChunkDefault = 100000
private val logger = KotlinLogging.logger("SortMerge")

// from Iterator<T>, convert to AuditableCard, assign prn, external sort and write sortedCards.
class SortMerge<T>(
    val scratchDirectory: String,
    val outputFile: String,
    val seed: Long,
    val maxChunk: Int = maxChunkDefault) {

    fun run(cardIter: CloseableIterator<T>, cvrs: List<Cvr>, toAuditableCard: (from: T, index: Int, prn: Long) -> AuditableCard) {
        // out of memory sort by sampleNum()
        sortCards(cardIter, cvrs, scratchDirectory, seed, toAuditableCard)
        mergeCards(scratchDirectory, outputFile)
    }

    // out of memory sorting
    fun sortCards(
        cardIterator: CloseableIterator<T>,
        cvrs: List<Cvr>, // phantoms
        scratchDirectory: String,
        seed: Long,
        toAuditableCard: (from: T, index: Int, prn: Long) -> AuditableCard
    ) {
        clearDirectory(Path.of(scratchDirectory))
        validateOutputDir(Path.of(scratchDirectory), ErrorMessages("sortCards"))

        val prng = Prng(seed)
        val cardSorter = ChunkSorter<T>(scratchDirectory, prng, maxChunk, toAuditableCard)

        cardIterator.use { cardIter ->
            while (cardIter.hasNext()) {
                cardSorter.add(cardIter.next())
            }
            cvrs.forEach { cardSorter.add(it) }
        }

        cardSorter.writeSortedChunk()
    }

    fun mergeCards(
        workingDirectory: String,
        outputFile: String,
    ) {
        //// the merging of the sorted chunks, and writing the completely sorted file
        val writer = AuditableCardCsvWriter(outputFile)

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
                             val toAuditableCard: (from: T, index: Int, prn: Long) -> AuditableCard) {
    var index = 0
    var count = 0
    val cards = mutableListOf<AuditableCard>()
    var countChunks = 0

    fun add(from: T) {
        val card = toAuditableCard(from, index, prng.next()) // , false, pools = pools, showPoolVotes)
        cards.add(card)
        index++
        count++

        if (count > max) {
            writeSortedChunk()
        }
    }

    fun add(cvr: Cvr) {
        val card = AuditableCard.fromCvr(cvr, index=index, sampleNum=prng.next())
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
        writeAuditableCardCsvFile(sortedCards, filename)
        println("write $filename")
        cards.clear()
        count = 0
        countChunks++
    }
}

private class CardMerger(chunkFilenames: List<String>, val writer: AuditableCardCsvWriter, val maxChunk: Int) {
    val nextUps = chunkFilenames.map { NextUp(IteratorCardsCsvFile(it)) }
    val cards = mutableListOf<AuditableCard>()
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

    class NextUp(val nextIter: CloseableIterator<AuditableCard>) {
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
                nextIter.close()
            }
        }
    }

}