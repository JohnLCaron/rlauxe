package org.cryptobiotic.rlauxe.persist.protobuf

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.CardCsvReader
import org.cryptobiotic.rlauxe.persist.json.readCardStylesJsonFile
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.dfn
import java.util.concurrent.TimeUnit
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestProtoCard {

    @Test
    fun testProtoAndCsvAgree () {
        val topdir = "$testdataDir/cases/auditcenter/County2024OnlyTeller"
        val publisher = Publisher("$topdir/audit")
        val bufferSize = 100_000

        val styles = readCardStylesJsonFile(publisher.cardStylesFile()).unwrap()
        val csvIter = CardCsvReader(publisher.sortedCardsFile(), styles).iterator()

        val stopwatch = Stopwatch()
        var ncards = 0L

        val protoIter: CloseableIterator<AuditableCard> = ProtoCardIterator(publisher.sortedCardsProtoFile(), styles = styles)
        while (protoIter.hasNext() && csvIter.hasNext() && ncards < 100_000) {
            val cardFromCsv = csvIter.next()
            val cardFromProto = protoIter.next()
            assertTrue(checkEqual(cardFromProto, cardFromCsv))
            ncards++
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ncards.toDouble()
        val secPer = msPer / 1000

        println("timeReadCsv ($bufferSize):  ncards = $ncards, took $stopwatch = $msPer ms/card")
        val totalCards = 4982747
        println("time to read all cards = ${dfn(totalCards * secPer, 3)} secs")
    }

}

fun checkEqual(c1: AuditableCard, c2: AuditableCard): Boolean {
    assertEquals(c1.id(), c2.id())
    assertEquals(c1.location(), c2.location())
    assertEquals(c1.index(), c2.index())
    assertEquals(c1.prn(), c2.prn())
    assertEquals(c1.poolId(), c2.poolId())
    assertEquals(c1.styleId, c2.styleId)

    assertEquals(c1.votes() == null, c2.votes() == null)
    if (c1.votes() != null) {
        val c1votes = c1.votes()!!
        c1votes.forEach { (contestId, candidates) ->
            val otherCands = c2.votes(contestId)
            assertTrue(candidates.contentEquals(otherCands))
        }
    }
    return true
}
