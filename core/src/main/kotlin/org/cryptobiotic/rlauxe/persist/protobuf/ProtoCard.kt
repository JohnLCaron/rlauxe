@file:OptIn(ExperimentalSerializationApi::class)
package org.cryptobiotic.rlauxe.persist.protobuf

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.cryptobiotic.rlauxe.audit.AuditableCardIF
import org.cryptobiotic.rlauxe.audit.AuditableCardM
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.io.BufferedInputStream
import java.io.FileOutputStream

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.String
import kotlin.io.path.Path

@Serializable
class ProtoCard (
    val id: String, // enough info to find the card for a manual audit.
    val location: String? = null, // enough info to find the card for a manual audit.
    val index: Int,  // index into the original, canonical list of cards
    val prn: Long,   // psuedo random number
    val phantom: Boolean,
    val poolId: Int? = null, // must be set if its from a CardPool
    // The default integer type is a varint encoding (intXX) that is optimized for small non-negative numbers.
    val contestIds: IntArray? = null,
    val contestStarts: IntArray? = null,
    val candidates: IntArray? = null,
    val styleName: String,
)

fun AuditableCardIF.publishProto() : ProtoCard {
    val votes = this.votes()
    if (votes != null) {
        val contestIds = votes.keys
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
    } else {
        return ProtoCard(
            this.id(),
            this.location(),
            this.index(),
            this.prn(),
            this.phantom(),
            this.poolId(),
            null,
            null,
            null,
            this.styleName(),
        )
    }
}

fun ProtoCard.importM(styleMap: Map<String, StyleIF> ): AuditableCardM {

    var style = styleMap[this.styleName]
    if (style == null) {
        if (this.phantom)
            style = CardStyle.phantomBatch
        else if (this.styleName == CardStyle.fromCvr)
            style = CardStyle.fromCvrBatch
        else
            throw RuntimeException()
    }

    return AuditableCardM(
        this.id,
        if (this.location == this.id) null else this.location,
        this.index,
        this.prn,
        this.phantom,
        this.styleName,
        this.poolId,
        this.contestIds ?: intArrayOf(),
        this.contestStarts ?: intArrayOf(),
        this.candidates ?: intArrayOf(),
    ).setStyle(style)
}

/////////////////////////////////////////////////////////

fun writeProtoCards(cards: CloseableIterator<AuditableCardIF>, protoFilename: String): Int {
    val outputStream: OutputStream = FileOutputStream(protoFilename)

    var count = 0
    while (cards.hasNext()) {
        val card = cards.next()
        val protoCard = card.publishProto()
        val bytes = ProtoBuf.encodeToByteArray(protoCard)
        writeDelimitedTo(bytes, outputStream)
        count++
        if (count % 10000 == 0) { print("$count, ")}
        if (count % 100000 == 0) { println("$count, ")}
    }
    outputStream.close()
    return count
}

// kotlinx.serialization's Protobuf implementation does not natively support writeDelimitedTo
// so roll it up
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

// see TimeCardReading
class ProtoCardIterable(val protoFilename: String, val bufferSize: Int = 100_000, val styles: List<StyleIF>?) : CloseableIterable<AuditableCardM> {
    override fun iterator(): CloseableIterator<AuditableCardM> =
        ProtoCardIteratorM(protoFilename, bufferSize, styles)
}

class ProtoCardIteratorM(filename: String, bufferSize: Int = 100_000, val styles: List<StyleIF>? = null): CloseableIterator<AuditableCardM> {
    val styleMap: Map<String, StyleIF> = styles?.associateBy{ it.name() } ?: emptyMap()

    val errs = ErrorMessages("readProtoCardsFile '${filename}'")
    val inputStream: InputStream
    var nextMessageSize = -1

    init {
        val filepath = Path(filename)
        if (!Files.exists(filepath)) {
            throw RuntimeException("file does not exist")
        }
        val input = Files.newInputStream(filepath, StandardOpenOption.READ)
        inputStream = BufferedInputStream(input, bufferSize)
    }

    override fun hasNext(): Boolean {
        nextMessageSize = readVlen(inputStream)
        return (nextMessageSize > 0)
    }

    override fun next(): AuditableCardM {
        val bytes = ByteArray(nextMessageSize)
        val bytesRead = inputStream.read(bytes)
        val protoCard = ProtoBuf.decodeFromByteArray<ProtoCard>(bytes)
        return protoCard.importM(styleMap)
    }

    override fun close() {
        inputStream.close()
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

