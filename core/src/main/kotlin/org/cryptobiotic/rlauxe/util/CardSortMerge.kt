package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.CvrExport
import org.cryptobiotic.rlauxe.persist.csv.*
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import java.nio.file.*

private val maxChunkDefault = 100000

// was
// class SortMerge(
//    val auditDir: String,
//    val cvrExportCsv: String, // open with cvrExportCsvIterator; contains CvrExport objects
//    val workingDir: String,
//    val outputFile: String,
//    val pools: Map<String, Int>?) {

// from Iterator<CvrExport>, convert to AuditableCard, assign prn, sort and write sortedCards.
class SortMerge(
    val scratchDirectory: String,
    val outputFile: String,
    val seed: Long,
    val pools: Map<String, Int>? = null,
    val maxChunk: Int = maxChunkDefault) {

    // cvrExportCsv: open with cvrExportCsvIterator; contains CvrExport objects
    fun run(cvrExportCsv: String) {
        // out of memory sort by sampleNum()
        sortCards(cvrExportCsv, scratchDirectory, seed, pools = pools)
        mergeCards(scratchDirectory, outputFile)
    }

    fun run2(cardIter: CloseableIterator<CvrExport>) {
        // out of memory sort by sampleNum()
        sortCards2(cardIter, scratchDirectory, seed, pools = pools)
        mergeCards(scratchDirectory, outputFile)
    }


    // out of memory sorting
    fun sortCards(
        cvrExportCsv: String, // may be zipped or not
        scratchDirectory: String,
        seed: Long,
        pools: Map<String, Int>?
    ) {
        val stopwatch = Stopwatch()

        clearDirectory(Path.of(scratchDirectory))
        validateOutputDir(Path.of(scratchDirectory), ErrorMessages("sortCards"))

        val prng = Prng(seed)
        val cardSorter = CardSorter(scratchDirectory, prng, maxChunk, pools = pools)

        //// reading CvrExport and sorted chunks
        cvrExportCsvIterator(cvrExportCsv).use { cardIter ->
            while (cardIter.hasNext()) {
                cardSorter.add(cardIter.next())
            }
        }
        cardSorter.writeSortedChunk()
        println("writeSortedChunk took $stopwatch")
    }

    fun sortCards2(
        cardIter: CloseableIterator<CvrExport>,
        scratchDirectory: String,
        seed: Long,
        pools: Map<String, Int>?
    ) {
        val stopwatch = Stopwatch()

        clearDirectory(Path.of(scratchDirectory))
        validateOutputDir(Path.of(scratchDirectory), ErrorMessages("sortCards"))

        val prng = Prng(seed)
        val cardSorter = CardSorter(scratchDirectory, prng, maxChunk, pools = pools)

        //// reading CvrExport and sorted chunks
        while (cardIter.hasNext()) {
            cardSorter.add(cardIter.next())
        }
        cardSorter.writeSortedChunk()
        println("writeSortedChunk took $stopwatch")
    }

    fun mergeCards(
        workingDirectory: String,
        outputFile: String,
    ) {
        val stopwatch = Stopwatch()

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
        println("mergeSortedChunk took $stopwatch")
    }
}

class CardSorter(val workingDirectory: String, val prng: Prng, val max: Int, val pools: Map<String, Int>?) {
    var index = 0
    var count = 0
    val cards = mutableListOf<AuditableCard>()
    var countChunks = 0

    fun add(card: CvrExport) {
        // TODO phantoms
        val card = card.toAuditableCard(index=index, prn=prng.next(), false, pools = pools)
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

class CardMerger(chunkFilenames: List<String>, val writer: AuditableCardCsvWriter, val maxChunk: Int) {
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