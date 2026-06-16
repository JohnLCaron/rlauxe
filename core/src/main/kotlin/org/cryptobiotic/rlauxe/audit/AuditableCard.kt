package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrIF
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

/*
interface AuditableCard: CvrIF, SamplingCardIF {
    fun location(): String // enough info to find the card for a manual audit.
    fun index(): Int  // index into the original, canonical list of cards

    fun votes(): Map<Int, IntArray>?   // CVRs and phantoms
    fun styleName(): String

    fun style(): StyleIF?            // "fromCvr" if no cardStyle and its from a CVR (then votes is non null)
    fun possibleContests() : IntArray

    fun hasExactContests(): Boolean // TODO is this needed?

    // fun show(): String
    fun toCvr(): Cvr
} */

interface SamplingCardIF {
    fun hasContest(contestId: Int): Boolean
    fun prn(): Long
}

// mutable style, so we dont need multiple classes
data class AuditableCard (
    val id: String, // enough info to find the card for a manual audit.
    val location: String?, // enough info to find the card for a manual audit.
    val index: Int,  // index into the original, canonical list of cards, aka manifest index
    val prn: Long,   // psuedo random number
    val phantom: Boolean,
    val styleName: String,
    val poolId: Int?, // must be set if its from a CardPool
    val contestIds: IntArray,   // these 3 form the votes map. set style if different
    val contestStarts: IntArray,
    val candidates: IntArray,
): CvrIF, SamplingCardIF {

    // you can change the style but not null it; could also prevent changing altogether after its set
    private var style: StyleIF? = null
    fun setStyle(style: StyleIF): AuditableCard {
        if (styleName != style.name())
            logger.warn{"AuditableCard.setStyle $styleName != ${style.name()}"}
        require(styleName == style.name()) //  || style.name() == "unknown")
        this.style = style
        return this
    }
    fun style(): StyleIF? = style // could work harder so its not null

    private val votes: Map<Int, IntArray>? by lazy {
        if (contestIds.isEmpty()) null else {
            val lastIndex = contestIds.size - 1
            val makeVotes = mutableMapOf<Int, IntArray>()
            contestIds.forEachIndexed { index, contestId ->
                val start = contestStarts[index]
                val end = if (index < lastIndex) contestStarts[index + 1] else candidates.size
                if (start > end || end > candidates.size)
                    logger.error{ "illegal range start=$start end=$end "}
                else
                    makeVotes[contestId] = candidates.sliceArray(start until end)
            }
            makeVotes.toMap()
        }
    }

    private val useCvr = CardStyle.useVotes(styleName)
    init {
        if (useCvr && votes == null) {
            throw RuntimeException("cardStyle '${styleName}' must have non-null votes")
        }
    }

    // CvrIF
    override fun id() = id
    override fun phantom() = phantom
    override fun poolId(): Int? = poolId
    override fun votes(contestId: Int): IntArray? = votes?.get(contestId) // use instead of votes()

    override fun hasContest(contestId: Int): Boolean {
        return if (!useCvr && style != null) style!!.hasContest(contestId)
        else contestIds.contains(contestId)
    }
    override fun hasMarkFor(contestId: Int, candidateId: Int): Int {
        val contestVotes = votes(contestId)
        return if (contestVotes == null) 0
        else if (contestVotes.contains(candidateId)) 1 else 0
    }

    // SamplingCardIF
    override fun prn() = prn

    fun location() = location ?: id()
    fun index() = index
    fun styleName() = styleName
    fun possibleContests() : IntArray {
        return when {
            (!useCvr && style != null) -> style!!.possibleContests()
            else -> votes!!.keys.toList().sorted().toIntArray() // assumes cvrsContainUndervotes, set style if not.
        }
    }

    fun votes(): Map<Int, IntArray>? = votes  // TODO is this needed?
    fun hasExactContests() = style?.hasExactContests() ?: false  // TODO is this needed?
    fun toCvr() = Cvr(id, votes!!, phantom, poolId())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuditableCard) return false

        if (index != other.index) return false
        if (prn != other.prn) return false
        if (phantom != other.phantom) return false
        if (poolId != other.poolId) return false
        if (id != other.id) return false
        if (location != other.location) return false
        if (styleName != other.styleName) return false
        if (!contestIds.contentEquals(other.contestIds)) return false
        if (!contestStarts.contentEquals(other.contestStarts)) return false
        if (!candidates.contentEquals(other.candidates)) return false
        if (style != other.style) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + prn.hashCode()
        result = 31 * result + phantom.hashCode()
        result = 31 * result + (poolId ?: 0)
        result = 31 * result + id.hashCode()
        result = 31 * result + (location?.hashCode() ?: 0)
        result = 31 * result + styleName.hashCode()
        result = 31 * result + contestIds.contentHashCode()
        result = 31 * result + contestStarts.contentHashCode()
        result = 31 * result + candidates.contentHashCode()
        result = 31 * result + (style?.hashCode() ?: 0)
        return result
    }

    override fun toString() = buildString {
        append("AuditableCard(id='$id',")
        if (location != null) append(" location='$location',")
        append(" index=$index, prn=$prn, styleName='$styleName',")
        if (phantom) append(" phantom=$phantom,")
        if (poolId != null) append(" poolId=$poolId,")
        if (votes == null) append(" votes=null") else {
            append(" votes=")
            votes!!.forEach { (key, value) ->
                if (value.size == 1)
                    append(" $key:${value[0]},") // remove the bracket
                else
                    append(" $key:${value.contentToString()},") //show bracket for 0 or > 1
            }
        }
        append(")")
    }

    companion object {
        private val logger = KotlinLogging.logger("AuditableCard")

        fun fromCvr(cvr: Cvr, index: Int, prn: Long): AuditableCard {
            return fromVotes(cvr.id, null, index, prn, cvr.phantom, styleName = CardStyle.fromCvr,
                poolId=cvr.poolId, votes=cvr.votes).setStyle(CardStyle.fromCvrBatch)
        }

        fun fromVotes(id: String, // enough info to find the card for a manual audit.
                      location: String?, // enough info to find the card for a manual audit.
                      index: Int,  // index into the original, canonical list of cards
                      prn: Long,   // psuedo random number
                      phantom: Boolean,
                      styleName: String,
                      poolId: Int?, // must be set if its from a CardPool
                      votes: Map<Int, IntArray>?
        ): AuditableCard {
            val (contestIds, contestStarts, candidates) = if (votes != null)
                makeFromVotes(votes)
            else
                Triple(IntArray(0), IntArray(0),IntArray(0))

            return AuditableCard(id, location, index, prn, phantom, styleName, poolId, contestIds, contestStarts, candidates)
        }

        fun empty(id: String, phantom: Boolean, styleName: String): AuditableCard {
            return fromVotes(id, null, 0, 0, phantom, styleName, null, null)
        }

        fun removeVotes(org: AuditableCard): AuditableCard {
            return AuditableCard(org.id, org.location, org.index, org.prn, org.phantom, org.styleName, org.poolId,
                IntArray(0), IntArray(0),IntArray(0))
        }
    }

}

