package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlaux.core.raire.RaireCvr
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.mean2margin

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

    // add assorters to the assertions, could be in the constructor?
    fun addAssorters(): List<RaireAssorter> {
        return this.assertions.map {
            val assort = RaireAssorter(this, it)
            it.assorter = assort
            assort
        }
    }

    override fun makeComparisonAssertions(cvrs : Iterable<CvrUnderAudit>) {
        this.assertions.forEach {
            val assort = RaireAssorter(this, it)
            it.assorter = assort
        }

        // TODO coroutines ??
        val stopwatch = Stopwatch()
        this.comparisonAssertions = assertions.map { assertion ->
            val welford = Welford()
            cvrs.forEach { cvr ->
                if (cvr.hasContest(contest.id)) {
                    welford.update(assertion.assorter!!.assort(cvr))
                }
            }
            val comparisonAssorter = ComparisonAssorter(contest, assertion.assorter!!, welford.mean)
            println(" assertion ${assertion} has margin ${comparisonAssorter.margin}")
            ComparisonAssertion(contest, comparisonAssorter)
        }
        println(" that took $stopwatch")

        val margins = comparisonAssertions.map { assert ->
            mean2margin(assert.assorter.avgCvrAssortValue)
        }
        val minMargin = margins.min()
        this.minAssert = comparisonAssertions.find { it.assorter.margin == minMargin }
        println("min = $minMargin minAssert = $minAssert")
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

data class RaireAssertion(
    val winner: Int,
    val loser: Int,
    val alreadyEliminated: List<Int>, // already eliminated for the purpose of this assertion
    val assertionType: String,
    val explanation: String,
)  {
    // TODO is it ok to have this state ??
    var assorter: RaireAssorter? = null  // TODO bit of a kludge, added after construction

    fun show() = buildString {
        appendLine("    assertion type '$assertionType' winner $winner loser $loser alreadyEliminated $alreadyEliminated explanation: '$explanation'")
    }

    // testing
    fun match(winner: Int, loser: Int, winnerType: Boolean, already: List<Int> = emptyList()): Boolean {
        if (this.winner != winner || this.loser != loser) return false
        if (winnerType && (assertionType != "WINNER_ONLY")) return false
        if (!winnerType && (assertionType == "WINNER_ONLY")) return false
        if (winnerType) return true
        return already == alreadyEliminated
    }
}


//////////////////////////////////////////////////////////////////////////////////////////////////

// This a primitive assorter.
class RaireAssorter(contest: RaireContestUnderAudit, val assertion: RaireAssertion): AssorterFunction {
    val contestName = contest.name
    val contestId = contest.name.toInt()
    val remaining = contest.candidates.filter { !assertion.alreadyEliminated.contains(it) }

    override fun upperBound() = 1.0
    override fun desc() = "RaireAssorter contest ${contestName} type= ${assertion.assertionType} winner=${assertion.winner} loser=${assertion.loser}"
    override fun winner() = assertion.winner
    override fun loser() = assertion.loser

    override fun assort(mvr: CvrIF): Double {
        // TODO clumsy
        val rcvr: RaireCvr = when (mvr) {
            is CvrUnderAudit -> {
                if (!(mvr.cvr is RaireCvr))
                    println("hey")
                mvr.cvr as RaireCvr
            }
            is RaireCvr -> mvr
            else -> throw RuntimeException()
        }
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
