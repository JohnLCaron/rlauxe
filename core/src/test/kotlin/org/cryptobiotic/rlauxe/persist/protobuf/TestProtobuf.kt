package org.cryptobiotic.rlauxe.persist.protobuf

import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.cryptobiotic.rlauxe.audit.CardWithBatchName
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CountyAudit
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestProtobuf {

    // @Test
    fun testConvert(card: CardWithBatchName) {
        val protoCard: ProtoCard = card.publishProto()
        val bytes = ProtoBuf.encodeToByteArray(protoCard)
        println(bytes.toHexString())
        val obj = ProtoBuf.decodeFromByteArray<ProtoCard>(bytes)
        println(obj)
    }

    /*
    fun testProtoCardConversion(cards: List<CardIF>, filename: String) {
        val json = contests.publishJson()
        val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
        FileOutputStream(filename).use { out ->
            jsonReader.encodeToStream(json, out)
            out.close()
        }
    } */

    @Test
    fun writeProtobufFile () {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val publisher = Publisher("$topdir/audit")
        val cardIter: CloseableIterator<CardWithBatchName> = readCardsCsvIterator(publisher.cardManifestFile())

        val protoFilename = "${topdir}/audit/cards.protobuf"
        val outputStream: OutputStream = FileOutputStream(protoFilename)

        val stopwatch = Stopwatch()
        // fun writeProtobufCards(cards: CloseableIterator<CardIF>, output: OutputStream): Int {
        val ncards = writeProtobufCards(cardIter, outputStream)
        outputStream.close()
        println("writeProtoFile ncards = $ncards, took $stopwatch")
    }

    @Test
    fun timeReadProtobuf () {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val protoFilename = "${topdir}/audit/cards.protobuf"
        val bufferSize = 100_000

        val countyAudit = AuditRecord.read(topdir) as CountyAudit
        val mvrManager = PersistedMvrManager(countyAudit)
        val styles = mvrManager.styles()

        val stopwatch = Stopwatch()
        var ncards = 0L

        var accum = 0
        // class ProtobufCardIterator(filename: String, bufferSize: Int, val styles: List<StyleIF>? = null): CloseableIterator<AuditableCardProtobuf> {
        val protoIter: CloseableIterator<AuditableCardProtobuf> = ProtobufCardIterator(protoFilename, bufferSize, styles)
        while (protoIter.hasNext()) { //  && ncards < 1000_000) {
            val card = protoIter.next()
            accum += card.index() // prevent optimization
            ncards++
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ncards.toDouble()
        val secPer = msPer / 1000

        println("timeReadProtobuf ($bufferSize):  ncards = $ncards, accum=$accum took $stopwatch = $msPer ms/card")
        val totalCards = 4982747
        println("time to read all cards = ${dfn(totalCards * secPer, 3)} secs")
    }

    @Test
    fun testProtoAndCsvAgree () {
        val bufferSize = 100_000

        val topdir = "${testdataDir}/cases/corla/consistent"
        val countyAudit = AuditRecord.read(topdir) as CountyAudit
        val mvrManager = PersistedMvrManager(countyAudit)
        val styles = mvrManager.styles()

        val publisher = Publisher("$topdir/audit")
        val csvIter: CloseableIterator<CardWithBatchName> = readCardsCsvIterator(publisher.cardManifestFile())

        val protoFilename = "${topdir}/audit/cards.protobuf"
        val stopwatch = Stopwatch()
        var ncards = 0L

        val protoIter: CloseableIterator<AuditableCardProtobuf> = ProtobufCardIterator(protoFilename, bufferSize, styles)
        while (protoIter.hasNext() && csvIter.hasNext() && ncards < 100_000) {
            val cardFromCsv = csvIter.next()
            val cardFromProto = protoIter.next()
            assertTrue(checkEqual(cardFromCsv, cardFromProto))
            ncards++
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ncards.toDouble()
        val secPer = msPer / 1000

        println("timeReadCsv ($bufferSize):  ncards = $ncards, took $stopwatch = $msPer ms/card")
        val totalCards = 4982747
        println("time to read all cards = ${dfn(totalCards * secPer, 3)} secs")
    }

    @Test
    fun testCsvAgreeOnCards () {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val publisher = Publisher("$topdir/audit")
        val bufferSize = 100_000

        val inputStream = File(publisher.cardManifestFile()).inputStream()
        val csvIter: CloseableIterator<AuditableCardProto> = CsvCardUsingArrays(inputStream, bufferSize)

        val stopwatch = Stopwatch()
        var ncards = 0L

        val currentCardIter: CloseableIterator<CardWithBatchName> = readCardsCsvIterator(publisher.cardManifestFile())
        while (currentCardIter.hasNext() && csvIter.hasNext() && ncards < 1000) {
            val c1 = csvIter.next()
            val c2 = currentCardIter.next()
            assertTrue(checkEqual(c1, c2))
            // println("ok ${c1.id}")
            ncards++
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ncards.toDouble()
        val secPer = msPer / 1000

        println("timeReadCsv ($bufferSize):  ncards = $ncards, took $stopwatch = $msPer ms/card")
        val totalCards = 4982747
        println("time to read all cards = ${dfn(totalCards * secPer, 3)} secs")
    }

    // @Test
    fun testProtoAgreeOnCards () {
        val bufferSize = 100_000

        val stopwatch = Stopwatch()
        var ncards = 0L

        val protoFilename = "${testdataDir}/temp/cards.proto"
        val protoIter: CloseableIterator<CardWithBatchName> = ProtoCardBunchIterator(protoFilename, bufferSize)

        val protoIterWithArrays: CloseableIterator<AuditableCardProto> = AuditableCardProtoIterator(protoFilename, bufferSize)
        while (protoIterWithArrays.hasNext() && protoIter.hasNext() && ncards < 1000) {
            val c1 = protoIterWithArrays.next()
            val c2 = protoIter.next()
            assertTrue(checkEqual(c1, c2))
            // println("ok ${c1.id}")
            ncards++
        }
        println("timeReadCsv ($bufferSize):  ncards = $ncards, took $stopwatch")
    }
}

fun checkEqual(c1: CardWithBatchName, c2: AuditableCardProtobuf): Boolean {
    assertEquals(c1.id(), c2.id())
    assertEquals(c1.location(), c2.location())
    assertEquals(c1.index(), c2.index())
    assertEquals(c1.prn(), c2.prn())
    assertEquals(c1.poolId(), c2.poolId())
    assertEquals(c1.styleName(), c2.styleName())

    assertEquals(c1.votes == null, c2.votes == null)
    if (c1.votes != null) {
        val c1votes = c1.votes
        for ((contestId, candidates) in c1.votes) {
            val otherCands = c2.votes!![contestId]
            assertTrue(candidates.contentEquals(otherCands))
        }
    }
    return true
}