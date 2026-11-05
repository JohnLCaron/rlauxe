package org.cryptobiotic.timing

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.protobuf.writeProtoCards
import org.cryptobiotic.rlauxe.util.Locations.testdataDir
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test

class TestWriteFiles {
    val tempProtoFile = "$testdataDir/sortedCards.proto"

    @Test
    fun writeProtoFile () {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val publisher = Publisher("$topdir")
        val cardIter: CloseableIterator<AuditableCard> = readCardsCsvIterator(publisher.sortedCardsFile()) { it }

        val stopwatch = Stopwatch()
        val ncards = writeProtoCards(cardIter, tempProtoFile)
        println("writeProtoFile ncards = $ncards, took $stopwatch")
    }

}