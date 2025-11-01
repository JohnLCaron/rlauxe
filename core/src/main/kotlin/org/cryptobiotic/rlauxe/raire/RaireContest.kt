package org.cryptobiotic.rlauxe.raire

import au.org.democracydevelopers.raire.assertions.AssertionAndDifficulty
import au.org.democracydevelopers.raire.assertions.NotEliminatedBefore
import au.org.democracydevelopers.raire.assertions.NotEliminatedNext
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.util.*

private val logger = KotlinLogging.logger("RaireContest")

// an IRV Contest that does not have votes (candidateId -> nvotes Map<Int, Int>)
// this is the motivation for ContestIF
data class RaireContest(
    val info: ContestInfo,
    val winners: List<Int>, // TODO only one
    val Nc: Int,
    val Ncast: Int,
    val undervotes: Int,
) : ContestIF {
    val winnerNames: List<String>
    val losers: List<Int>

    // debug / visibility (see rlauxe-viewer)
    // added by makeRaireContests() during construction of RaireContestUnderAudit
    // there may be multiple paths through the elimination tree when there are ties
    val roundsPaths = mutableListOf<IrvRoundsPath>()

    init {
        require(info.choiceFunction == SocialChoiceFunction.IRV) { "RaireContest must be an IRV contest" }
        val mapIdToName: Map<Int, String> = info.candidateNames.toList().associate { Pair(it.second, it.first) }
        winnerNames = winners.map { mapIdToName[it]!! }
        val mlosers = mutableListOf<Int>()
        info.candidateNames.forEach { (_, id) ->
            if (!winners.contains(id)) mlosers.add(id)
        }
        losers = mlosers.toList()
    }

    override fun Nc() = Nc
    override fun Np() = Nc - Ncast
    override fun Nundervotes() = undervotes
    override fun info() = info
    override fun winnerNames() = winnerNames
    override fun winners() = winners
    override fun losers() = losers

    override fun recountMargin(assorter: AssorterIF): Double {
        try {
            val rassorter = assorter as RaireAssorter
            val rassertion = rassorter.rassertion
            val pctDefault = rassertion.marginInVotes / Nc.toDouble()
            if (roundsPaths.isEmpty()) return pctDefault
            val rounds = roundsPaths.first().rounds // common case is only one
            if (rounds.isEmpty()) return pctDefault

            // find the latest round with both candidates
            var latestRound : IrvRound? = null
            rounds.forEach{ it:IrvRound -> if (it.count.contains(assorter.winner()) && it.count.contains(assorter.loser())) latestRound = it }
            if (latestRound == null) return pctDefault

            val winner = latestRound.count[assorter.winner()]!!
            val loser = latestRound.count[assorter.loser()]!!
            return (winner - loser) / (winner.toDouble())
        } catch (e : Throwable) {
            logger.warn(e) { "recountMargin for RaireContest ${id} assorter ${assorter.shortName()} failed" }
            return -1.0
        }
    }

    override fun showAssertionDifficulty(assorter: AssorterIF): String {
        val rassorter = assorter as RaireAssorter
        val rassertion = rassorter.rassertion
        val pctDefault = rassertion.marginInVotes / Nc.toDouble()
        val diffDdefault = "marginInVotes=${rassertion.marginInVotes} recountMargin=${pctDefault}"
        if (roundsPaths.isEmpty()) return diffDdefault
        val rounds = roundsPaths.first().rounds // common case is only one
        if (rounds.isEmpty()) return diffDdefault

        // find the latest round with both candidates
        var latestRound : IrvRound? = null
        rounds.forEach{ it:IrvRound -> if (it.count.contains(assorter.winner()) && it.count.contains(assorter.loser())) latestRound = it }
        if (latestRound == null) return diffDdefault

        val winner = latestRound.count[assorter.winner()]!!
        val loser = latestRound.count[assorter.loser()]!!
        val recountMargin = (winner - loser) / (winner.toDouble())
        return "winner=$winner loser=$loser diff=${winner-loser} (w-l)/w =${recountMargin} difficulty=${rassertion.difficulty}"
    }

    override fun show() = buildString {
        append("'$name' ($id) $choiceFunction voteForN=${info.voteForN} winners=${winners()} Nc=${Nc()} Np=${Np()} Ncast=$Ncast Nu=${Nundervotes()}")
    }

    override fun showCandidates() = buildString {
        append(showIrvCountResult(IrvCountResult(roundsPaths), info))
    }
}

