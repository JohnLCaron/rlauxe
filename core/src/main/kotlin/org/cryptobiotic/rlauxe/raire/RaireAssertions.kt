package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlaux.core.raire.RaireCvr
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.Welford

// The ouput of RAIRE assertion generator, read from JSON files
data class RaireResults(
    val overallExpectedPollsNumber : Int,
    val ballotsInvolvedInAuditNumber : Int,
    val contests: List<RaireContestUnderAudit>,
) {
    fun show() = buildString {
        appendLine("overallExpectedPollsNumber=$overallExpectedPollsNumber ballotsInvolvedInAuditNumber=$ballotsInvolvedInAuditNumber")
        contests.forEach { append(it.show()) }
    }
}

class RaireContestUnderAudit(
    contest: Contest,
    val winner: Int,  // the sum of winner and eliminated must be all the candiates in the contest
    val eliminated: List<Int>,
    val expectedPollsNumber : Int,
    val expectedPollsPercent : Double,
    val assertions: List<RaireAssertion>,
): ContestUnderAudit(contest) {
    val candidates =  listOf(winner) + eliminated

    // TODO eliminate
    fun makeAssorters(): List<RaireAssorter> {
        return this.assertions.map {
            RaireAssorter(this, it)
        }
    }

    override fun makeComparisonAssertions(cvrs : Iterable<CvrUnderAudit>) {
            this.comparisonAssertions = assertions.map { assertion ->
            val assorter = RaireAssorter(this, assertion)
            val welford = Welford()
            cvrs.forEach { cvr ->
                if (cvr.hasContest(contest.id)) {
                    welford.update(assorter.assort(cvr))
                }
            }
            val comparisonAssorter = ComparisonAssorter(contest, assorter, welford.mean)
            println(" assertion ${assertion} margin=${comparisonAssorter.margin} avg=${comparisonAssorter.avgCvrAssortValue}")
            ComparisonAssertion(contest, comparisonAssorter)
        }
    }

    fun show() = buildString {
        appendLine("  contest $name winner $winner eliminated $eliminated")
        assertions.forEach { append(it.show()) }
    }

    companion object {
        fun make(name: String,
                 winner: Int,  // the sum of winner and eliminated must be all the candiates
                 eliminated: List<Int>,
                 expectedPollsNumber : Int,
                 expectedPollsPercent : Double,
                 assertions: List<RaireAssertion>): RaireContestUnderAudit {

            val candidates =  listOf(winner) + eliminated // the sum of winner and eliminated must be all the candiates
            val contest = Contest(
                name,
                name.toInt(), // ??
                candidates.associate{ it.toString() to it },
                listOf(winner.toString()),
                SocialChoiceFunction.IRV,
            )
            return RaireContestUnderAudit(contest, winner, eliminated, expectedPollsNumber, expectedPollsPercent, assertions)
        }
    }
}

enum class RaireAssertionType(val aname:String) {
    winner_only("NEB"),
    irv_elimination("NEN");

    companion object {
        fun fromString(s:String) : RaireAssertionType {
            return when (s.lowercase()) {
                "winner_only" -> winner_only
                "irv_elimination" -> irv_elimination
                else -> throw RuntimeException("Unknown RaireAssertionType '$s'")
            }
        }
    }
}

data class RaireAssertion(
    val winner: Int,
    val loser: Int,
    val alreadyEliminated: List<Int>, // already eliminated for the purpose of this assertion
    val assertionType: RaireAssertionType,
    val explanation: String,
)  {
    fun show() = buildString {
        appendLine("    assertion type '$assertionType' winner $winner loser $loser alreadyEliminated $alreadyEliminated explanation: '$explanation'")
    }
}

//////////////////////////////////////////////////////////////////////////////////////////////////

// This a primitive assorter.
class RaireAssorter(contest: RaireContestUnderAudit, val assertion: RaireAssertion): AssorterFunction {
    val contestName = contest.name
    val contestId = contest.name.toInt()
    val remaining = contest.candidates.filter { !assertion.alreadyEliminated.contains(it) }

    override fun upperBound() = 1.0
    override fun desc() = buildString {
        append("RaireAssorter contest ${contestName} type= ${assertion.assertionType} winner=${assertion.winner} loser=${assertion.loser}")
        if (assertion.assertionType == RaireAssertionType.irv_elimination) append(" alreadyElim=${assertion.alreadyEliminated}")
    }
    override fun winner() = assertion.winner
    override fun loser() = assertion.loser

    override fun assort(mvr: CvrIF): Double {
        if (mvr.phantom) {
            return 0.5
        }
        val rcvr = RaireCvr(mvr)
        return if (assertion.assertionType == RaireAssertionType.winner_only) assortWinnerOnly(rcvr)
        else  if (assertion.assertionType == RaireAssertionType.irv_elimination) assortIrvElimination(rcvr)
        else throw RuntimeException("unknown assertionType = $(this.assertionType")
    }

    // aka NEB
    fun assortWinnerOnly(rcvr: RaireCvr): Double {
        // CVR is a vote for the winner only if it has the winner as its first preference (rank == 1)
        val awinner = if (rcvr.get_vote_for(contestId, assertion.winner) == 1) 1 else 0
        // CVR is a vote for the loser if they appear and the winner does not, or they appear before the winner
        val aloser = rcvr.rcv_lfunc_wo( contestId, assertion.winner, assertion.loser)
        return (awinner - aloser + 1) * 0.5 // affine transform from (-1, 1) -> (0, 1)
    }

    // aka NEN
    fun assortIrvElimination(rcvr: RaireCvr): Double {
        // Context is that all candidates in "already_eliminated" have been
        // eliminated and their votes distributed to later preferences
        val awinner = rcvr.rcv_votefor_cand(contestId, assertion.winner, remaining)
        val aloser = rcvr.rcv_votefor_cand(contestId, assertion.loser, remaining)
        return (awinner - aloser + 1) * 0.5 // affine transform from (-1, 1) -> (0, 1)
    }
}
