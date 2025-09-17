package org.cryptobiotic.rlauxe.audit

// used for polling audits using List<CardLocation> instead of List<Cvr>
// AuditableCard serializes both, adding the original index in the manifest and the prn.
data class CardLocationManifest(
    val cardLocations: List<CardLocation>,
    val cardStyles: List<CardStyle> // empty if style info not available
)

// if hasStyles, then either cardStyle or contestIds is non null; if !hasStyles then both are null
data class CardLocation(
    val location: String,
    val phantom: Boolean = false,
    val cardStyle: CardStyle?, // if hasStyles (or)
    val contestIds: List<Int>? = null, // if hasStyles
) {
    fun hasContest(contestId: Int): Boolean {
        if (cardStyle != null) return cardStyle.hasContest(contestId)
        if (contestIds != null) return contestIds.find{ it == contestId } != null
        return false
    }
    fun contests(): IntArray {
        return if (cardStyle != null) cardStyle.contestIds.toIntArray()
        else if (contestIds != null) contestIds.toIntArray()
        else intArrayOf()
    }
}

// The term ballot style generally refers to the set of contests on a given voter’s ballot. (Ballot
// style can also encode precinct information, i.e., even if voters in two different precincts are
// eligible to vote in the same set of contests, ballots for the two precincts are considered to
// be of two different styles.) Here, we use card style to refer to the set of contests on a given
// ballot card, and CSD to refer to card-style data for an election. (MoreStyle p.2)

// essentially, CardStyle factors out the contestIds, which the CardLocation references, so its a form of normalization
data class CardStyle(
    val name: String,
    val id: Int,
    val contestNames: List<String>,
    val contestIds: List<Int>,
    val numberOfCards: Int?,
) {
    val ncards = numberOfCards ?: 0
    fun hasContest(contestId: Int) = contestIds.contains(contestId)

    override fun toString() = buildString {
        append("CardStyle('$name' ($id), contestIds=$contestIds")
    }

    companion object {
        fun make(styleId: Int, contestNames: List<String>, contestIds: List<Int>, numberBallots: Int?): CardStyle {
            return CardStyle("style$styleId", styleId, contestNames, contestIds, numberBallots)
        }
    }
}


