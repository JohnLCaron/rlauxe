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

// TODO is bunch worth it ?
fun writeProtoCardBunch(cards: CloseableIterator<CardIF>, output: OutputStream): Int {
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

// could break the rule and return ProtoCard for speed....
// that is, ProtoCard == CardWithBatchName
// otoh,
class ProtoCardBunchIterator(filename: String, bufferSize: Int): CloseableIterator<CardWithBatchName> {
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

// could break the rule and return ProtoCard for speed....
// that is, ProtoCard == CardWithBatchName
// otoh,
class AuditableCardProtoBunchIterator(filename: String, bufferSize: Int, val styles: List<StyleIF>? = null): CloseableIterator<AuditableCardProto> {
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

