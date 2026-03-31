package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrIF

// The information we have on each physical card in the audit; the complete set is the CardManifest.

data class AuditableCard (
    val location: String, // enough info to find the card for a manual audit.
    val index: Int,  // index into the original, canonical list of cards
    val prn: Long,   // psuedo random number
    val phantom: Boolean,
    val votes: Map<Int, IntArray>?,   // CVRs and phantoms
    val batch: BatchIF,       // batch reference. CLCA dont need unless cvrsContainUndervotes = false
): CvrIF, CardIF {

    init {
        if (Batch.useVotes(batch.name()) && votes == null) {
            throw RuntimeException("batch '${batch.name()}' must have non-null votes")
        }
    }

    constructor(card: CardWithBatchName, batch: BatchIF): this(card.location, card.index, card.prn, card.phantom, card.votes, batch)
    constructor(cvr: Cvr, index: Int, prn: Long): this(cvr.id, index, prn, cvr.phantom, cvr.votes, batch = Batch.fromCvrBatch)

    fun toCvr() = Cvr(location, votes!!, phantom, poolId()) // TODO can we get rid of?

    // "may have contest". Cvr hasContest does not allow missing, ie is not the same as "may have contest"
    override fun hasContest(contestId: Int): Boolean {
        return when {
            Batch.useVotes(batch.name()) -> votes!![contestId] != null // assumes cvrsContainUndervotes, use batch if not.
            else -> batch.hasContest(contestId)
        }
    }

    override fun location() = location
    override fun index() = index
    override fun prn() = prn
    override fun isPhantom() = phantom
    override fun votes() = votes
    override fun votes(contestId: Int): IntArray? = votes?.get(contestId)
    override fun poolId(): Int? = if (batch is CardPoolIF) batch.id() else null // TODO check
    override fun batchName() = batch.name()

    override fun hasMarkFor(contestId: Int, candidateId: Int): Int {
        val contestVotes = votes?.get(contestId)
        return if (contestVotes == null) 0
        else if (contestVotes.contains(candidateId)) 1 else 0
    }

    // return sorted
    fun possibleContests() : IntArray {
        return when {
            Batch.useVotes(batch.name()) -> votes!!.keys.toList().sorted().toIntArray() // assumes cvrsContainUndervotes, use batch if not.
            else -> batch.possibleContests().toList().sorted().toIntArray()
        }
    }

    fun hasStyle() = batch.hasSingleCardStyle()

    //// Kotlin data class doesnt handle IntArray correctly
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuditableCard) return false

        if (index != other.index) return false
        if (prn != other.prn) return false
        if (phantom != other.phantom) return false
        if (location != other.location) return false
        if (batch != other.batch) return false

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
        result = 31 * result + batch.hashCode()
        votes?.forEach { (contestId, candidates) -> result = 31 * result + contestId.hashCode() + candidates.contentHashCode() }
        return result
    }

    override fun toString() = buildString {
        append("AuditableCard(location='$location', index=$index, prn=$prn, phantom=$phantom")
        append(", has batch ${batch.name()} ${batch.id()} possibleContests=${batch.possibleContests().contentToString()} singleStyle=${batch.hasSingleCardStyle()}")
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
    fun location(): String // enough info to find the card for a manual audit.
    fun index(): Int  // index into the original, canonical list of cards
    fun prn(): Long   // psuedo random number
    fun isPhantom(): Boolean

    fun votes(): Map<Int, IntArray>?   // CVRs and phantoms
    fun poolId(): Int?                 // must be set if its from a CardPool
    fun batchName(): String            // batch name: "fromCvr" if no batch and its from a CVR (then votes is non null)
}

// for serialization and ElectionBuilder
data class CardWithBatchName (
    val location: String, // enough info to find the card for a manual audit.
    val index: Int,  // index into the original, canonical list of cards
    val prn: Long,   // psuedo random number
    val phantom: Boolean,

    val votes: Map<Int, IntArray>?,   // CVRs and phantoms
    val poolId: Int?,                 // must be set if its from a CardPool  TODO verify batch name, poolId
    val batchName: String,            // batch name: "fromCvr" if no batch and its from a CVR (then votes is non null)
): CardIF {

    constructor(card: AuditableCard): this(card.location, card.index, card.prn, card.phantom, card.votes, card.poolId(), card.batchName())

    override fun location() = location
    override fun index() = index
    override fun prn() = prn
    override fun isPhantom() = phantom
    override fun votes() = votes
    override fun poolId() = poolId
    override fun batchName() = batchName

    fun toCvr() = Cvr(location, votes!!, phantom, poolId) // TODO get rid of

    //// Kotlin data class doesnt handle IntArray and List<IntArray> correctly
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CardWithBatchName) return false

        if (index != other.index) return false
        if (prn != other.prn) return false
        if (phantom != other.phantom) return false
        if (poolId != other.poolId) return false
        if (location != other.location) return false
        if (batchName != other.batchName) return false

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
        result = 31 * result + batchName.hashCode()
        votes?.forEach { (contestId, candidates) -> result = 31 * result + contestId.hashCode() + candidates.contentHashCode() }
        return result
    }

    override fun toString() = buildString {
        append("CardWithBatchName(location='$location', index=$index, prn=$prn, phantom=$phantom")
        if (poolId != null) append(", poolId=$poolId")
        append(", batchName='$batchName'")
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