package org.cryptobiotic.rlauxe.persist.bin

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.audit.makeFastCards
import org.cryptobiotic.rlauxe.estimate.COUNTY
import org.cryptobiotic.rlauxe.util.Locations.cases
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.protobuf.ProtoCardIterator
import org.cryptobiotic.rlauxe.util.Locations.testdataDir
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

// dont run in tests
class TestFastSamplingCards {
    val topdir = "${cases}/corla/2020/"

    val testFastSamplingFile = "$testdataDir/fastSampling.bin"
    val testFastSamplingFile1 = "$testdataDir/fastSampling1.bin"
    val testFastSamplingFile2 = "$testdataDir/fastSampling2.bin"

    @Test
    fun writeSamplingCards() {
        val countyAudit = AuditRecord.read(topdir) as CountyAuditRecord
        val mvrManager = PersistedMvrManager(countyAudit)
        val styles = mvrManager.styles()
        val cardManifest = countyAudit.readSortedManifest(styles)

        var cardIter = cardManifest.cardIterable.iterator()

        val stopwatch = Stopwatch()
        val ncards = writeFastSamplingCards(cardIter, styles, testFastSamplingFile)
        cardIter.close()

        println("writeSamplingCards ncards = $ncards, took $stopwatch")

        stopwatch.start()
        var count = 0
        cardIter = cardManifest.cardIterable.iterator()

        val fastIter = FastSamplingCardIterator(testFastSamplingFile, styles, 100_000)
        while (fastIter.hasNext() && cardIter.hasNext()) { //  && ncards < 1000_000) {
            val card = cardIter.next()
            val fastCard: FastSamplingCard = fastIter.next()
            assertEquals(card.style(), fastCard.style)
            assertEquals(card.prn(), fastCard.prn)
            count++
        }
        assertEquals(ncards, count)
        fastIter.close()
        cardIter.close()

        println("read and check FastSamplingCard ncards = $count, took $stopwatch")
    }

    @Test
    fun testPhantomCards() {
        val votes = mapOf( 42 to intArrayOf() )

        val phantomCard = AuditableCard.fromVotes(id = "testing123", COUNTY, location = "", index = 0, prn = 666L, phantom = true,
            styleId=CardStyle.phantomStyle.id, poolId = null, votes=votes).setStyle(CardStyle.phantomStyle)
        val cards = listOf(phantomCard)

        val stopwatch = Stopwatch()
        val ncards = writeFastSamplingCards(Closer(cards.iterator()), emptyList(), testFastSamplingFile1)

        val cardIter = FastSamplingCardIterator(testFastSamplingFile1, emptyList<StyleIF>(), 100)
        while (cardIter.hasNext()) { //  && ncards < 1000_000) {
            val card = cardIter.next()
            println(card)
            val wtf = card.style
            assertEquals(42, card.style.possibleContests()[0])
        }
    }

    @Test
    fun readSamplingCards() {
        val publisher = Publisher(topdir)

        val countyAudit = AuditRecord.read(topdir) as CountyAuditRecord
        val mvrManager = PersistedMvrManager(countyAudit)
        val styles = mvrManager.styles()

        val stopwatch = Stopwatch()
        var ncards = 0
        val cardIter = FastSamplingCardIterator(publisher.fastSamplingFile(), styles, 100_000)
        while (cardIter.hasNext()) { //  && ncards < 1000_000) {
            val card = cardIter.next()
            ncards++
        }
        cardIter.close()

        println("readSamplingCards ncards = $ncards, took $stopwatch")
    }

    @Test
    fun testMakeFastCards() {
        val auditRecord = AuditRecord.read(topdir) as AuditRecord
        val mvrManager = PersistedMvrManager(auditRecord)
        val styles = mvrManager.styles()

        val stopwatch = Stopwatch()
        val publisher = Publisher(topdir)
        val bufferSize = 100_000
        var protoIter = ProtoCardIterator(publisher.sortedCardsProtoFile(), bufferSize, styles)
        val ncards = writeFastSamplingCards(protoIter, styles, testFastSamplingFile2)

        println("testMakeFastCards ncards = $ncards that took $stopwatch= ${stopwatch.elapsed(TimeUnit.MILLISECONDS)/ncards.toDouble()} ms/card")

        var count = 0
        val fastIter = FastSamplingCardIterator(testFastSamplingFile2, styles, 100_000)
        protoIter = ProtoCardIterator(publisher.sortedCardsProtoFile(), bufferSize, styles)
        while (fastIter.hasNext() && protoIter.hasNext()) { //  && ncards < 1000_000) {
            val card = protoIter.next()
            val fastCard: FastSamplingCard = fastIter.next()
            assertEquals(card.style(), fastCard.style)
            assertEquals(card.prn(), fastCard.prn)
            count++
        }
        assertEquals(ncards, count)
        protoIter.close()
        fastIter.close()

        println("read and check FastSamplingCard ncards = $count, took $stopwatch")
    }

    // @Test
    fun remakeFastCards() {
        val auditRecord = AuditRecord.read(topdir) as AuditRecord
        val mvrManager = PersistedMvrManager(auditRecord)
        val styles = mvrManager.styles()

        val stopwatch = Stopwatch()
        val publisher = Publisher(topdir)
        val ncards = makeFastCards(publisher, styles)
        println("remakeFastCards ncards = $ncards that took $stopwatch= ${stopwatch.elapsed(TimeUnit.MILLISECONDS)/ncards.toDouble()} ms/card")

        var count = 0
        val fastIter = FastSamplingCardIterator(publisher.fastSamplingFile(), styles, 100_000)
        val protoIter = ProtoCardIterator(publisher.sortedCardsProtoFile(), 100_00, styles)
        while (fastIter.hasNext() && protoIter.hasNext()) { //  && ncards < 1000_000) {
            val card = protoIter.next()
            val fastCard: FastSamplingCard = fastIter.next()
            assertEquals(card.style(), fastCard.style)
            assertEquals(card.prn(), fastCard.prn)
            count++
        }
        assertEquals(ncards, count)
        protoIter.close()
        fastIter.close()

        println("read and check remade ccards took $stopwatch")
    }
}