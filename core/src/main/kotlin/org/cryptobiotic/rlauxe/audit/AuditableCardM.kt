package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrIF
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

interface AuditableCardIF: CvrIF, SamplingCardIF {
    fun location(): String // enough info to find the card for a manual audit.
    fun index(): Int  // index into the original, canonical list of cards

    fun votes(): Map<Int, IntArray>?   // CVRs and phantoms
    fun styleName(): String

    fun style(): StyleIF?            // "fromCvr" if no cardStyle and its from a CVR (then votes is non null)
    fun possibleContests() : IntArray
    // TODO is hasStyle really card specific? contest? audit?
    //    is it the same as "consistentSampling" or something else ??
    fun hasExactContests(): Boolean // TODO

    // fun show(): String
    fun toCvr(): Cvr  // TODO can we get rid of?
}

interface SamplingCardIF {
    fun hasContest(contestId: Int): Boolean
    fun prn(): Long
}

data class AuditableCardM (
    val id: String, // enough info to find the card for a manual audit.
    val location: String?, // enough info to find the card for a manual audit.
    val index: Int,  // index into the original, canonical list of cards
    val prn: Long,   // psuedo random number
    val phantom: Boolean,
    val styleName: String,
    val poolId: Int?, // must be set if its from a CardPool
    val contestIds: IntArray,   // these form the votes map. set style if different
    val contestStarts: IntArray,
    val candidates: IntArray,
): AuditableCardIF {
    // you can change the style but not null it; could also prevent changing altogether after its set
    private var style: StyleIF? = null
    fun setStyle(style: StyleIF): AuditableCardM {
        if (styleName != style.name())
            print("wtf?")
        require(styleName == style.name())
        this.style = style
        return this
    }
    override fun style(): StyleIF? = style // TODO

    // TODO could ignore useCvr
    private val useCvr = CardStyle.useVotes(styleName)
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

    override fun id() = id
    override fun location() = location ?: id()
    override fun index() = index
    override fun prn() = prn
    override fun phantom() = phantom
    override fun poolId(): Int? = poolId
    override fun styleName() = styleName

    override fun votes(): Map<Int, IntArray>? = votes
    override fun votes(contestId: Int): IntArray? = votes?.get(contestId)

    override fun hasContest(contestId: Int): Boolean {
        return if (!useCvr && style != null) style!!.hasContest(contestId)
        else contestIds.contains(contestId)
    }

    override fun possibleContests() : IntArray {
        return when {
            (!useCvr && style != null) -> style!!.possibleContests()
            else -> votes!!.keys.toList().sorted().toIntArray() // assumes cvrsContainUndervotes, set style if not.
        }
    }

    override fun hasMarkFor(contestId: Int, candidateId: Int): Int {
        val contestVotes = votes(contestId)
        return if (contestVotes == null) 0
                else if (contestVotes.contains(candidateId)) 1 else 0
    }

    override fun hasExactContests() = style?.hasExactContests() ?: false

    override fun toCvr() = Cvr(id, votes!!, phantom, poolId()) // TODO can we get rid of?

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuditableCardM) return false

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

    companion object {
        private val logger = KotlinLogging.logger("AuditableCardM")

        fun fromCvr(cvr: Cvr, index: Int, prn: Long): AuditableCardM {
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
        ): AuditableCardM {
            val (contestIds, contestStarts, candidates) = if (votes != null)
                makeFromVotes(votes)
            else
                Triple(IntArray(0), IntArray(0),IntArray(0))

            return AuditableCardM(id, location, index, prn, phantom, styleName, poolId, contestIds, contestStarts, candidates)
        }

        fun removeVotes(org: AuditableCardM): AuditableCardM {
            return AuditableCardM(org.id, org.location, org.index, org.prn, org.phantom, org.styleName, org.poolId,
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
fun makeFromVotes(votes: Map<Int, IntArray>): Triple<IntArray, IntArray, IntArray> {
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