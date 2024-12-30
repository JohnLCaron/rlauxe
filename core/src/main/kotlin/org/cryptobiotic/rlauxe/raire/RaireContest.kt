package org.cryptobiotic.rlauxe.raire

import au.org.democracydevelopers.raire.assertions.AssertionAndDifficulty
import au.org.democracydevelopers.raire.assertions.NotEliminatedBefore
import au.org.democracydevelopers.raire.assertions.NotEliminatedNext
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.margin2mean

data class RaireContest(
    override val info: ContestInfo,
    override val winners: List<Int>,
    override val Nc: Int,
    override val Np: Int,
) : ContestIF {
    override val winnerNames: List<String>
    override val losers: List<Int>
    override val ncandidates = info.candidateIds.size
    override val id = info.id
    override val choiceFunction = info.choiceFunction

    init {
        val mapIdToName: Map<Int, String> = info.candidateNames.toList().associate { Pair(it.second, it.first) }
        winnerNames = winners.map { mapIdToName[it]!! }
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
    val assertions: List<RaireAssertion>,
): ContestUnderAudit(contest, isComparison=true, hasStyle=true) {
    val candidates =  contest.info.candidateIds

    // TODO eliminate
    fun makeAssorters(): List<RaireAssorter> {
        return this.assertions.map {
            RaireAssorter(contest.info, it)
        }
    }

    // override fun makeComparisonAssertions(cvrs: Iterable<Cvr>, votes: Map<Int, Int>?): ContestUnderAudit {
    override fun makeComparisonAssertions(cvrs: Iterable<Cvr>): ContestUnderAudit {
        require(isComparison) { "makeComparisonAssertions() can be called only on comparison contest"}
        this.comparisonAssertions = assertions.map { assertion ->
            val assorter = RaireAssorter(contest.info, assertion)
            val calcMargin = assorter.calcAssorterMargin(id, cvrs)
            if (assertion.margin != 0) {
                val reportedMargin = assertion.margin / this.Nc.toDouble()
                println(" calcMargin=$calcMargin reportedMargin=$reportedMargin")
                require(doubleIsClose(calcMargin, reportedMargin))
            }
            assorter.reportedMargin = calcMargin
            val comparisonAssorter = ComparisonAssorter(contest, assorter, margin2mean(calcMargin), hasStyle=hasStyle)
            ComparisonAssertion(contest, comparisonAssorter)
        }
        return this
    }

    fun show() = buildString {
        appendLine("  RaireContestUnderAudit ${contest.info.name} winner $winner losers ${contest.losers}")
        assertions.forEach { append(it.show()) }
    }

    companion object {
        fun make(name: String,
                 winner: Int,  // the sum of winner and eliminated must be all the candiates
                 Nc: Int,
                 Np: Int,
                 eliminated: List<Int>,
                 assertions: List<RaireAssertion>): RaireContestUnderAudit {

            val candidates =  listOf(winner) + eliminated // the sum of winner and eliminated must be all the candiates
            val contest = RaireContest(
                ContestInfo(
                    name,
                    name.toInt(), // ??
                    candidates.associate{ it.toString() to it },
                    SocialChoiceFunction.IRV,
                ),
                listOf(winner),
                Nc = Nc,
                Np = Np,
            )
            return RaireContestUnderAudit(contest, winner, assertions)
        }

        fun makeFromInfo(
                 info: ContestInfo,
                 winner: Int,
                 Nc: Int,
                 Np: Int,
                 assertions: List<RaireAssertion>): RaireContestUnderAudit {

            val contest = RaireContest(
                info,
                listOf(winner),
                Nc = Nc,
                Np = Np,
            )
            return RaireContestUnderAudit(contest, winner, assertions)
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
    val margin: Int,
    val assertionType: RaireAssertionType,
    val alreadyEliminated: List<Int> = emptyList(), // NEN only; already eliminated for the purpose of this assertion
    val explanation: String? = null,
) {
    fun show() = buildString {
        appendLine("    assertion type '$assertionType' winner $winner loser $loser alreadyEliminated $alreadyEliminated explanation: '$explanation'")
    }

    companion object {
        fun convertAssertion(candidates: List<Int>, aandd: AssertionAndDifficulty): RaireAssertion {
            val assertion = aandd.assertion
            return if (assertion is NotEliminatedBefore) {
                RaireAssertion(assertion.winner, assertion.loser, aandd.margin, RaireAssertionType.winner_only)
            } else if (assertion is NotEliminatedNext) {
                // have to convert continuing (aka remaining) -> alreadyEliminated
                val continuing = assertion.continuing.toList()
                val alreadyEliminated = candidates.filter { !continuing.contains(it) }
                RaireAssertion(
                    assertion.winner,
                    assertion.loser,
                    aandd.margin,
                    RaireAssertionType.irv_elimination,
                    alreadyEliminated,
                )
            } else {
                throw Exception("Unknown assertion type: ${assertion.javaClass.name}")
            }
        }
    }
}

//////////////////////////////////////////////////////////////////////////////////////////////////

// This a primitive assorter.
class RaireAssorter(info: ContestInfo, val assertion: RaireAssertion): AssorterFunction {
    val contestId = info.id
    val remaining = info.candidateIds.filter { !assertion.alreadyEliminated.contains(it) }
    var reportedMargin: Double = 0.0

    override fun upperBound() = 1.0 // TODO
    override fun winner() = assertion.winner
    override fun loser() = assertion.loser
    override fun reportedMargin() = reportedMargin
    override fun desc() = buildString {
        append("RaireAssorter winner/loser=${assertion.winner}/${assertion.loser}")
        if (assertion.assertionType == RaireAssertionType.irv_elimination) append(" alreadyElim=${assertion.alreadyEliminated}")
    }

    override fun assort(mvr: Cvr, usePhantoms: Boolean): Double {
        if (usePhantoms && mvr.phantom) return 0.5;
        val rcvr = RaireCvr(mvr)
        return if (assertion.assertionType == RaireAssertionType.winner_only) assortWinnerOnly(rcvr)
        else  if (assertion.assertionType == RaireAssertionType.irv_elimination) assortIrvElimination(rcvr)
        else throw RuntimeException("unknown assertionType = $(this.assertionType")
    }

    //                 # CVR is a vote for the winner only if it has the
    //                # winner as its first preference
    //                winner_func = lambda v, contest_id=contest.id, winr=winr: (
    //                    1 if v.get_vote_for(contest_id, winr) == 1 else 0
    //                )
    // aka NEB
    fun assortWinnerOnly(rcvr: RaireCvr): Double {
        // CVR is a vote for the winner only if it has the winner as its first preference (rank == 1)
        val awinner = if (rcvr.get_vote_for(contestId, assertion.winner) == 1) 1 else 0
        // CVR is a vote for the loser if they appear and the winner does not, or they appear before the winner
        val aloser = rcvr.rcv_lfunc_wo( contestId, assertion.winner, assertion.loser)
        return (awinner - aloser + 1) * 0.5 // affine transform from (-1, 1) -> (0, 1)
    }

    //                        assort=lambda v, contest_id=contest.id, winner=winr, loser=losr, remn=remn:
    //                            ( v.rcv_votefor_cand(contest.id, winner, remn)
    //                            - v.rcv_votefor_cand(contest.id, loser, remn)
    //                            + 1 ) / 2
    // aka NEN
    fun assortIrvElimination(rcvr: RaireCvr): Double {
        // Context is that all candidates in "already_eliminated" have been
        // eliminated and their votes distributed to later preferences
        val awinner = rcvr.rcv_votefor_cand(contestId, assertion.winner, remaining)
        val aloser = rcvr.rcv_votefor_cand(contestId, assertion.loser, remaining)
        return (awinner - aloser + 1) * 0.5 // affine transform from (-1, 1) -> (0, 1)
    }
}
