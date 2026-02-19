package org.cryptobiotic.rlauxe.raire

import au.org.democracydevelopers.raire.assertions.AssertionAndDifficulty
import au.org.democracydevelopers.raire.assertions.NotEliminatedBefore
import au.org.democracydevelopers.raire.assertions.NotEliminatedNext
import au.org.democracydevelopers.raire.irv.Votes
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.collections.toIntArray

private val logger = KotlinLogging.logger("RaireContest")

// an IRV Contest that does not have votes (candidateId -> nvotes Map<Int, Int>)
// this is the motivation for ContestIF
data class RaireContest(
    val info: ContestInfo,
    val winners: List<Int>, // actually only one winner is allowed
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
    override fun Nphantoms() = Nc - Ncast
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
        if (latestRound == null) latestRound = rounds.last()

        val winner = latestRound.count[assorter.winner()]!!
        val loser = latestRound.count[assorter.loser()] ?: 0
        val recountMargin = (winner - loser) / (winner.toDouble())
        return "winner=$winner loser=$loser diff=${winner-loser} (w-l)/w =${recountMargin} difficulty=${rassertion.difficulty}"
    }

    override fun show() = buildString {
        append("'$name' ($id) $choiceFunction voteForN=${info.voteForN} winners=${winners()} Nc=${Nc()} Nphantoms=${Nphantoms()} Ncast=$Ncast Nu=${Nundervotes()}")
    }

    override fun showCandidates() = buildString {
        append(showIrvCountResult(IrvCountResult(roundsPaths), info))
    }
}

class RaireContestWithAssertions(
    contest: RaireContest,
    val rassertions: List<RaireAssertion>,
    NpopIn: Int,
): ContestWithAssertions(contest, isClca=true, NpopIn) {
    val candidates =  contest.info.candidateIds

    init {
        this.assertions = makeRairePollingAssertions()
        this.clcaAssertions = assertions.map { assertion ->
            val clcaAssorter = makeClcaAssorter(assertion)
            ClcaAssertion(contest.info(), clcaAssorter)
        }
    }

    fun makeRairePollingAssertions(): List<Assertion> {
        return rassertions.map { rassertion ->
            val dilutedMean = margin2mean(rassertion.marginInVotes.toDouble() / Npop)
            val assorter = RaireAssorter(contest.info(), rassertion).setDilutedMean(dilutedMean)
            Assertion(contest.info(), assorter)
        }
    }

    override fun showShort() = buildString {
        append("${name} ($id) Nc=$Nc Npop=$Npop winner ${contest.winners().first()} losers ${contest.losers()} minMargin=${df(minDilutedMargin())}")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as RaireContestWithAssertions

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
                 assertions: List<RaireAssertion>,
                 Npop: Int,
         ): RaireContestWithAssertions {

            val winnerId = info.candidateIds[winnerIndex]
            val contest = RaireContest(
                info,
                listOf(winnerId),
                Nc = Nc,
                Ncast = Ncast,
                undervotes = undervotes,
            )
            return RaireContestWithAssertions(contest, assertions, Npop)
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
enum class RaireAssertionType(val shortName: String) {
    winner_only("NEB"), // winner cannot be eliminated before loser
    irv_elimination("NEN"); // winner > loser if only {remaining} remain

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
    val winnerIdx: Int,
    val loserIdx: Int,
    val eliminated: List<Int> = emptyList(), // candidate Ids; NEN only; already eliminated for the purpose of this assertion
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
                RaireAssertion(
                    winner,
                    loser,
                    aandd.difficulty,
                    aandd.margin,
                    RaireAssertionType.winner_only,
                    aassertion.winner,
                    aassertion.loser,
                    emptyList(),
                    // votes,
                )
            } else if (aassertion is NotEliminatedNext) {
                val winner = candidateIds[aassertion.winner]
                val loser = candidateIds[aassertion.loser]
                // have to convert continuing (aka remaining) -> alreadyEliminated, and index -> id
                val continuing = aassertion.continuing.map{ candidateIds[it] }.toList()
                val eliminated = candidateIds.filter { !continuing.contains(it) }
                RaireAssertion(
                    winner,
                    loser,
                    aandd.difficulty,
                    aandd.margin,
                    RaireAssertionType.irv_elimination,
                    aassertion.winner,
                    aassertion.loser,
                    eliminated,
                    // votes,
                )
            } else {
                throw Exception("Unknown assertion type: ${aassertion.javaClass.name}")
            }
        }
    }
}

//////////////////////////////////////////////////////////////////////////////////////////////////

data class RaireAssorter(val info: ContestInfo, val rassertion: RaireAssertion): AssorterIF {
    val contestId = info.id
    val remaining = info.candidateIds.filter { !rassertion.eliminated.contains(it) } // candidate Ids
    val remainingIdx: IntArray = remaining.map { info.candidateIdToIdx[it]!! }.toIntArray() // candidate Indices
    val isNEB = rassertion.assertionType == RaireAssertionType.winner_only

    var dilutedMean: Double = 0.0

    fun setDilutedMean(mean: Double): RaireAssorter {
        this.dilutedMean = mean
        return this
    }

    fun calcMarginFromVotes(votes: Votes, N: Int): Double {
        return calcVoteMargin(votes) / N.toDouble()
    }

    // uses raire-java to find the marginInVotes from an arbitrary set of Votes. used to get assorter pool averages
    // Note this may be negetive when loser had more votes than winner in this pool
    fun calcVoteMargin(votes: Votes): Int {
        val winnerLoser = winnerLoserVotes(votes)
        return winnerLoser.first - winnerLoser.second
    }

