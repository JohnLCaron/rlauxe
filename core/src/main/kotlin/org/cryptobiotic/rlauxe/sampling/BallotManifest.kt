package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.ContestInfo

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
    val ballotStyles: List<BallotStyle>
) {
    fun getBallotStyleFor(ballotStyleId: Int): BallotStyle {
        return ballotStyles.find { it.id == ballotStyleId }!!
    }
}

data class Ballot(
    val id: String,
    val phantom: Boolean = false,
    val ballotStyleId: Int?,
)

class BallotUnderAudit (val ballot: Ballot, var sampleNum: Long = 0L) {
    var sampled = false //  # is this CVR in the sample?
    val id = ballot.id
    val phantom = ballot.phantom
}

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
        append(" BallotStyle(contestNames=$contestNames, contestIds=$contestIds, numberBallots=$ncards")
    }

    companion object {
        private var styleId = 0;
        fun makeWithInfo(contestNames: List<String>, contests: List<ContestInfo>, numberBallots: Int?): BallotStyle {
            styleId++
            val contestIds = contestNames.map { name -> contests.find { contest -> contest.name == name} ?.id ?: throw RuntimeException("Cant find $name") }
            return BallotStyle("style$styleId", styleId, contestNames, contestIds, numberBallots)
        }
        fun make(contestNames: List<String>, contestIds: List<Int>, numberBallots: Int?): BallotStyle {
            styleId++
            return BallotStyle("style$styleId", styleId, contestNames, contestIds, numberBallots)
        }
    }
}


