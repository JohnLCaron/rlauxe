package org.cryptobiotic.rlauxe.timing

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.protobuf.writeProtoCards
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test

// Too slow for unit tests
class TestWriteFiles {
    val tempProtoFile = "$testdataDir/temp/sortedCards.proto"

    @Test
    fun writeProtoFile () {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val publisher = Publisher("$topdir")
        val cardIter: CloseableIterator<AuditableCard> = readCardsCsvIterator(publisher.sortedCardsFile(), styles=null)

        val stopwatch = Stopwatch()
        val ncards = writeProtoCards(cardIter, tempProtoFile)
        println("writeProtoFile ncards = $ncards, took $stopwatch")
    }

}