fun makeVotes( contestIds: IntArray,  contestStarts: IntArray, candidates: IntArray): Map<Int, IntArray> {
    val lastIndex = contestIds.size-1
    val makeVotes = mutableMapOf<Int, IntArray>()
    contestIds.forEachIndexed { index, contestId ->
        val start = contestStarts[index]
        val end = if (index < lastIndex) contestStarts[index+1] else candidates.size
        if (start > end || end > candidates.size)
            print("HEY")
        makeVotes[contestId] = candidates.sliceArray(start until end)
    }
    return makeVotes.toMap()
}

//         val (contestIds, contestStarts, candidates) = makeFromVotes(cvrExport.votes)
fun makeFromVotes(votes: Map<Int, IntArray>?): Triple<IntArray, IntArray, IntArray> {
    if (votes == null) return Triple(IntArray(0), IntArray(0), IntArray(0))

    val contestIds = votes.keys.toList().sorted().toIntArray()
    val candidates = mutableListOf<Int>()
    val contestStarts = mutableListOf<Int>()
    var start = 0

    contestIds.forEach {
        val cands = votes[it]!!
        candidates.addAll(cands.toList())
        contestStarts.add(start)
        start += cands.size
    }
    return Triple(contestIds, contestStarts.toIntArray(), candidates.toIntArray())
}

fun testVotesEqual(votes: Map<Int, IntArray>?, other: Map<Int, IntArray>?): Boolean {
    if ((votes == null) != (other == null)) return false
    if (votes != null) {
        for ((contestId, candidates) in votes) {
            val otherCands = other!![contestId]
            if (!candidates.contentEquals(otherCands)) return false
        }
    }
    return true
}