class RaireContestUnderAudit(
    contest: RaireContest,
    val rassertions: List<RaireAssertion>,
    hasStyle: Boolean = true,  // TODO do we really support hasStyle == false?
): ContestUnderAudit(contest, isClca=true, hasStyle=hasStyle) {
    val candidates =  contest.info.candidateIds

    init {
        this.pollingAssertions = makeRairePollingAssertions()
        this.clcaAssertions = pollingAssertions.map { assertion ->
            val clcaAssorter = makeClcaAssorter(assertion)
            ClcaAssertion(contest.info(), clcaAssorter)
        }
    }

    fun makeRairePollingAssertions(): List<Assertion> {
        return rassertions.map { rassertion ->
            val reportedMean = margin2mean(rassertion.marginInVotes.toDouble() / contest.Nc())
            val assorter = RaireAssorter(contest.info(), rassertion).setReportedMean(reportedMean)
            Assertion(contest.info(), assorter)
        }
    }

    override fun showShort() = buildString {
        append("${name} ($id) Nc=$Nc winner ${contest.winners().first()} losers ${contest.losers()} minMargin=${df(minMargin())}") //  est=$estMvrs status=$status")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as RaireContestUnderAudit

        if (rassertions != other.rassertions) return false
        if (candidates != other.candidates) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + rassertions.hashCode()
        result = 31 * result + candidates.hashCode()
        return result
    }

    companion object {
         fun makeFromInfo(
                 info: ContestInfo,
                 winnerIndex: Int,
                 Nc: Int,
                 Ncast: Int,
                 undervotes: Int,
                 assertions: List<RaireAssertion>
         ): RaireContestUnderAudit {

            val winnerId = info.candidateIds[winnerIndex]
            val contest = RaireContest(
                info,
                listOf(winnerId),
                Nc = Nc,
                Ncast = Ncast,
                undervotes = undervotes,
            )
            return RaireContestUnderAudit(contest, assertions)
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
enum class RaireAssertionType(val aname: String) {
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

// wraps the info from au.org.democracydevelopers.raire.assertions.AssertionAndDifficulty
// converts a raire.java AssertionAndDifficulty
data class RaireAssertion(
    val winnerId: Int, // this must be the candidate ID, in order to match with Cvr.votes
    val loserId: Int,  // ditto
    var difficulty: Double,
    var marginInVotes: Int,
    val assertionType: RaireAssertionType,
    val eliminated: List<Int> = emptyList(), // candidate Ids; NEN only; already eliminated for the purpose of this assertion
    val votes: Map<Int, Int> = emptyMap(), // votes for winner, loser depending on assertion type
) {
    fun show() = buildString {
        appendLine("    assertion type '$assertionType' winner $winnerId loser $loserId eliminated=$eliminated difficulty=${dfn(difficulty,2)}, marginInVotes=$marginInVotes")
    }

    fun remaining(candidateIds: List<Int>) = candidateIds.filter { !eliminated.contains(it) }

    companion object {
        // Note aand and votes are in index space
        fun convertAssertion(candidateIds: List<Int>, aandd: AssertionAndDifficulty, votes: Map<Int, Int>): RaireAssertion {
            val aassertion = aandd.assertion
            return if (aassertion is NotEliminatedBefore) {
                val winner = candidateIds[aassertion.winner]
                val loser = candidateIds[aassertion.loser]
                RaireAssertion(winner, loser, aandd.difficulty, aandd.margin, RaireAssertionType.winner_only, votes = votes)
            } else if (aassertion is NotEliminatedNext) {
                val winner = candidateIds[aassertion.winner]
                val loser = candidateIds[aassertion.loser]
                // have to convert continuing (aka remaining) -> alreadyEliminated, and index -> id
                val continuing = aassertion.continuing.map{ candidateIds[it] }.toList()
                val eliminated = candidateIds.filter { !continuing.contains(it) }
                return RaireAssertion(
                    winner,
                    loser,
                    aandd.difficulty,
                    aandd.margin,
                    RaireAssertionType.irv_elimination,
                    eliminated,
                    votes = votes,
                )
            } else {
                throw Exception("Unknown assertion type: ${aassertion.javaClass.name}")
            }
        }
    }
}

//////////////////////////////////////////////////////////////////////////////////////////////////

// This is a "primitive" assorter.
data class RaireAssorter(val info: ContestInfo, val rassertion: RaireAssertion): AssorterIF {
    val contestId = info.id
    val remaining = info.candidateIds.filter { !rassertion.eliminated.contains(it) } // // TODO this is index ??
    var reportedMean: Double = 0.0

    fun setReportedMean(mean: Double): RaireAssorter {
        this.reportedMean = mean
        return this
    }

    override fun upperBound() = 1.0
    override fun winner() = rassertion.winnerId // candidate id, not index
    override fun loser() = rassertion.loserId   // candidate id, not index
    override fun reportedMargin() = mean2margin(reportedMean)
    override fun reportedMean() = reportedMean

    override fun desc() = buildString {
        append("winner/loser=${rassertion.winnerId}/${rassertion.loserId} margin=${rassertion.marginInVotes} difficulty=${rassertion.difficulty}")
        if (rassertion.assertionType == RaireAssertionType.irv_elimination) append(" eliminated=${rassertion.eliminated}")
        append(" votes=${rassertion.votes}")
    }
    override fun hashcodeDesc() = "${winLose()} ${info.hashCode()} ${rassertion.hashCode()}" // must be unique for serialization

    override fun assort(mvr: Cvr, usePhantoms: Boolean): Double {
        if (usePhantoms && mvr.phantom) return 0.5
        return if (rassertion.assertionType == RaireAssertionType.winner_only) assortWinnerOnly(mvr)
            else  if (rassertion.assertionType == RaireAssertionType.irv_elimination) assortIrvElimination(mvr)
            else throw RuntimeException("unknown assertionType = ${rassertion.assertionType}")
    }

    //                 # CVR is a vote for the winner only if it has the
    //                # winner as its first preference
    //                winner_func = lambda v, contest_id=contest.id, winr=winr: (
    //                    1 if v.get_vote_for(contest_id, winr) == 1 else 0
    //                )
    // aka NEB
    fun assortWinnerOnly(rcvr: Cvr): Double {
        // CVR is a vote for the winner only if it has the winner as its first preference (rank == 1)
        val awinner = if (raire_get_rank(rcvr, contestId, rassertion.winnerId) == 1) 1 else 0
        // CVR is a vote for the loser if they appear and the winner does not, or they appear before the winner
        val aloser = raire_loser_vote_wo( rcvr, contestId, rassertion.winnerId, rassertion.loserId)
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
        val awinner = raire_votefor_elim(rcvr, contestId, rassertion.winnerId, remaining)
        val aloser = raire_votefor_elim(rcvr, contestId, rassertion.loserId, remaining)
        return (awinner - aloser + 1) * 0.5 // affine transform from (-1, 1) -> (0, 1)
    }

    override fun toString() = desc()
}

/** Duplicating the math from SHANGRLA CVR in Audit.py */

//     def get_vote_for(self, contest_id: str, candidate: str):
//        return (
//            False
//            if (contest_id not in self.votes or candidate not in self.votes[contest_id])
//            else self.votes[contest_id][candidate]
//        )

// if candidate not ranked, return 0, else rank (1 based)
fun raire_get_rank(cvr: Cvr, contest: Int, candidate: Int): Int {
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

// Check whether vote is a vote for the loser with respect to a 'winner only' assertion.
// Its a vote for the loser if they appear and the winner does not, or they appear before the winner
// return 1 if the given vote is a vote for 'loser' and 0 otherwise
fun raire_loser_vote_wo(cvr: Cvr, contest: Int, winner: Int, loser: Int): Int {
    val rank_winner = raire_get_rank(cvr, contest, winner)
    val rank_loser = raire_get_rank(cvr, contest, loser)

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
 * Check whether 'vote' is a vote for the given candidate in the context where only candidates in 'remaining' remain standing.
 * If you reduce the ballot down to only those candidates in 'remaining', and 'cand' is the first preference, return 1; otherwise return 0.
 * @param cand identifier for candidate
 * @param remaining list of identifiers of candidates still standing
 * @return 1 if the given vote for the contest counts as a vote for 'cand' and 0 otherwise.
 */
fun raire_votefor_elim(cvr: Cvr, contest: Int, cand: Int, remaining: List<Int>): Int {
    if (cand !in remaining) return 0

    val rank_cand = raire_get_rank(cvr, contest, cand)
    if (rank_cand == 0) return 0

    for (altc in remaining) {
        if (altc == cand) continue
        val rank_altc = raire_get_rank(cvr, contest, altc)
        if (rank_altc != 0 && rank_altc <= rank_cand) return 0
    }
    return 1
}
