package org.cryptobiotic.rlauxe.raire



import org.cryptobiotic.rlaux.core.raire.RaireCvr
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit


data class RaireResults(
    val overallExpectedPollsNumber : Int,
    val ballotsInvolvedInAuditNumber : Int,
    val contests: List<RaireContestAudit>,
) {
    fun show() = buildString {
        appendLine("overallExpectedPollsNumber=$overallExpectedPollsNumber ballotsInvolvedInAuditNumber=$ballotsInvolvedInAuditNumber")
        contests.forEach { append(it.show()) }
    }
}

data class RaireContestAudit(
    val name: String,
    val winner: Int,  // the sum of winner and eliminated must be all the candiates
    val eliminated: List<Int>,
    val expectedPollsNumber : Int,
    val expectedPollsPercent : Double,
    val assertions: List<RaireAssertion>,
)  {
    val candidates =  listOf(winner) + eliminated // seems likely

    fun show() = buildString {
        appendLine("  contest $name winner $winner eliminated $eliminated")
        assertions.forEach { append(it.show()) }
    }

    fun toContest(): Contest {
        //    val name: String,
        //    val id: Int,
        //    var candidateNames: Map<String, Int>, // candidate name -> candidate id
        //    val winnerNames: List<String>,
        //    val choiceFunction: SocialChoiceFunction,
        return Contest(
            name,
            name.toInt(),
            candidates.associate{ it.toString() to it },
            listOf(winner.toString()),
            SocialChoiceFunction.IRV,
        )
    }

    fun toContestUnderAudit(ncards: Int): ContestUnderAudit {
        return ContestUnderAudit(
            toContest(),
            ncards,
        )
    }
}

data class RaireAssertion(
    val winner: Int,
    val loser: Int,
    val alreadyEliminated: List<Int>, // already eliminated for the purpose of this assertion
    val assertionType: String,
    val explanation: String,
)  {
    var assorter: RaireAssorter? = null  // bit of a kludge

    fun show() = buildString {
        appendLine("    assertion type '$assertionType' winner $winner loser $loser alreadyEliminated $alreadyEliminated explanation: '$explanation'")
    }

    fun match(winner: Int, loser: Int, winnerType: Boolean, already: List<Int> = emptyList()): Boolean {
        if (this.winner != winner || this.loser != loser) return false
        if (winnerType && (assertionType != "WINNER_ONLY")) return false
        if (!winnerType && (assertionType == "WINNER_ONLY")) return false
        if (winnerType) return true
        return already == alreadyEliminated
    }
}

// add assorters to the assertions
fun RaireContestAudit.addAssorters(): List<RaireAssorter> {
    return this.assertions.map {
        val assort = RaireAssorter(this, it)
        it.assorter = assort
        assort
    }
}


//////////////////////////////////////////////////////////////////////////////////////////////////

class RaireAssorter(contest: RaireContestAudit, val assertion: RaireAssertion): AssorterFunction {
    val contestName = contest.name
    val contestId = contest.name.toInt()
    // I believe this doesnt change in the course of the audit
    val remaining = contest.candidates.filter { !assertion.alreadyEliminated.contains(it) }

    override fun upperBound() = 1.0
    override fun desc() = "RaireAssorter contest ${contestName} type= ${assertion.assertionType} winner=${assertion.winner} loser=${assertion.loser}"
    override fun winner() = assertion.winner
    override fun loser() = assertion.loser

    override fun assort(mvr: CvrIF): Double {
        val rcvr = mvr as RaireCvr
        return if (assertion.assertionType == "WINNER_ONLY") assortWinnerOnly(rcvr)
        else  if (assertion.assertionType == "IRV_ELIMINATION") assortIrvElimination(rcvr)
        else throw RuntimeException("unknown assertionType = $(this.assertionType")
    }

    fun assortWinnerOnly(rcvr: RaireCvr): Double {
        // CVR is a vote for the winner only if it has the winner as its first preference
        val awinner = if (rcvr.get_vote_for(contestId, assertion.winner) == 1) 1 else 0
        // CVR is a vote for the loser if they appear and the winner does not, or they appear before the winner
        val aloser = rcvr.rcv_lfunc_wo( contestId, assertion.winner, assertion.loser)

        //     An assorter must either have an `assort` method or both `winner` and `loser` must be defined
        //    (in which case assort(c) = (winner(c) - loser(c) + 1)/2. )
        return (awinner - aloser + 1) * 0.5
    }

    fun assortIrvElimination(rcvr: RaireCvr): Double {
        // Context is that all candidates in "already_eliminated" have been
        // eliminated and their votes distributed to later preferences
        val awinner = rcvr.rcv_votefor_cand(contestId, assertion.winner, remaining)
        val aloser = rcvr.rcv_votefor_cand(contestId, assertion.loser, remaining)

        return (awinner - aloser + 1) * 0.5
    }
}

fun makeRaireComparisonAudit(rcontests: List<RaireContestAudit>, cvrs : Iterable<Cvr>, riskLimit: Double=0.05): AuditComparison {
    val comparisonAssertions = mutableMapOf<Int, List<ComparisonAssertion>>()

    val contests = mutableListOf<Contest>()
    rcontests.forEach { rcontest ->
        rcontest.addAssorters()
        val contest = rcontest.toContest()
        contests.add(contest)

        val clist = rcontest.assertions.map { assertion ->
            val avgCvrAssortValue = cvrs.map { assertion.assorter!!.assort(it) }.average()
            val comparisonAssorter = ComparisonAssorter(contest, assertion.assorter!!, avgCvrAssortValue)
            ComparisonAssertion(contest, comparisonAssorter)
        }
        comparisonAssertions[contest.id] = clist
    }

    return AuditComparison(AuditType.CARD_COMPARISON, riskLimit, contests, comparisonAssertions)
}
