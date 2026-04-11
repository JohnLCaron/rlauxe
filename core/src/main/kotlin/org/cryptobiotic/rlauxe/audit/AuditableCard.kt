package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrIF

// The information we have on each physical card in the audit; the complete set is the CardManifest.

data class AuditableCard (
    val id: String, // enough info to find the card for a manual audit.
    val location: String?, // enough info to find the card for a manual audit.
    val index: Int,  // index into the original, canonical list of cards
    val prn: Long,   // psuedo random number
    val phantom: Boolean,
    val poolId: Int?, // must be set if its from a CardPool
    val votes: Map<Int, IntArray>?,   // CVRs and phantoms
    val style: StyleIF,
): CvrIF, CardIF {

    init {
        if (CardStyle.useVotes(style.name()) && votes == null) {
            throw RuntimeException("cardStyle '${style.name()}' must have non-null votes")
        }
    }

    constructor(card: CardWithBatchName, cardStyle: StyleIF): this(card.id, card.location, card.index, card.prn, card.phantom, card.poolId, card.votes, cardStyle)
    constructor(cvr: Cvr, index: Int, prn: Long): this(cvr.id, null, index, prn, cvr.phantom, cvr.poolId, cvr.votes, style = CardStyle.fromCvrBatch)

    fun toCvr() = Cvr(id, votes!!, phantom, poolId()) // TODO can we get rid of?

    // "may have contest". Cvr hasContest does not allow missing, ie is not the same as "may have contest"
    override fun hasContest(contestId: Int): Boolean {
        return when {
            CardStyle.useVotes(style.name()) -> votes!![contestId] != null // assumes cvrsContainUndervotes, use cardStyle if not.
            else -> style.hasContest(contestId)
        }
    }

    override fun id() = id
    override fun location() = location ?: id()
    override fun index() = index
    override fun prn() = prn
    override fun isPhantom() = phantom
    override fun votes() = votes
    override fun votes(contestId: Int): IntArray? = votes?.get(contestId)
    override fun poolId(): Int? = poolId
    override fun styleName() = style.name()

    override fun hasMarkFor(contestId: Int, candidateId: Int): Int {
        val contestVotes = votes?.get(contestId)
        return if (contestVotes == null) 0
        else if (contestVotes.contains(candidateId)) 1 else 0
    }

    // return sorted
    fun possibleContests() : IntArray {
        return when {
            CardStyle.useVotes(style.name()) -> votes!!.keys.toList().sorted().toIntArray() // assumes cvrsContainUndervotes, use cardStyle if not.
            else -> style.possibleContests().toList().sorted().toIntArray()
        }
    }

    fun hasStyle() = style.hasExactContests()

    //// Kotlin data class doesnt handle IntArray correctly
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuditableCard) return false

        if (index != other.index) return false
        if (prn != other.prn) return false
        if (phantom != other.phantom) return false
        if (location != other.location) return false
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
        result = 31 * result + location.hashCode()
        result = 31 * result + style.hashCode()
        votes?.forEach { (contestId, candidates) -> result = 31 * result + contestId.hashCode() + candidates.contentHashCode() }
        return result
    }

    override fun toString() = buildString {
        append("AuditableCard(id='$id', ")
        if (location != null) append("location='$location', ")
        append("index=$index, prn=$prn, phantom=$phantom")
        if (poolId != null) append("poolId=$poolId, ")
        append(", has cardStyle ${style.name()} ${style.id()} possibleContests=${style.possibleContests().contentToString()} singleStyle=${style.hasExactContests()}")
        append(")")
        if (votes != null) {
            appendLine()
            append("  votes: ")
            votes.forEach { id, vote -> append("$id:${vote.contentToString()}, ") }
        }
    }
}

// lets us serialize either CardNoStyle or AuditableCard
interface CardIF {
    fun id(): String // enough info to find the card for a manual audit.
    fun location(): String // enough info to find the card for a manual audit.
    fun index(): Int  // index into the original, canonical list of cards
    fun prn(): Long   // psuedo random number
    fun isPhantom(): Boolean

    fun votes(): Map<Int, IntArray>?   // CVRs and phantoms
    fun poolId(): Int?                 // must be set if its from a CardPool
    fun styleName(): String            // "fromCvr" if no cardStyle and its from a CVR (then votes is non null)
}

// for serialization and ElectionBuilder
data class CardWithBatchName (
    val id: String,
    val location: String?, // enough info to find the card for a manual audit.
    val index: Int,  // index into the original, canonical list of cards
    val prn: Long,   // psuedo random number
    val phantom: Boolean,

    val votes: Map<Int, IntArray>?,   // CVRs and phantoms
    val poolId: Int?,                 // must be set if its from a CardPool
    val styleName: String,
): CardIF {

    constructor(card: AuditableCard): this(card.id, card.location, card.index, card.prn, card.phantom, card.votes, card.poolId(), card.styleName())

    override fun id() = id
    override fun location() = location ?: id()
    override fun index() = index
    override fun prn() = prn
    override fun isPhantom() = phantom
    override fun votes() = votes
    override fun poolId() = poolId
    override fun styleName() = styleName

    fun toCvr() = Cvr(id, votes!!, phantom, poolId) // TODO get rid of

    //// Kotlin data class doesnt handle IntArray and List<IntArray> correctly
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CardWithBatchName) return false

        if (index != other.index) return false
        if (prn != other.prn) return false
        if (phantom != other.phantom) return false
        if (poolId != other.poolId) return false
        if (location != other.location) return false
        if (styleName != other.styleName) return false

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
        result = 31 * result + location.hashCode()
        result = 31 * result + styleName.hashCode()
        votes?.forEach { (contestId, candidates) -> result = 31 * result + contestId.hashCode() + candidates.contentHashCode() }
        return result
    }

    override fun toString() = buildString {
        append("CardWithBatchName(id='$id', ")
        if (location != null) append("location='$location', ")
        append("index=$index, prn=$prn, phantom=$phantom")
        if (poolId != null) append(", poolId=$poolId")
        append(", styleName='$styleName'")
        append(")")
        if (votes != null) {
            appendLine()
            append("  votes: ")
            votes.forEach { id, vote -> append("$id:${vote.contentToString()}, ") }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger("CardWithBatchName")
    }
}