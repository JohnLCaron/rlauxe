package org.cryptobiotic.rlauxe.core

interface CvrIF {
    val id: String
    val phantom: Boolean
    val votes: Map<Int, IntArray> // contestId -> list of candidate Ids, ranked when Raire
    fun hasContest(contestId: Int): Boolean
    fun hasMarkFor(contestId: Int, candidateId: Int): Int
    fun hasOneVote(contestId: Int, candidates: List<Int>): Boolean
}

// immutable
data class Cvr(
    override val id: String,
    override val votes: Map<Int, IntArray>, // contest : list of candidates voted for; for IRV, ranked hi to lo
    override val phantom: Boolean = false,
): CvrIF {
    override fun hasContest(contestId: Int): Boolean = votes[contestId] != null

    constructor(oldCvr: CvrIF, votes: Map<Int, IntArray>) : this(oldCvr.id, votes, oldCvr.phantom)
    constructor(contest: Int, ranks: List<Int>): this( "testing", mapOf(contest to ranks.toIntArray())) // for quick testing
    constructor(contest: Int, id: String, ranks: List<Int>, phantom: Boolean): this( id, mapOf(contest to ranks.toIntArray()), phantom) // for quick testing

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Cvr

        if (phantom != other.phantom) return false
        if (id != other.id) return false
        for ((contestId, candidates) in votes) {
            if (!candidates.contentEquals(other.votes[contestId])) return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = phantom.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + votes.hashCode()
        return result
    }
}

/** Mutable version of Cvr. sampleNum >= 0  */
class CvrUnderAudit (val cvr: Cvr, var sampleNum: Long = 0L): CvrIF {
    var sampled = false //  # is this CVR in the sample?

    override val id = cvr.id
    override val votes = cvr.votes
    override val phantom = cvr.phantom
    override fun hasContest(contestId: Int) = cvr.hasContest(contestId)
    override fun hasMarkFor(contestId: Int, candidateId: Int) = cvr.hasMarkFor(contestId, candidateId)
    override fun hasOneVote(contestId: Int, candidates: List<Int>) = cvr.hasOneVote(contestId, candidates)

    constructor(id: String, contestIdx: Int) : this( Cvr(id, mapOf(contestIdx to IntArray(0)), false))

    override fun toString() = buildString {
        append("$id ($phantom)")
        votes.forEach { (key, value) -> append(" $key: ${value.contentToString()}")}
    }

    companion object {
        fun makePhantom(cvrId: String, contestId: Int) = CvrUnderAudit(Cvr(contestId, cvrId, emptyList(), true))
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

// The id must uniquely identify the paper ballot. Here it may be some simple thing (seq number) that points to
// the paper ballot location. Its necessary that the system publically commit to that mapping before the Audit,
// as well as to sampleNum.
// TODO conform better to this spec:
// B7. Proper bookkeeping of voter participation records and voter registration, and proper ballot
//      accounting. This gives a trustworthy upper bound on the number of cards validly cast in each
//      contest under audit. This upper bound must come from some procedure distict/decoupled from the voting system/equipment
// B8. The ballots are stored securely, creating a trustworthy but possibly incomplete paper trail (some ballots might be missing).
//      A ballot manifest is created (untrusted, possibly by card style), giving a list of where the ballots are.
//      It may also be a style manifest which tells you which contests are on which ballots. This does not need to be trusted, but the upper bounds on
//      the number of cards for each contest do.
// B9. Untrusted imprinter claims to assign a unique identifier to every card in the manifest. Imprinter
//      commits to the identifiers it claims to have used. Identifiers, once applied, cannot be altered.
//      Once audit starts, identifiers cannot be added.
// B10. Voting system commits to reported winners and possibly other information such as totals, batch
//      subtotals, or CVRs. Public commitment to CVRs can shroud votes, eg using SOBA or VAULT.
//      For card-level comparison audits, each CVR is labeled with a unique identifier from the set of
//      identifiers previously reported (checked later). Identifier is in plaintext even if votes are shrouded.

// C16. Commit to and disclose mapping from PRNG output to identifiers.

// this is ballot_manifest_with_ids
open class BallotWithStyle(val id: String, val ballotStyle: BallotStyle) {
    var sampleNum: Long = 0L
    var sampled: Boolean = false
    var phantom = false

    fun hasContest(contestId: Int): Boolean = ballotStyle.hasContest(contestId)
}


