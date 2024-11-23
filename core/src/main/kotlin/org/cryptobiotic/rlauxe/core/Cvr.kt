package org.cryptobiotic.rlauxe.core

interface CvrIF {
    val id: String
    val phantom: Boolean
    val votes: Map<Int, IntArray> // contestId -> list of candidate Ids, ranked when Raire
    fun hasContest(contestId: Int): Boolean
    fun hasMarkFor(contestId: Int, candidateId: Int): Int
    fun hasOneVote(contestId: Int, candidates: List<Int>): Boolean
}

// there must be an entry in votes for every contest on the ballot, even if no candidate was voted for
open class Cvr(
    override val id: String,
    override val votes: Map<Int, IntArray>, // contest : list of candidates voted for; for IRV, ranked hi to lo
): CvrIF {
    override val phantom = false
    override fun hasContest(contestId: Int): Boolean = votes[contestId] != null

    constructor(oldCvr: CvrIF, votes: Map<Int, IntArray>) : this(oldCvr.id, votes)
    constructor(contest: Int, ranks: List<Int>): this( "testing", mapOf(contest to ranks.toIntArray())) // for quick testing
    constructor(contest: Int, id: String, ranks: List<Int>): this( id, mapOf(contest to ranks.toIntArray())) // for quick testing

    // Let 1candidate(bi) = 1 if ballot i has a mark for candidate, and 0 if not; SHANGRLA section 2, page 4
    override fun hasMarkFor(contestId: Int, candidateId: Int): Int {
        val contestVotes = votes[contestId]
        return if (contestVotes == null) 0
               else if (contestVotes.contains(candidateId)) 1 else 0
    }

    // Is there exactly one vote in the contest among the given candidates?
    override fun hasOneVote(contestId: Int, candidates: List<Int>): Boolean {
        val contestVotes = this.votes[contestId] ?: return false
        val totalVotes = contestVotes.filter{ candidates.contains(it) }.count()
        return (totalVotes == 1)
    }

    override fun toString() = buildString {
        append("$id ($phantom)")
        votes.forEach { (key, value) -> append(" $key: ${value.contentToString()}")}
    }
}

/** Mutable version of Cvr. sampleNum >= 0  */
class CvrUnderAudit(val cvr: Cvr, override val phantom: Boolean, var sampleNum: Long = 0L): CvrIF {
    override val id = cvr.id
    override val votes = cvr.votes

    // dont really need, just used by computeTotalSampleSize
    var sampled = false //  # is this CVR in the sample?
    var p: Double = 0.0
    var used = false

    override fun hasContest(contestId: Int) = cvr.hasContest(contestId)
    override fun hasMarkFor(contestId: Int, candidateId: Int) = cvr.hasMarkFor(contestId, candidateId)
    override fun hasOneVote(contestId: Int, candidates: List<Int>) = cvr.hasOneVote(contestId, candidates)

    constructor(id: String, contestIdx: Int) : this( Cvr(id, mapOf(contestIdx to IntArray(0))), false)

    override fun toString() = buildString {
        append("$id ($phantom)")
        votes.forEach { (key, value) -> append(" $key: ${value.contentToString()}")}
    }

    companion object {
        fun fromCvrIF(cvr: CvrIF, phantom: Boolean) = if (cvr is CvrUnderAudit) cvr else CvrUnderAudit( cvr as Cvr, phantom)
    }
}

///////////////////////////////////////////////////////////////////////////////

data class BallotStyle(val contestNames: List<String>, val contestIds: List<Int>, val ncards: Int) {
    fun hasContest(contestId: Int) = contestIds.contains(contestId)

    override fun toString() = buildString {
        append(" BallotStyle(contestNames=$contestNames, contestIds=$contestIds, ncards=$ncards")
    }

    companion object {
        fun make(contestNames: List<String>, contests: List<ContestInfo>, ncards: Int): BallotStyle {
            val contestIds = contestNames.map { name -> contests.find { contest -> contest.name == name} ?.id ?: throw RuntimeException("Cant find $name") }
            return BallotStyle(contestNames, contestIds, ncards)
        }
    }
}

// id should probably be String
open class BallotUnderAudit(val id: Int, val ballotStyle: BallotStyle) {
    var sampleNum: Long = 0L
    var sampled: Boolean = false // needed?
    var p = 0.0
    var phantom = false

    fun hasContest(contestId: Int): Boolean = ballotStyle.hasContest(contestId)
}

