package org.cryptobiotic.rlauxe.persist.proto

import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.cryptobiotic.rlauxe.audit.AuditableCardProto
import org.cryptobiotic.rlauxe.audit.CardWithBatchName
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.CsvCardUsingArrays
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.protobuf.ProtoCard
import org.cryptobiotic.rlauxe.persist.protobuf.ProtoCardIterator
import org.cryptobiotic.rlauxe.persist.protobuf.AuditableCardProtoIterator
import org.cryptobiotic.rlauxe.persist.protobuf.publishProto
import org.cryptobiotic.rlauxe.persist.protobuf.writeProtoCards
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.dfn
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

class TestProtoCard {

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
    fun writeProtoFile () {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val publisher = Publisher("$topdir/audit")
        val cardIter: CloseableIterator<CardWithBatchName> = readCardsCsvIterator(publisher.cardManifestFile())

        val protoFilename = "${topdir}/audit/cards.proto"
        val outputStream: OutputStream = FileOutputStream(protoFilename)

        val stopwatch = Stopwatch()
        val ncards = writeProtoCards(cardIter, outputStream)
        outputStream.close()
        println("writeProtoFile ncards = $ncards, took $stopwatch")
    }

    @Test
    fun timeReadProto () {
        val protoFilename = "${testdataDir}/temp/cards.proto"
        val bufferSize = 100_000

        val stopwatch = Stopwatch()
        var ncards = 0L

        val protoIter: CloseableIterator<AuditableCardProto> = AuditableCardProtoIterator(protoFilename, bufferSize)
        while (protoIter.hasNext()) { //  && ncards < 1000_000) {
            val card = protoIter.next()
            ncards++
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ncards.toDouble()
        val secPer = msPer / 1000

        println("timeReadProto ($bufferSize):  ncards = $ncards, took $stopwatch = $msPer ms/card")
        val totalCards = 4982747
        println("time to read all cards = ${dfn(totalCards * secPer, 3)} secs")
    }
    // ===========================
    //ok 16 sec reading: use proto, CardUsingArrays, init votes lazily
    //
    //vs 52?
    //should speed up Consistent sampling by 3x ??

    @Test
    fun timeReadCsv () {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val publisher = Publisher("$topdir/audit")

        val stopwatch = Stopwatch()
        var ncards = 0L
        val bufferSize = 100_000

        val inputStream = File(publisher.cardManifestFile()).inputStream()
        val csvIter: CloseableIterator<AuditableCardProto> = CsvCardUsingArrays(inputStream, bufferSize)
        while (csvIter.hasNext() && ncards < 1000000) {
            val card = csvIter.next()
            ncards++
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ncards.toDouble()
        val secPer = msPer / 1000

        println("timeReadCsv ($bufferSize):  ncards = $ncards, took $stopwatch = $msPer ms/card")
        val totalCards = 4982747
        println("time to read all cards = ${dfn(totalCards * secPer, 3)} secs")
    }

    @Test
    fun testProtoAndCsvAgreeOnCardWithBatchName () {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val publisher = Publisher("$topdir/audit")
        val currentCardIter: CloseableIterator<CardWithBatchName> = readCardsCsvIterator(publisher.cardManifestFile())

        val protoFilename = "${testdataDir}/temp/cards.proto"
        val bufferSize = 100_000
        val stopwatch = Stopwatch()
        var ncards = 0L

        val protoIter: CloseableIterator<CardWithBatchName> = ProtoCardIterator(protoFilename, bufferSize)
        while (protoIter.hasNext() && currentCardIter.hasNext() && ncards < 100_000) {
            val cardFromCsv = currentCardIter.next()
            val cardFromProto = protoIter.next()
            assertEquals(cardFromCsv, cardFromProto)
            ncards++
        }

/*
        val inputStream = File(publisher.cardManifestFile()).inputStream()
        val csvIter: CloseableIterator<CardUsingArrays> = IteratorCardUsingArrays(inputStream, bufferSize)
        while (csvIter.hasNext() && ncards < 1000000) {
            val card = csvIter.next()
            ncards++
        } */

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ncards.toDouble()
        val secPer = msPer / 1000

        println("timeReadCsv ($bufferSize):  ncards = $ncards, took $stopwatch = $msPer ms/card")
        val totalCards = 4982747
        println("time to read all cards = ${dfn(totalCards * secPer, 3)} secs")
    }

    @Test
    fun testProtoAndCsvAgreeOnCardUsingArrays () {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val publisher = Publisher("$topdir/audit")
        val bufferSize = 100_000

        val inputStream = File(publisher.cardManifestFile()).inputStream()
        val csvIter: CloseableIterator<AuditableCardProto> = CsvCardUsingArrays(inputStream, bufferSize)

        val protoFilename = "${testdataDir}/temp/cards.proto"
        val stopwatch = Stopwatch()
        var ncards = 0L

        val protoIter: CloseableIterator<AuditableCardProto> = AuditableCardProtoIterator(protoFilename, bufferSize)
        while (protoIter.hasNext() && csvIter.hasNext() && ncards < 100_000) {
            val cardFromCsv = csvIter.next()
            val cardFromProto = protoIter.next()
            assertEquals(cardFromCsv, cardFromProto)
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

    @Test
    fun testProtoAgreeOnCards () {
        val bufferSize = 100_000

        val stopwatch = Stopwatch()
        var ncards = 0L

        val protoFilename = "${testdataDir}/temp/cards.proto"
        val protoIter: CloseableIterator<CardWithBatchName> = ProtoCardIterator(protoFilename, bufferSize)

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

fun checkEqual(c1: AuditableCardProto, c2: CardWithBatchName): Boolean {
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