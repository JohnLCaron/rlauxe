@file:OptIn(ExperimentalSerializationApi::class)
package org.cryptobiotic.rlauxe.persist.protobuf


import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.cryptobiotic.rlauxe.audit.CardIF
import org.cryptobiotic.rlauxe.audit.AuditableCardProto
import org.cryptobiotic.rlauxe.audit.CardWithBatchName
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.io.BufferedInputStream

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.String
import kotlin.io.path.Path

// TODO: check if faster
@Serializable
data class ProtoCards (
    val cards: List<ProtoCard>
)

// TODO check is using real protobuf library is better
@Serializable
data class ProtoCard (
    val id: String, // enough info to find the card for a manual audit.
    val location: String?, // enough info to find the card for a manual audit.
    val index: Int,  // index into the original, canonical list of cards
    val prn: Long,   // psuedo random number
    val phantom: Boolean,
    val poolId: Int?, // must be set if its from a CardPool
    val contestIds: IntArray,
    val contestStarts: IntArray,
    val candidates: IntArray,
    val styleName: String,
)

fun CardIF.publishProto() : ProtoCard {
    val votes = this.votes()
    val contestIds = votes!!.keys
    val contestIdas = contestIds.toList().toIntArray()
    val candidates = mutableListOf<Int>()
    val contestStarts = mutableListOf<Int>()
    var start = 0

    contestIds.forEach {
        val cands = votes[it]!!
        candidates.addAll(cands.toList())
        contestStarts.add(start)
        start += cands.size
    }

    return ProtoCard(
        this.id(),
        this.location(),
        this.index(),
        this.prn(),
        this.phantom(),
        this.poolId(),
        contestIdas,
        contestStarts.toIntArray(),
        candidates.toIntArray(),
        this.styleName(),
    )
}

// data class CardWithBatchName (
//    val id: String,
//    val location: String?, // enough info to find the card for a manual audit.
//    val index: Int,  // index into the original, canonical list of cards
//    val prn: Long,   // psuedo random number
//    val phantom: Boolean,
//
//    val votes: Map<Int, IntArray>?,   // CVRs and phantoms
//    val poolId: Int?,                 // must be set if its from a CardPool
//    val styleName: String,
//): CardIF {
//
//    constructor(card: AuditableCard): this(card.id, card.location, card.index, card.prn, card.phantom, card.votes, card.poolId(), card.styleName())
fun ProtoCard.import() = CardWithBatchName(
        this.id,
        if (this.location == this.id) null else this.location,
        this.index,
        this.prn,
        this.phantom,
    makeVotes(this.contestIds, this.contestStarts, this.candidates),
        this.poolId,
        this.styleName,
    )

fun makeVotes( contestIds: IntArray,  contestStarts: IntArray,  candidates: IntArray): Map<Int, IntArray> {
    val lastIndex = contestIds.size-1
    val makeVotes = mutableMapOf<Int, IntArray>()
    contestIds.forEachIndexed { index, contestId ->
        val start = contestStarts[index]
        val end = if (index < lastIndex) contestStarts[index+1] else candidates.size
        if (start > end || end > candidates.size)
            print("")
        makeVotes[contestId] = candidates.sliceArray(start until end)
    }
    return makeVotes.toMap()
}

/////////////////////////////////////////////////////////

fun writeProtoCards(cards: CloseableIterator<CardIF>, output: OutputStream): Int {
    // kotlinx.serialization's Protobuf implementation does not natively support writeDelimitedTo
    var count = 0
    while (cards.hasNext()) {
        val bunch = mutableListOf<ProtoCard>()
        while (cards.hasNext() && bunch.size < 1000) {  // bunch into 1000
            val card = cards.next()
            bunch.add(card.publishProto())
            count++
        }
        val protoCards = ProtoCards(bunch)
        val bytes = ProtoBuf.encodeToByteArray(protoCards)
        writeDelimitedTo(bytes, output)
        if (count % 10000 == 0) { print("$count, ")}
        if (count % 100000 == 0) { println("$count, ")}
    }
    return count
}

fun writeDelimitedTo(bytes: ByteArray, output: OutputStream) {
   writeVlenForProto(bytes.size, output)
   output.write(bytes)
   output.flush()
}

private fun writeVlenForProto(messageSize: Int, output: OutputStream) {
    var value = messageSize
    while (true) {
        if (value and 0x7F.inv() == 0) {
            output.write(value)
            return
        } else {
            output.write(value and 0x7F or 0x80)
            value = value ushr 7
        }
    }
}


