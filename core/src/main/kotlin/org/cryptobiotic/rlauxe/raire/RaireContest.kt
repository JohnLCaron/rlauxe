package org.cryptobiotic.rlauxe.raire

import au.org.democracydevelopers.raire.assertions.AssertionAndDifficulty
import au.org.democracydevelopers.raire.assertions.NotEliminatedBefore
import au.org.democracydevelopers.raire.assertions.NotEliminatedNext
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.margin2mean

// a Contest that does not have votes: Map<Int, Int>,   // candidateId -> nvotes
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
    override val undervotes: Int = -1  // TODO get this; nballots not voted on?

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
    val winner: Int,
    val rassertions: List<RaireAssertion>,
    isComparison: Boolean = true,
    hasStyle: Boolean = true
): ContestUnderAudit(contest, isComparison=isComparison, hasStyle=hasStyle) {
    val candidates =  contest.info.candidateIds

    // TODO eliminate who calls this?
    fun makeAssorters(): List<RaireAssorter> {
        return this.rassertions.map {
            RaireAssorter(contest.info, it, (it.marginInVotes.toDouble() / contest.Nc))
        }
    }

    // override fun makeComparisonAssertions(cvrs: Iterable<Cvr>, votes: Map<Int, Int>?): ContestUnderAudit {
    override fun makeClcaAssertions(): ContestUnderAudit {
        require(isComparison) { "makeComparisonAssertions() can be called only on comparison contest"}
        this.clcaAssertions = rassertions.map { rassertion ->
            val assorter = RaireAssorter(contest.info, rassertion, (rassertion.marginInVotes.toDouble() / contest.Nc))
            // val calcMargin = assorter.calcAssorterMargin(id, cvrs)
            val margin = assorter.reportedMargin()
            val clcaAssorter = ClcaAssorter(contest.info, assorter, margin2mean(margin), hasStyle=hasStyle)
            ClcaAssertion(contest.info, clcaAssorter)
        }
        return this
    }

    override fun showShort() = buildString {
        appendLine("${name} ($id) Nc=$Nc winner$winner losers ${contest.losers} minMargin=${df(minMargin())}") //  est=$estMvrs status=$status")
        /* assertions().filter { roundIdx == null || it.round == roundIdx} .forEach {
            append(" ${it.show()}")
        } */
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as RaireContestUnderAudit

        if (winner != other.winner) return false
        if (rassertions != other.rassertions) return false
        if (candidates != other.candidates) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + winner
        result = 31 * result + rassertions.hashCode()
        result = 31 * result + candidates.hashCode()
        return result
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
    val winnerId: Int,
    val loserId: Int,
    var marginInVotes: Int,
    val assertionType: RaireAssertionType,
    val eliminated: List<Int> = emptyList(), // NEN only; already eliminated for the purpose of this assertion
    val votes: Map<Int, Int> = emptyMap(), // votes for winner, loser depending on assertion type
    val explanation: String? = null,
) {
    fun show() = buildString {
        appendLine("    assertion type '$assertionType' winner $winnerId loser $loserId eliminated=$eliminated explanation: '$explanation'")
    }

    fun remaining(candidateIds: List<Int>) = candidateIds.filter { !eliminated.contains(it) }

    companion object {
        fun convertAssertion(candidates: List<Int>, aandd: AssertionAndDifficulty, votes: Map<Int, Int>): RaireAssertion {
            val assertion = aandd.assertion
            return if (assertion is NotEliminatedBefore) {
                RaireAssertion(assertion.winner, assertion.loser, aandd.margin, RaireAssertionType.winner_only, votes = votes)
            } else if (assertion is NotEliminatedNext) {
                // have to convert continuing (aka remaining) -> alreadyEliminated
                val continuing = assertion.continuing.toList()
                val eliminated = candidates.filter { !continuing.contains(it) }
                return RaireAssertion(
                    assertion.winner,
                    assertion.loser,
                    aandd.margin,
                    RaireAssertionType.irv_elimination,
                    eliminated,
                    votes = votes,
                )
            } else {
                throw Exception("Unknown assertion type: ${assertion.javaClass.name}")
            }
        }
    }
}

//////////////////////////////////////////////////////////////////////////////////////////////////

// This a primitive assorter.
data class RaireAssorter(val info: ContestInfo, val rassertion: RaireAssertion, val reportedMargin: Double): AssorterIF {
    val contestId = info.id
    val remaining = info.candidateIds.filter { !rassertion.eliminated.contains(it) }

    override fun upperBound() = 1.0
    override fun winner() = rassertion.winnerId
    override fun loser() = rassertion.loserId
    override fun reportedMargin() = reportedMargin
    override fun desc() = buildString {
        append("winner/loser=${rassertion.winnerId}/${rassertion.loserId} margin=${rassertion.marginInVotes}")
        if (rassertion.assertionType == RaireAssertionType.irv_elimination) append(" eliminated=${rassertion.eliminated}")
        append(" votes=${rassertion.votes}")
    }

    override fun assort(rcvr: Cvr, usePhantoms: Boolean): Double {
        if (usePhantoms && rcvr.phantom) return 0.5
        return if (rassertion.assertionType == RaireAssertionType.winner_only) assortWinnerOnly(rcvr)
        else  if (rassertion.assertionType == RaireAssertionType.irv_elimination) assortIrvElimination(rcvr)
        else throw RuntimeException("unknown assertionType = $(this.assertionType")
    }

    //                 # CVR is a vote for the winner only if it has the
    //                # winner as its first preference
    //                winner_func = lambda v, contest_id=contest.id, winr=winr: (
    //                    1 if v.get_vote_for(contest_id, winr) == 1 else 0
    //                )
    // aka NEB
    fun assortWinnerOnly(rcvr: Cvr): Double {
        // CVR is a vote for the winner only if it has the winner as its first preference (rank == 1)
        val awinner = if (raire_get_vote_for(rcvr, contestId, rassertion.winnerId) == 1) 1 else 0
        // CVR is a vote for the loser if they appear and the winner does not, or they appear before the winner
        val aloser = raire_rcv_lfunc_wo( rcvr, contestId, rassertion.winnerId, rassertion.loserId)
        return (awinner - aloser + 1) * 0.5 // affine transform from (-1, 1) -> (0, 1)
    }

    //                        assort=lambda v, contest_id=contest.id, winner=winr, loser=losr, remn=remn:
    //                            ( v.rcv_votefor_cand(contest.id, winner, remn)
    //                            - v.rcv_votefor_cand(contest.id, loser, remn)
    //                            + 1 ) / 2
    // aka NEN
    fun assortIrvElimination(rcvr: Cvr): Double {
        // Context is that all candidates in "already_eliminated" have been
        // eliminated and their votes distributed to later preferences
        val awinner = raire_rcv_votefor_cand(rcvr, contestId, rassertion.winnerId, remaining)
        val aloser = raire_rcv_votefor_cand(rcvr, contestId, rassertion.loserId, remaining)
        return (awinner - aloser + 1) * 0.5 // affine transform from (-1, 1) -> (0, 1)
    }
}

/** Duplicating the math from SHANGRLA CVR in Audit.py */

//     def get_vote_for(self, contest_id: str, candidate: str):
//        return (
//            False
//            if (contest_id not in self.votes or candidate not in self.votes[contest_id])
//            else self.votes[contest_id][candidate]
//        )

/** if candidate not ranked, 0, else rank (1 based) */
fun raire_get_vote_for(cvr: Cvr, contest: Int, candidate: Int): Int {
    val rankedChoices = cvr.votes[contest]
    return if (rankedChoices == null || !rankedChoices.contains(candidate)) 0
    else rankedChoices.indexOf(candidate) + 1
}

//        rank_winner = self.get_vote_for(contest_id, winner)
//        rank_loser = self.get_vote_for(contest_id, loser)
//
//        if not bool(rank_winner) and bool(rank_loser):
//            return 1
//        elif bool(rank_winner) and bool(rank_loser) and rank_loser < rank_winner:
//            return 1
//        else:
//            return 0
/**
 * Check whether vote is a vote for the loser with respect to a 'winner only' assertion.
 * Its a vote for the loser if they appear and the winner does not, or they appear before the winner
 *
 * @param winner identifier for winning candidate
 * @param loser identifier for losing candidate
 * @return 1 if the given vote is a vote for 'loser' and 0 otherwise
 */
fun raire_rcv_lfunc_wo(cvr: Cvr, contest: Int, winner: Int, loser: Int): Int {
    val rank_winner = raire_get_vote_for(cvr, contest, winner)
    val rank_loser = raire_get_vote_for(cvr, contest, loser)

    return when {
        rank_winner == 0 && rank_loser != 0 -> 1
        rank_winner != 0 && rank_loser != 0 && rank_loser < rank_winner -> 1
        else -> 0
    }
}

//         if not cand in remaining:
//            return 0
//
//        if not bool(rank_cand := self.get_vote_for(contest_id, cand)):
//            return 0
//        else:
//            for altc in remaining:
//                if altc == cand:
//                    continue
//                rank_altc = self.get_vote_for(contest_id, altc)
//                if bool(rank_altc) and rank_altc <= rank_cand:
//                    return 0
//            return 1
/**
 * Check whether 'vote' is a vote for the given candidate in the context
 * where only candidates in 'remaining' remain standing.
 *
 * @param cand identifier for candidate
 * @param remaining list of identifiers of candidates still standing
 * @return 1 if the given vote for the contest counts as a vote for 'cand' and 0 otherwise.
 * Essentially, if you reduce the ballot down to only those candidates in 'remaining',
 * and 'cand' is the first preference, return 1; otherwise return 0.
 */
fun raire_rcv_votefor_cand(cvr: Cvr, contest: Int, cand: Int, remaining: List<Int>): Int {
    if (cand !in remaining) {
        return 0
    }

    val rank_cand = raire_get_vote_for(cvr, contest, cand)
    if (rank_cand == 0) return 0

    for (altc in remaining) {
        if (altc == cand) continue

        val rank_altc = raire_get_vote_for(cvr, contest, altc)
        if (rank_altc != 0 && rank_altc <= rank_cand) {
            return 0
        }
    }
    return 1
}
