package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.ContestUnderAudit

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


data class BallotManifest(
    val ballots: List<Ballot>,
    val ballotStyles: List<BallotStyle> // empty if style info not available
)

data class BallotManifestUnderAudit(
    val ballots: List<BallotUnderAudit>,
    val ballotStyles: List<BallotStyle> // empty if style info not available
)

interface BallotOrCvr {
    fun hasContest(contestId: Int): Boolean
    fun sampleNumber(): Long
    fun isSampled(): Boolean
    fun setIsSampled(isSampled: Boolean): BallotOrCvr

    fun hasOneOrMoreContest(contests: List<ContestRound>): Boolean {
        for (contest in contests) {
            if (hasContest(contest.id)) return true
        }
        return false
    }
}

data class Ballot(
    val id: String,
    val phantom: Boolean = false,
    val ballotStyle: BallotStyle?, // if hasStyles
    val contestIds: List<Int>? = null, // if hasStyles, instead of BallotStyles
) {
    fun hasContest(contestId: Int): Boolean {
        if (ballotStyle != null) return ballotStyle.hasContest(contestId)
        if (contestIds != null) return contestIds.find{ it == contestId } != null
        return false
    }
}

data class BallotUnderAudit (val ballot: Ballot, val sampleNum: Long) : BallotOrCvr {
    var sampled = false //  # is this in the sample?
    val id = ballot.id
    val phantom = ballot.phantom

    override fun hasContest(contestId: Int) = ballot.hasContest(contestId)
    override fun sampleNumber() = sampleNum
    override fun setIsSampled(isSampled: Boolean): BallotUnderAudit {
        this.sampled = isSampled
        return this
    }
    override fun isSampled() = sampled
}

// The term ballot style generally refers to the set of contests on a given voter’s ballot. (Ballot
// style can also encode precinct information, i.e., even if voters in two different precincts are
// eligible to vote in the same set of contests, ballots for the two precincts are considered to
// be of two different styles.) Here, we use card style to refer to the set of contests on a given
// ballot card, and CSD to refer to card-style data for an election. (MoreStyle p.2)

// Auditors compare upper bounds (possibly by style) to numbers in manifest (PubliclyVerifiable)
data class BallotStyle(
    val name: String,
    val id: Int,
    val contestNames: List<String>,
    val contestIds: List<Int>,
    val numberOfBallots: Int?,
) {
    val ncards = numberOfBallots ?: 0
    fun hasContest(contestId: Int) = contestIds.contains(contestId)

    override fun toString() = buildString {
        append("BallotStyle($id, contestIds=$contestIds, numberBallots=$ncards")
    }

    companion object {
        fun make(styleId: Int, contestNames: List<String>, contestIds: List<Int>, numberBallots: Int?): BallotStyle {
            return BallotStyle("style$styleId", styleId, contestNames, contestIds, numberBallots)
        }
    }
}