// could break the rule and return ProtoCard for speed....
// that is, ProtoCard == CardWithBatchName
// otoh,
class ProtoCardIterator(filename: String, bufferSize: Int): CloseableIterator<CardWithBatchName> {
    val errs = ErrorMessages("readProtoCardsFile '${filename}'")
    val inputStream: InputStream
    var currentBunch: Iterator<ProtoCard>

    init {
        val filepath = Path(filename)
        if (!Files.exists(filepath)) {
            throw RuntimeException("file does not exist")
        }
        val input = Files.newInputStream(filepath, StandardOpenOption.READ)
        inputStream = BufferedInputStream(input, bufferSize)

        val messageSize = readVlen(inputStream)
        currentBunch = nextBunch(messageSize)
    }

    override fun hasNext(): Boolean {
        if (currentBunch.hasNext()) return true
        val messageSize  = readVlen(inputStream)
        if (messageSize <= 0) return false
        currentBunch = nextBunch(messageSize)
        return true
    }

    override fun next(): CardWithBatchName {
        return currentBunch.next().import()
    }

    override fun close() {
        inputStream.close()
    }

    // the inner iterator
    fun nextBunch(messageSize: Int): Iterator<ProtoCard> {
        val bytes = ByteArray(messageSize)
        val bytesRead = inputStream.read(bytes)
        val protoCards = ProtoBuf.decodeFromByteArray<ProtoCards>(bytes)
        return protoCards.cards.iterator()
    }
}

/////////////////////////////////////////////////////////////////////////////////////////////
// The parseDelimitedFrom method in Java's Protocol Buffers library reads a single message from an InputStream where the message is prefixed by its size (as a varint).
// In Java, writeDelimitedTo(OutputStream output) is a method provided by MessageLite (implemented by all Protobuf messages)

// (*) The files encryptedBallots.protobuf and spoiledBallotTallies.protobuf contain multiple length-delimited proto messages.
//This allows to read messages one at a time, rather than all into memory at once.
//See writeDelimitedTo() and parseDelimitedFrom() methods for varint length-delimited messages.
//
// fun writeDelimitedTo(proto: pbandk.Message, output: OutputStream) {
//    val bb = ByteArrayOutputStream()
//    proto.encodeToStream(bb)
//    writeVlenForProto(bb.size(), output)
//    output.write(bb.toByteArray())
//    output.flush()
//}
// import pbandk.decodeFromStream


fun testConvert(card: CardWithBatchName) {
    val protoCard: ProtoCard = card.publishProto()
    val bytes = ProtoBuf.encodeToByteArray(protoCard)
    println(bytes.toHexString())
    val obj = ProtoBuf.decodeFromByteArray<ProtoCard>(bytes)
    println(obj)
}

// could break the rule and return ProtoCard for speed....
// that is, ProtoCard == CardWithBatchName
// otoh,
class AuditableCardProtoIterator(filename: String, bufferSize: Int, val styles: List<StyleIF>? = null): CloseableIterator<AuditableCardProto> {
    val styleMap: Map<String, StyleIF> = styles?.associateBy{ it.name() } ?: emptyMap()

    val errs = ErrorMessages("readProtoCardsFile '${filename}'")
    val inputStream: InputStream
    var currentBunch: Iterator<ProtoCard>

    init {
        val filepath = Path(filename)
        if (!Files.exists(filepath)) {
            throw RuntimeException("file does not exist")
        }
        val input = Files.newInputStream(filepath, StandardOpenOption.READ)
        inputStream = BufferedInputStream(input, bufferSize)

        val messageSize = readVlen(inputStream)
        currentBunch = nextBunch(messageSize)
    }

    override fun hasNext(): Boolean {
        if (currentBunch.hasNext()) return true
        val messageSize  = readVlen(inputStream)
        if (messageSize <= 0) return false
        currentBunch = nextBunch(messageSize)
        return true
    }

    override fun next(): AuditableCardProto {
        return AuditableCardProto.from(currentBunch.next(), styleMap)
    }

    override fun close() {
        inputStream.close()
    }

    // the inner iterator
    fun nextBunch(messageSize: Int): Iterator<ProtoCard> {
        val bytes = ByteArray(messageSize)
        val bytesRead = inputStream.read(bytes)
        val protoCards = ProtoBuf.decodeFromByteArray<ProtoCards>(bytes)
        return protoCards.cards.iterator()
    }
}

fun readVlen(input: InputStream): Int {
    var ib: Int = input.read()
    if (ib == -1) {
        return -1
    }
    var result = ib.and(0x7F)
    var shift = 7
    while (ib.and(0x80) != 0) {
        ib = input.read()
        if (ib == -1) {
            return -1
        }
        val im = ib.and(0x7F).shl(shift)
        result = result.or(im)
        shift += 7
    }
    return result
}

