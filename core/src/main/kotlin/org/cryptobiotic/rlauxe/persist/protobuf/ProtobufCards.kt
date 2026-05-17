package org.cryptobiotic.rlauxe.persist.protobuf

import org.cryptobiotic.rlauxe.audit.AuditableCardIF
import org.cryptobiotic.rlauxe.audit.CardIF
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.persist.protobuf.Protobuf.Card.parseFrom
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path

// message Card {
//    string id = 1;        // enough info to find the card for a manual audit.
//    string location = 2;  // enough info to find the card for a manual audit.
//    uint32 index = 3;     // index into the original, canonical list of cards
//    fixed64 prn = 4;      // psuedo random number
//    bool phantom = 5;
//    uint32 poolId = 6;    // must be set if its from a CardPool
//    //repeated uint32 contestIds = 7;     // these define the votes: Map<Int, IntArray>
//    //repeated uint32 contestStarts = 8;
//    //repeated uint32 candidates = 9;
//    string styleName = 10;
//    repeated VotesEntry votes = 11; // these define the votes: Map<Int, IntArray>
//}
//
//message VotesEntry {
//    uint32 contest = 1;
//    uint32 cands = 2;
//}

fun writeProtobufCards(cards: CloseableIterator<CardIF>, output: OutputStream, limit: Int? = null): Int {
    // kotlinx.serialization's Protobuf implementation does not natively support writeDelimitedTo
    var count = 0
    while (cards.hasNext() && (limit == null || count < limit)) {
        val card: CardIF = cards.next()
        val protoCard: Protobuf.Card = encodeToProtobuf(card)
        val bytes = protoCard.toByteArray()
        writeDelimitedTo(bytes, output)
        count++
        if (count % 10000 == 0) { print("$count, ")}
        if (count % 100000 == 0) { println("$count, ")}
    }
    return count
}

fun encodeToProtobuf(cardf : CardIF): Protobuf.Card  {

    val voteEntries: List<Protobuf.VotesEntry> = if (cardf.votes() == null) emptyList() else {
        cardf.votes()!!.map { (contestId, candidates) ->
            votesEntry {
                contest = contestId
                cands.addAll(candidates.toList())
            }
        }
    }

    val wtf: Protobuf.Card = card {
        id = cardf.id()
        location = cardf.location()
        index = cardf.index()
        prn = cardf.prn()
        phantom = cardf.phantom()
        if (cardf.poolId() != null) poolId = cardf.poolId()!!
        styleName = cardf.styleName()

        votes.addAll(voteEntries)
    }

    return wtf
}

////////////////////////////////////////////////////////////////////////////////////////////

class ProtobufCardIterator(filename: String, bufferSize: Int, val styles: List<StyleIF>? = null): CloseableIterator<AuditableCardProtobuf> {
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

    override fun next(): AuditableCardProtobuf {
        val bytes = ByteArray(nextMessageSize)
        val bytesRead = inputStream.read(bytes)
        val pcard: Protobuf.Card = parseFrom(ByteBuffer.wrap(bytes))
        return from(pcard, styleMap)
    }

    override fun close() {
        inputStream.close()
    }
}

fun from(pcard: Protobuf.Card, styles: Map<String, StyleIF>): AuditableCardProtobuf {
    val votePairs: List<Pair<Int, IntArray>> = pcard.votesList.map {
        Pair(it.contest, it.getCandsList().toIntArray())
    }

    return AuditableCardProtobuf(
        pcard.id,
        if (pcard.location == pcard.id) null else pcard.location,
        pcard.index,
        pcard.prn,
        pcard.phantom,
        if (pcard.hasPoolId()) pcard.poolId else null,
        votePairs = votePairs,
        styles[pcard.styleName] ?: CardStyle.fromCvrBatch,
    )
}

////////////////////////////////////////////////////////////////////////////

class AuditableCardProtobuf(
    val id: String, // enough info to find the card for a manual audit.
    val location: String?, // enough info to find the card for a manual audit.
    val index: Int,  // index into the original, canonical list of cards
    val prn: Long,   // psuedo random number
    val phantom: Boolean,
    val poolId: Int?, // must be set if its from a CardPool
    val votePairs: List<Pair<Int, IntArray>>,  // contest -> candidates
    val style: StyleIF
): AuditableCardIF {
    val useCvr = CardStyle.useVotes(style.name())

    val contestIds: IntArray by lazy { votePairs.map{ it.first }.toIntArray() }
    val votes: Map<Int, IntArray>? by lazy { if (votePairs.isEmpty()) null else votePairs.toMap() }

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
    override fun hasStyle() = style.hasExactContests()

    override fun toCvr(): Cvr {
        TODO("Not yet implemented")
    }

    override fun hasMarkFor(contestId: Int, candidateId: Int): Int {
        val contestVotes = votes(contestId)
        return if (contestVotes == null) 0
        else if (contestVotes.contains(candidateId)) 1 else 0
    }

    /*
    override fun votes(contestId: Int): IntArray? {
        // or turn back into a map lazily ??
        // HEY consistent sampling only wants hasContest, optimize for that...
        val contestIdx = contestIds.indexOf(contestId)
        val start = contestStarts[contestIdx]
        val end = contestStarts[contestIdx+1]
        return candidates // .subarray(start, end)
    } */

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuditableCardProtobuf) return false

        if (index != other.index) return false
        if (prn != other.prn) return false
        if (phantom != other.phantom) return false
        if (poolId != other.poolId) return false
        if (id != other.id) return false
        if (location != other.location) return false
        if (style != other.style) return false

        if (votePairs.size != other.votePairs.size) return false
        votePairs.forEachIndexed { idx, (contest, candidates) ->
            val otherPair = other.votePairs[idx]
            if (contest != otherPair.first) return false
            if (!candidates.contentEquals(otherPair.second)) return false
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
        result = 31 * result + style.hashCode()
        result = 31 * result + votePairs.hashCode()
        return result
    }

    override fun toString()= buildString {
        appendLine("id='$id', location=$location, index=$index, prn=$prn, phantom=$phantom, poolId=$poolId, ")
        appendLine("   style=$style, votes=$votes")
    }
}
