package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.util.margin2mean

// The output of RAIRE assertion generator, read from JSON files
data class RaireResults(
    val overallExpectedPollsNumber : Int,
    val ballotsInvolvedInAuditNumber : Int,
    val contests: List<RaireContestUnderAudit>,
) {
    fun show() = buildString {
        appendLine("RaireResults: overallExpectedPollsNumber=$overallExpectedPollsNumber ballotsInvolvedInAuditNumber=$ballotsInvolvedInAuditNumber")
        contests.forEach { append(it.show()) }
    }
}

data class RaireContest(
    override val info: org.cryptobiotic.rlauxe.core.ContestInfo,
    override val winnerNames: List<String>,
    override val Nc: Int,
    override val Np: Int,
) : ContestIF {
    override val winners: List<Int>
    override val losers: List<Int>
    override val ncandidates = info.candidateIds.size
    override val id = info.id
    override val choiceFunction = info.choiceFunction

    init {
        winners = winnerNames.map { info.candidateNames[it]!! }
        val mlosers = mutableListOf<Int>()
        info.candidateNames.forEach { (_, id) ->
            if (!winners.contains(id)) mlosers.add(id)
        }
        losers = mlosers.toList()
    }
}

class RaireContestUnderAudit(
    contest: RaireContest,
    val winner: Int,  // the sum of winner and eliminated must be all the candiates in the contest
    val eliminated: List<Int>,
    val expectedPollsNumber : Int,
    val expectedPollsPercent : Double,
    val assertions: List<RaireAssertion>,
): ContestUnderAudit(contest, isComparison=true, hasStyle=true) { // TODO set ncvrs, Nc
    val candidates =  listOf(winner) + eliminated

    // TODO eliminate
    fun makeAssorters(): List<RaireAssorter> {
        return this.assertions.map {
            RaireAssorter(this, it)
        }
    }

    override fun makeComparisonAssertions(cvrs: Iterable<Cvr>, votes: Map<Int, Int>?): ContestUnderAudit {
        this.comparisonAssertions = assertions.map { assertion ->
            val assorter = RaireAssorter(this, assertion)
            val margin = assorter.calcAssorterMargin(id, cvrs)
            assorter.reportedMargin = margin
            val comparisonAssorter = ComparisonAssorter(contest, assorter, margin2mean(margin))
            // println(" assertion ${assertion} margin=${comparisonAssorter.margin} avg=${comparisonAssorter.avgCvrAssortValue}")
            ComparisonAssertion(contest, comparisonAssorter)
        }
        return this
    }

    fun show() = buildString {
        appendLine("  RaireContestUnderAudit ${contest.info.name} winner $winner eliminated $eliminated")
        assertions.forEach { append(it.show()) }
    }

    companion object {
        fun make(name: String,
                 winner: Int,  // the sum of winner and eliminated must be all the candiates
                 Nc: Int,
                 Np: Int,
                 eliminated: List<Int>,
                 expectedPollsNumber : Int,
                 expectedPollsPercent : Double,
                 assertions: List<RaireAssertion>): RaireContestUnderAudit {

            val candidates =  listOf(winner) + eliminated // the sum of winner and eliminated must be all the candiates
            val contest = RaireContest(
                ContestInfo(
                    name,
                    name.toInt(), // ??
                    candidates.associate{ it.toString() to it },
                    SocialChoiceFunction.IRV,
                ),
                listOf(winner.toString()),
                Nc = Nc,
                Np = Np,
            )
            return RaireContestUnderAudit(contest, winner, eliminated, expectedPollsNumber, expectedPollsPercent, assertions)
        }
    }
}

/*
Not Eliminated Next (NEN) Assertions. "IRV Elimination"
NEN assertions compare the tallies of two candidates under the assumption that a specific set of
candidates have been eliminated. An instance of this kind of assertion could look like this:
  NEN: Alice > Bob if only {Alice, Bob, Diego} remain.

Not Eliminated Before (NEB) Assertions. "Winner Only"
Alice NEB Bob is an assertion saying that Alice cannot be eliminated before Bob, irrespective of which
other candidates are continuing.
 */

/*
Assertion                   RaireScore  ShangrlaScore            Notes
NEB (winner_only)
  c1 NEB ck where k > 1           1       1                 Supports 1st prefs for c1
  cj NEB ck where k > j > 1       0       1/2               cj precedes ck but is not first
  cj NEB ck where k < j          -1       0                 a mention of ck preceding cj

NEN(irv_elimination): ci > ck if only {S} remain
  where ci = first(pS(b))           1     1                 counts for ci (expected)
  where first(pS(b)) !∈ {ci , ck }  0     1/2               counts for neither cj nor ck
  where ck = first(pS(b))          -1     0                 counts for ck (unexpected)
 */

/*
  NEB two vote overstatement: cvr has winner as first pref (1), mvr has loser preceeding winner (0)
  NEB one vote overstatement: cvr has winner as first pref (1), mvr has winner preceding loser, but not first (1/2)
  NEB two vote understatement: cvr has loser preceeding winner(0), mvr has winner as first pref (1)
  NEB one vote understatement: cvr has winner preceding loser, but not first (1/2), mvr has winner as first pref (1)

  NEN two vote overstatement: cvr has winner as first pref among remaining (1), mvr has loser as first pref among remaining (0)
  NEN one vote overstatement: cvr has winner as first pref among remaining (1), mvr has neither winner nor loser as first pref among remaining (1/2)
  NEN two vote understatement: cvr has loser as first pref among remaining (0), mvr has winner as first pref among remaining (1)
  NEN one vote understatement: cvr has neither winner nor loser as first pref among remaining (1/2), mvr has winner as first pref among remaining  (1)
 */
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
    val contestName = contest.contest.info.name
    val contestId = contest.id
    val remaining = contest.candidates.filter { !assertion.alreadyEliminated.contains(it) }
    var reportedMargin: Double = 0.0

    override fun upperBound() = 1.0
    /* override fun toString() = buildString {
        append("RaireAssorter contest ${contestName} type= ${assertion.assertionType} winner=${assertion.winner} loser=${assertion.loser}")
        if (assertion.assertionType == RaireAssertionType.irv_elimination) append(" alreadyElim=${assertion.alreadyEliminated}")
    } */
    override fun desc() = buildString {
        append("RaireAssorter winner/loser=${assertion.winner}/${assertion.loser}")
        if (assertion.assertionType == RaireAssertionType.irv_elimination) append(" alreadyElim=${assertion.alreadyEliminated}")
    }
    override fun winner() = assertion.winner
    override fun loser() = assertion.loser
    override fun reportedMargin() = reportedMargin

    // override fun reportedAssorterMargin(votes: Map<Int, Int>): Double = 0.0 // TODO

    override fun assort(mvr: Cvr, usePhantoms: Boolean): Double {
        if (usePhantoms && mvr.phantom) return 0.5;
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
