package org.cryptobiotic.rlauxe.audit

data class BallotManifest(
    val ballots: List<Ballot>,
    val ballotStyles: List<BallotStyle> // empty if style info not available
)

data class BallotManifestUnderAudit(
    val ballots: List<BallotUnderAudit>,
    val ballotStyles: List<BallotStyle> // empty if style info not available
)

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

data class BallotUnderAudit (val ballot: Ballot, val index: Int, val sampleNum: Long) : BallotOrCvr {
    val id = ballot.id
    val phantom = ballot.phantom

    override fun hasContest(contestId: Int) = ballot.hasContest(contestId)
    override fun sampleNumber() = sampleNum
    override fun index() = index
}

// The term ballot style generally refers to the set of contests on a given voter’s ballot. (Ballot
// style can also encode precinct information, i.e., even if voters in two different precincts are
// eligible to vote in the same set of contests, ballots for the two precincts are considered to
// be of two different styles.) Here, we use card style to refer to the set of contests on a given
// ballot card, and CSD to refer to card-style data for an election. (MoreStyle p.2)

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


