package org.cryptobiotic.rlauxe.persist.protobuf

import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.cryptobiotic.rlauxe.audit.AuditableCardIF
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.contains
import kotlin.collections.iterator
import kotlin.io.path.Path
import kotlin.ranges.until

// aka CardWithArrays
class AuditableCardProto(
    val id: String, // enough info to find the card for a manual audit.
    val location: String?, // enough info to find the card for a manual audit.
    val index: Int,  // index into the original, canonical list of cards
    val prn: Long,   // psuedo random number
    val phantom: Boolean,
    val poolId: Int?, // must be set if its from a CardPool
    val contestIds: IntArray,
    val contestStarts: IntArray,
    val candidates: IntArray,
    val style: StyleIF
): AuditableCardIF {
    val useCvr = CardStyle.useVotes(style.name())

    val votes: Map<Int, IntArray>? by lazy {
        if (contestIds.isEmpty()) null else {
            val lastIndex = contestIds.size - 1
            val makeVotes = mutableMapOf<Int, IntArray>()
            contestIds.forEachIndexed { index, contestId ->
                val start = contestStarts[index]
                val end = if (index < lastIndex) contestStarts[index + 1] else candidates.size
                if (start > end || end > candidates.size)
                    makeVotes[contestId] = candidates.sliceArray(start until end)
            }
            makeVotes.toMap()
        }
    }

    // TODO test that StyleIF.contests agrees with votes when they are both present

    override fun hasContest(contestId: Int): Boolean {
        return if (!useCvr) style.hasContest(contestId)
        else contestIds.contains(contestId)
    }

    override fun possibleContests() : IntArray {
        return when {
            CardStyle.useVotes(style.name()) -> votes!!.keys.toList().sorted().toIntArray() // assumes cvrsContainUndervotes, use cardStyle if not.
            else -> style.possibleContests().toList().sorted().toIntArray()
        }
    }

    override fun id() = id
    override fun location() = location ?: id()
    override fun index() = index
    override fun prn() = prn
    override fun phantom() = phantom
    override fun votes() = votes
    override fun votes(contestId: Int): IntArray? = votes?.get(contestId)
    override fun poolId(): Int? = poolId
    override fun styleName() = style.name()
    override fun style() = style
    override fun hasExactContests() = style.hasExactContests()

    override fun toCvr(): Cvr {
        TODO("Not yet implemented")
    }

    override fun hasMarkFor(contestId: Int, candidateId: Int): Int {
        val contestVotes = votes(contestId)
        return if (contestVotes == null) 0
        else if (contestVotes.contains(candidateId)) 1 else 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuditableCardProto) return false

        if (index != other.index) return false
        if (prn != other.prn) return false
        if (phantom != other.phantom) return false
        if (poolId != other.poolId) return false
        if (id != other.id) return false
        if (location != other.location) return false
        if (!contestIds.contentEquals(other.contestIds)) return false
        if (!contestStarts.contentEquals(other.contestStarts)) return false
        if (!candidates.contentEquals(other.candidates)) return false
        if (style != other.style) return false
        if ((votes == null) != (other.votes == null)) return false
        if (votes != null) {
            for ((contestId, candidates) in votes) {
                val otherCands = other.votes!![contestId]
                if (!candidates.contentEquals(otherCands)) return false
            }
        }

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + prn.hashCode()
        result = 31 * result + phantom.hashCode()
        result = 31 * result + (poolId ?: 0)
        result = 31 * result + id.hashCode()
        result = 31 * result + (location?.hashCode() ?: 0)
        result = 31 * result + contestIds.contentHashCode()
        result = 31 * result + contestStarts.contentHashCode()
        result = 31 * result + candidates.contentHashCode()
        result = 31 * result + style.hashCode()
        result = 31 * result + votes.hashCode()
        return result
    }

    override fun toString()= buildString {
        appendLine("id='$id', location=$location, index=$index, prn=$prn, phantom=$phantom, poolId=$poolId, ")
        appendLine("   contestIds=${contestIds.contentToString()}, contestStarts=${contestStarts.contentToString()}, ")
        appendLine("   candidates=${candidates.contentToString()}, style=$style, votes=$votes")
    }
}


// class AuditableCardProto(
//    val id: String, // enough info to find the card for a manual audit.
//    val location: String?, // enough info to find the card for a manual audit.
//    val index: Int,  // index into the original, canonical list of cards
//    val prn: Long,   // psuedo random number
//    val phantom: Boolean,
//    val poolId: Int?, // must be set if its from a CardPool
//    val contestIds: IntArray,
//    val contestStarts: IntArray,
//    val candidates: IntArray,
//    val style: StyleIF
//): AuditableCardIF
fun ProtoCard.importP(styleMap: Map<String, StyleIF> ): AuditableCardProto {

    var style = styleMap[this.styleName]
    if (style == null) {
        if (this.phantom)
            style = CardStyle.phantomBatch
        else if (this.styleName == CardStyle.fromCvr)
            style = CardStyle.fromCvrBatch
        else
            throw RuntimeException()
    }

    return AuditableCardProto(
        this.id,
        if (this.location == this.id) null else this.location,
        this.index,
        this.prn,
        this.phantom,
        this.poolId,
        this.contestIds ?: intArrayOf(),
        this.contestStarts ?: intArrayOf(),
        this.candidates ?: intArrayOf(),
        style,
    )
}

class ProtoCardIterator(filename: String, bufferSize: Int = 100_000, val styles: List<StyleIF>?): CloseableIterator<AuditableCardProto> {
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

    override fun next(): AuditableCardProto {
        val bytes = ByteArray(nextMessageSize)
        val bytesRead = inputStream.read(bytes)
        val protoCard = ProtoBuf.decodeFromByteArray<ProtoCard>(bytes)
        return protoCard.importP(styleMap)
    }

    override fun close() {
        inputStream.close()
    }
}


