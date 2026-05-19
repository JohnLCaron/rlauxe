package org.cryptobiotic.rlauxe.persist.protobuf

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.AuditableCardIF
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.CardCsvReaderM
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIteratorM
import org.cryptobiotic.rlauxe.persist.json.readCardStylesJsonFile
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.dfn
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestProtoCard {
    val testFastSamplFile = "$testdataDir/temp/sortedCards.proto"

    @Test
    fun writeProtoFile () {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val publisher = Publisher("$topdir/audit")
        val cardIter: CloseableIterator<AuditableCardIF> = readCardsCsvIteratorM(publisher.sortedCardsFile(), styles=null)

        val stopwatch = Stopwatch()
        val ncards = writeProtoCards(cardIter, testFastSamplFile)
        println("writeProtoFile ncards = $ncards, took $stopwatch")
    }

    @Test
    fun timeReadProto () {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val publisher = Publisher("$topdir/audit")
        val protoFilename = publisher.sortedCardsProtoFile()
        val styles = readCardStylesJsonFile(publisher.cardStylesFile()).unwrap()

        val bufferSize = 100_000
        val stopwatch = Stopwatch()
        var ncards = 0L

        var accum = 0
        val protoIter: CloseableIterator<AuditableCardProto> = ProtoCardIterator(protoFilename, bufferSize, styles)
        while (protoIter.hasNext()) { //  && ncards < 1000_000) {
            val card = protoIter.next()
            accum += card.index() // prevent optimization
            ncards++
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ncards.toDouble()
        val secPer = msPer / 1000

        println("timeReadProto ($bufferSize):  ncards = $ncards, accum=$accum took $stopwatch = $msPer ms/card")
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

    /* @Test
    fun testProtoAndCsvAgreeOnCardWithBatchName () {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val publisher = Publisher("$topdir/audit")
        val currentCardIter: CloseableIterator<CardWithStyleName> = readCardsCsvIterator(publisher.cardManifestFile())

        val protoFilename = "${topdir}/audit/${publisher.sortedCardsProtoFile()}"
        val bufferSize = 100_000
        val stopwatch = Stopwatch()
        var ncards = 0L

        val protoIter: CloseableIterator<CardWithStyleName> = ProtoCardBunchIterator(protoFilename, bufferSize)
        while (protoIter.hasNext() && currentCardIter.hasNext() && ncards < 100_000) {
            val cardFromCsv = currentCardIter.next()
            val cardFromProto = protoIter.next()
            assertEquals(cardFromCsv, cardFromProto)
            ncards++
        }


        val inputStream = File(publisher.cardManifestFile()).inputStream()
        val csvIter: CloseableIterator<CardUsingArrays> = IteratorCardUsingArrays(inputStream, bufferSize)
        while (csvIter.hasNext() && ncards < 1000000) {
            val card = csvIter.next()
            ncards++
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ncards.toDouble()
        val secPer = msPer / 1000

        println("timeReadCsv ($bufferSize):  ncards = $ncards, took $stopwatch = $msPer ms/card")
        val totalCards = 4982747
        println("time to read all cards = ${dfn(totalCards * secPer, 3)} secs")
    } */

    @Test
    fun testProtoAndCsvAgree () {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val publisher = Publisher("$topdir/audit")
        val bufferSize = 100_000

        val styles = readCardStylesJsonFile(publisher.cardStylesFile()).unwrap()
        val csvIter = CardCsvReaderM(publisher.sortedCardsFile(), styles).iterator()

        val stopwatch = Stopwatch()
        var ncards = 0L

        val protoIter: CloseableIterator<AuditableCardProto> = ProtoCardIterator(publisher.sortedCardsProtoFile(), bufferSize, styles)
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

fun checkEqual(c1: AuditableCardProto, c2: AuditableCardIF): Boolean {
    assertEquals(c1.id(), c2.id())
    assertEquals(c1.location(), c2.location())
    assertEquals(c1.index(), c2.index())
    assertEquals(c1.prn(), c2.prn())
    assertEquals(c1.poolId(), c2.poolId())
    assertEquals(c1.styleName(), c2.styleName())

    assertEquals(c1.votes == null, c2.votes() == null)
    if (c1.votes != null) {
        val c1votes = c1.votes
        for ((contestId, candidates) in c1.votes) {
            val otherCands = c2.votes(contestId)
            assertTrue(candidates.contentEquals(otherCands))
        }
    }
    return true
}

////////////


class CsvCardUsingArrays(input: InputStream, bufferSize: Int): CloseableIterator<AuditableCardProto> {
    // was val reader = BufferedReader(InputStreamReader(input, "ISO-8859-1")) for some reason
    val reader = BufferedReader(InputStreamReader(input),bufferSize)
    var nextLine: String? = null
    var countLines  = 0

    init {
        reader.readLine() // get rid of header line
    }

    override fun hasNext() : Boolean {
        if (nextLine == null) {
            countLines++
            nextLine = reader.readLine()
        }
        return nextLine != null
    }

    override fun next(): AuditableCardProto {
        if (!hasNext()) throw NoSuchElementException()
        val result =  readCardWithArrays(nextLine!!)
        nextLine = null
        return result
    }

    override fun close() {
        reader.close()
    }
}


fun readCardWithArrays(line: String): AuditableCardProto {
    val tokens = line.split(",")
    val ttokens = tokens.map { it.trim() }

    var idx = 0
    val id = ttokens[idx++]
    val locationToken = ttokens[idx++]
    val location = locationToken.ifEmpty { null }
    val index = ttokens[idx++].toInt()
    val sampleNum = ttokens[idx++].toLong(radix=16)
    val phantom = ttokens[idx++] == "yes"
    val poolIdToken = ttokens[idx++]
    val poolId = if (poolIdToken.isEmpty()) null else poolIdToken.toInt()
    val styleName = ttokens[idx++].trim()

    // if clca, list of actual contests and their votes
    // if (idx < ttokens.size-1) {
    val contestsStr = ttokens[idx++].trim()
    val contestsTokenTrimmed = contestsStr.split(" ").map { it.trim() }

    val contestIds = mutableListOf<Int>()

    contestsTokenTrimmed.forEach { tok ->
        if (tok.isNotEmpty()) contestIds.add(tok.toInt())
    }

    val contestStarts = mutableListOf<Int>()
    val candidates = mutableListOf<Int>()

    val hasVotes = (idx + contestIds.size) < ttokens.size

    val votes = if (!hasVotes) null else {
        val work = mutableListOf<IntArray>()
        while (idx < ttokens.size && (work.size < contestIds.size)) {
            val vtokens = ttokens[idx]
            val candArray =
                if (vtokens.isEmpty()) intArrayOf()
                else vtokens.split(" ").map { it.trim().toInt() }.toIntArray()
            work.add(candArray)
            idx++
        }
        require(contestIds.size == work.size) { "contests.size (${contestIds.size}) != votes.size (${work.size})" }
        contestIds.zip(work).toMap()
    }

    if (votes != null) {
        var start = 0
        contestIds.forEach {
            val cands = votes[it]!!
            candidates.addAll(cands.toList())
            contestStarts.add(start)
            start += cands.size
        }
    }

    return AuditableCardProto(
        id,
        if (location == id) null else location,
        index, sampleNum, phantom, poolId,
        contestIds.toIntArray(),
        contestStarts.toIntArray(),
        candidates.toIntArray(),
        CardStyle.fromCvrBatch,
        // styleName=styleName
    )
    //}
    //return CardUsingArrays(id, location, index, sampleNum, phantom, poolId,null, , styleName=styleName)
}