    // uses raire-java to find the assertion winner and loser's vote count
    fun winnerLoserVotes(votes: Votes): Pair<Int, Int> {
        val winnerLoser = if (isNEB) { // raire-java NotEliminatedBefore lines 67-71
            val tally2 = votes.restrictedTallies(intArrayOf(rassertion.winnerIdx, rassertion.loserIdx))
            val tallyWinner = votes.firstPreferenceOnlyTally(rassertion.winnerIdx)
            val tallyLoser = tally2[1]
            Pair(tallyWinner, tallyLoser)

        } else { // raire-java NotEliminatedNext lines 83-95
            val tallyAll = votes.restrictedTallies(remainingIdx)
            val tallyMap = remainingIdx.mapIndexed { idx, it -> Pair(it, idx) }.toMap() // candidate idx -> remaining index

            val tallyWinner = tallyAll[tallyMap[rassertion.winnerIdx]!!]
            val tallyLoser = tallyAll[tallyMap[rassertion.loserIdx]!!]
            Pair(tallyWinner, tallyLoser)
        }
        return winnerLoser
    }

    override fun upperBound() = 1.0
    override fun winner() = rassertion.winnerId // candidate id, not index
    override fun loser() = rassertion.loserId   // candidate id, not index
    override fun dilutedMargin() = mean2margin(dilutedMean)
    override fun dilutedMean() = dilutedMean
    override fun shortName() = "${rassertion.assertionType.shortName} ${winner()}/${loser()}"

    override fun desc() = buildString {
        append("Raire ${rassertion.assertionType.shortName} winner/loser=${rassertion.winnerId}/${rassertion.loserId} marginInVotes=${rassertion.marginInVotes} difficulty=${rassertion.difficulty}")
        if (rassertion.assertionType == RaireAssertionType.irv_elimination) append(" eliminated=${rassertion.eliminated}")
        // append(" votes=${rassertion.votes}")
    }
    override fun hashcodeDesc() = "${rassertion.assertionType.shortName} ${winLose()} ${rassertion.eliminated}" // must be unique for serialization

    override fun calcMarginFromRegVotes(useVotes: Map<Int, Int>?, N: Int): Double {
        throw RuntimeException("RaireAssorter can't calculate margin from Regular Votes; use calcMarginFromVotes")
    }

    override fun assort(cvr: CvrIF, usePhantoms: Boolean): Double {
        if (!cvr.hasContest(info.id)) return 0.5
        if (usePhantoms && cvr.isPhantom()) return 0.5
        return if (isNEB) assortNotEliminatedBefore(cvr)
               else assortNotEliminatedNext(cvr)
    }

    // aka NEB
    fun assortNotEliminatedBefore(rcvr: CvrIF): Double {
        // CVR is a vote for the winner only if it has the winner as its first preference (rank == 1)
        val awinner = if (raire_get_rank(rcvr, contestId, rassertion.winnerId) == 1) 1 else 0
        // CVR is a vote for the loser if they appear and the winner does not, or they appear before the winner
        val aloser = raire_loser_vote_wo( rcvr, contestId, rassertion.winnerId, rassertion.loserId)
        return (awinner - aloser + 1) * 0.5 // affine transform from (-1, 1) -> (0, 1)
    }

    // aka NEN
    fun assortNotEliminatedNext(rcvr: CvrIF): Double {
        // Context is that all candidates in "already_eliminated" have been
        // eliminated and their votes distributed to later preferences
        val awinner = raire_votefor_elim(rcvr, contestId, rassertion.winnerId, remaining)
        val aloser = raire_votefor_elim(rcvr, contestId, rassertion.loserId, remaining)
        return (awinner - aloser + 1) * 0.5 // affine transform from (-1, 1) -> (0, 1)
    }

    override fun toString() = desc()
}

// if candidate not ranked, return 0, else rank (1 based)
fun raire_get_rank(cvr: CvrIF, contest: Int, candidate: Int): Int {
    val rankedChoices = cvr.votes(contest)
    return if (rankedChoices == null || !rankedChoices.contains(candidate)) 0
    else rankedChoices.indexOf(candidate) + 1
}

// Check whether vote is a vote for the loser with respect to a 'winner only' assertion.
// Its a vote for the loser if they appear and the winner does not, or they appear before the winner
// return 1 if the given vote is a vote for 'loser' and 0 otherwise
fun raire_loser_vote_wo(cvr: CvrIF, contest: Int, winner: Int, loser: Int): Int {
    val rank_winner = raire_get_rank(cvr, contest, winner)
    val rank_loser = raire_get_rank(cvr, contest, loser)

    return when {
        rank_winner == 0 && rank_loser != 0 -> 1
        rank_winner != 0 && rank_loser != 0 && rank_loser < rank_winner -> 1
        else -> 0
    }
}

/**
 * Check whether 'vote' is a vote for the given candidate in the context where only candidates in 'remaining' remain standing.
 * If you reduce the ballot down to only those candidates in 'remaining', and 'cand' is the first preference, return 1; otherwise return 0.
 * @param cand identifier for candidate
 * @param remaining list of identifiers of candidates still standing
 * @return 1 if the given vote for the contest counts as a vote for 'cand' and 0 otherwise.
 */
fun raire_votefor_elim(cvr: CvrIF, contest: Int, cand: Int, remaining: List<Int>): Int {
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
