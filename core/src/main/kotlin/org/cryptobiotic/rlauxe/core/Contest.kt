package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.oneaudit.OneAuditContest
import org.cryptobiotic.rlauxe.raire.RaireContest
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.df
import kotlin.math.min

enum class SocialChoiceFunction { PLURALITY, APPROVAL, SUPERMAJORITY, IRV }

/** pre-election information **/
data class ContestInfo(
    val name: String,
    val id: Int,
    val candidateNames: Map<String, Int>, // candidate name -> candidate id
    val choiceFunction: SocialChoiceFunction,  // electionguard has "VoteVariationType"
    val nwinners: Int = 1,          // aka "numberElected"
    val voteForN: Int = nwinners,   // aka "contestSelectionLimit" or "optionSelectionLimit"
    val minFraction: Double? = null, // supermajority only.
) {
    val candidateIds: List<Int>

    init {
        if (choiceFunction == SocialChoiceFunction.SUPERMAJORITY) {
            require((nwinners == 1 && voteForN == 1)) { "SUPERMAJORITY must have nwinners == 1, and voteForN == 1" }
        }
        require(choiceFunction != SocialChoiceFunction.SUPERMAJORITY || minFraction != null) { "SUPERMAJORITY requires minFraction"}
        require(choiceFunction == SocialChoiceFunction.SUPERMAJORITY || minFraction == null) { "only SUPERMAJORITY can have minFraction"}
        require(minFraction == null || minFraction in (0.0..1.0)) { "minFraction between 0 and 1"}
        require(nwinners in (1..candidateNames.size)) { "nwinners between 1 and candidateNames.size"}
        require(voteForN in (1..candidateNames.size)) { "voteForN between 1 and candidateNames.size"}

        val candidateSet = candidateNames.toList().map { it.first }.toSet()
        require(candidateSet.size == candidateNames.size) { "duplicate candidate name $candidateNames"} // may not be possible
        candidateSet.forEach { candidate ->
            candidateSet.filter{ it != candidate }.forEach {
                require(candidate.isNotEmpty() ) { "empty candidate name: $candidateNames"}
                require(!candidate.equals(it, ignoreCase = true) ) { "candidate names differ only by case: $candidateNames"}
            }
        }

        candidateIds = candidateNames.toList().map { it.second }
        val candidateIdSet = candidateIds.toSet()
        require(candidateIdSet.size == candidateIds.size) { "duplicate candidate id $candidateIds"}
    }

    override fun toString() = buildString {
        append("'$name' ($id) candidates=${candidateIds} choiceFunction=$choiceFunction nwinners=$nwinners voteForN=${voteForN}")
    }
}

// Needed to allow RaireContest, which does not have votes: Map<Int, Int>
interface ContestIF {
    val id get() = info().id
    val name get() = info().name
    val ncandidates get() = info().candidateNames.size
    val choiceFunction get() = info().choiceFunction

    val Nc get() = Nc()

    fun Nc(): Int
    fun Np(): Int
    fun info(): ContestInfo
    fun winnerNames(): List<String>
    fun winners(): List<Int>
    fun losers(): List<Int>

    fun phantomRate() = Np() / Nc().toDouble()
    fun isIRV() = choiceFunction == SocialChoiceFunction.IRV
    fun show() : String = toString()
}

//    When we have styles and a complete CardLocationManifest, we can calculate Nb_c = physical ballots containing contest C.
//    Then Nc = Np + Nb_c

// nvotes = sum(votes)
// undervotes = Nc * nwinners - nvotes - Np

// Nu =
//    Let V_c = reported votes for contest C; V_c <= Nb_c <= N_c.

//    Let N_c = upper bound on ballots for contest C.
//    Let V_c = reported votes for contest C
//    Let U_c = undervotes for contest C; U_c = Nb_c - V_c >= 0.
//    Let Np_c = nphantoms for contest C; Np_c = N_c - Nb_c.
//    Then N_c = V_c + U_c + Np_c.

/**
 * Contest with the reported results.
 * Immutable.
 * @parameter voteInput: candidateId -> reported number of votes. keys must be in info.candidateIds, though zeros may be omitted.
 * @parameter Nc: maximum ballots/cards that contain this contest, independently verified (not from cvrs).
 * @parameter Np: number of phantoms for this contest.
 */
open class Contest(
        val info: ContestInfo,
        voteInput: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
        val iNc: Int,               // number of voters who cast ballots containing this Contest
        val Np: Int,                // number of phantoms
    ): ContestIF {

    override fun Nc() = iNc
    override fun Np() = Np
    override fun info() = info
    override fun winnerNames() = winnerNames
    override fun winners() = winners
    override fun losers() = losers

    val winnerNames: List<String>
    val winners: List<Int>
    val losers: List<Int>

    val votes: Map<Int, Int>  // candidateId -> nvotes; zero vote candidates have been added
    val undervotes: Int

    init {
        // construct votes, adding 0 votes if needed
        voteInput.forEach {
            require(info().candidateIds.contains(it.key)) {
                "'${it.key}' not found in contestInfo candidateIds ${info.candidateIds}"
            }
        }
        val voteBuilder = mutableMapOf<Int, Int>()
        voteBuilder.putAll(voteInput)
        info.candidateIds.forEach {
            if (!voteInput.contains(it)) {
                voteBuilder[it] = 0
            }
        }
        votes = voteBuilder.toList().sortedBy{ it.second }.reversed().toMap() // sort by votes recieved
        votes.forEach { (candId, candVotes) ->
            require(candVotes <= (iNc - Np)) {
                "contest $id candidate= $candId votes = $candVotes must be <= (Nc - Np)=${Nc - Np}"
            }
        }
        val nvotes = votes.values.sum()
       /* if (info.choiceFunction != SocialChoiceFunction.IRV) {
            require(nvotes <= info.voteForN * (iNc - Np)) {
                "contest $id nvotes= $nvotes must be <= nwinners=${info.voteForN} * (Nc=$Nc - Np=$Np) = ${info.voteForN * (Nc - Np)}"
            }
        } */
        undervotes = info.voteForN * (iNc - Np) - nvotes   // C1
        // (undervotes + nvotes) = voteForN * (Nc - Np)
        // Np + (undervotes + nvotes) / voteForN = Nc     // C2
        // But if you calculate Nc from some random numbers, you have to ensure that there are enough ballots for the winner:
        //  let winnerVotes = votes.map{ it.value }.max()
        //  then (Nc - Np) >= winnerVotes
        //       (Nc - Np) = (undervotes + nvotes) / voteForN >= winnerVotes
        //                    undervotes >= winnerVotes * voteForN - nvotes   // C3

        //// find winners, check that the minimum value is satisfied
        // This works for PLURALITY, APPROVAL, SUPERMAJORITY.  IRV handled by RaireContest

        // "A winning candidate must have a minimum fraction f ∈ (0, 1) of the valid votes to win". assume that means nvotes, not Nc.
        val useMin = info.minFraction ?: 0.0
        val overTheMin = votes.toList().filter{ it.second.toDouble()/nvotes >= useMin }
        val useNwinners = min(overTheMin.size, info.nwinners)
        winners = overTheMin.subList(0, useNwinners).map { it.first }
        val mapIdToName: Map<Int, String> = info.candidateNames.toList().associate { Pair(it.second, it.first) } // invert the map
        winnerNames = winners.map { mapIdToName[it]!! }
        if (winners.isEmpty()) {
            val pct = votes.toList().associate { it.first to it.second.toDouble() / nvotes }.toMap()
            println("*** there are no winners for minFraction=$useMin vote% = $pct")
        }

        // find losers
        val mlosers = mutableListOf<Int>()
        info.candidateNames.forEach { (_, id) ->
            if (!winners.contains(id)) mlosers.add(id)
        }
        losers = mlosers.toList()
    }

    override fun toString() = buildString {
        append("$name ($id) Nc=$Nc Np=$Np votesAndUndervotes=${votesAndUndervotes()}")
    }

    fun calcMargin(winner: Int, loser: Int): Double {
        val winnerVotes = votes[winner] ?: 0
        val loserVotes = votes[loser] ?: 0
        return (winnerVotes - loserVotes) / Nc.toDouble()
    }

    fun percent(cand: Int): Double {
        val candVotes = votes[cand] ?: 0
        return candVotes / Nc.toDouble()
    }

    fun votesAndUndervotes(): Map<Int, Int> {
        return (votes.map { Pair(it.key, it.value)} + Pair(ncandidates, undervotes)).toMap()
    }

    override fun show(): String {
        return "Contest(info=$info, Nc=$Nc, Np=$Np, id=$id, name='$name', choiceFunction=$choiceFunction, ncandidates=$ncandidates, votesAndUndervotes=${votesAndUndervotes()}, winnerNames=$winnerNames, winners=$winners, losers=$losers)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Contest

        if (iNc != other.iNc) return false
        if (Np != other.Np) return false
        if (undervotes != other.undervotes) return false
        if (info != other.info) return false
        if (winnerNames != other.winnerNames) return false
        if (winners != other.winners) return false
        if (losers != other.losers) return false
        if (votes != other.votes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = iNc
        result = 31 * result + Np
        result = 31 * result + undervotes
        result = 31 * result + info.hashCode()
        result = 31 * result + winnerNames.hashCode()
        result = 31 * result + winners.hashCode()
        result = 31 * result + losers.hashCode()
        result = 31 * result + votes.hashCode()
        return result
    }

    companion object {
        fun makeWithCandidateNames(info: ContestInfo, votesByName: Map<String, Int>, Nc: Int, Np: Int): Contest {
            val votesById = votesByName.map { (key, value) -> Pair(info.candidateNames[key]!!, value) }.toMap()
            return Contest(info, votesById, Nc, Np)
        }
    }
}

/** Could rename to "Contest with assertions". note mutability. */
open class ContestUnderAudit(
    val contest: ContestIF,
    val isComparison: Boolean = true,
    val hasStyle: Boolean = true,
) {
    val id = contest.id
    val name = contest.name
    val choiceFunction = contest.choiceFunction
    val ncandidates = contest.ncandidates
    val Nc = contest.Nc()
    val Np = contest.Np()

    var preAuditStatus = TestH0Status.InProgress // pre-auditing status: NoLosers, NoWinners, ContestMisformed, MinMargin, TooManyPhantoms
    var pollingAssertions: List<Assertion> = emptyList() // mutable needed for Raire override and serialization
    var clcaAssertions: List<ClcaAssertion> = emptyList() // mutable needed for serialization

    init {
        if (contest.losers().size == 0) {
            preAuditStatus = TestH0Status.NoLosers
        } else if (contest.winners().size == 0) {
            preAuditStatus = TestH0Status.NoWinners
        }
        // should really be called after init is done
        if (contest is Contest || (contest is OneAuditContest && (contest.contest is Contest))) {
            pollingAssertions = makePollingAssertions()
        }
        // So Raire has to add its own asserttions
    }

    private fun makePollingAssertions(): List<Assertion> {
        val useVotes = when (contest) {
            is Contest -> contest.votes
            is OneAuditContest -> (contest.contest as Contest).votes
            else -> throw RuntimeException("contest type ${contest.javaClass.name} is not supported")
        }

        return when (choiceFunction) {
            SocialChoiceFunction.APPROVAL,
            SocialChoiceFunction.PLURALITY -> makePluralityAssertions(useVotes)
            SocialChoiceFunction.SUPERMAJORITY -> makeSuperMajorityAssertions(useVotes)
            else -> throw RuntimeException("choice function ${choiceFunction} is not supported")
        }
    }

    private fun makePluralityAssertions(votes: Map<Int, Int>): List<Assertion> {
        // test that every winner beats every loser. SHANGRLA 2.1
        val assertions = mutableListOf<Assertion>()
        contest.winners().forEach { winner ->
            contest.losers().forEach { loser ->
                val assorter = PluralityAssorter.makeWithVotes(contest, winner, loser, votes)
                assertions.add(Assertion(contest.info(), assorter))
            }
        }
        return assertions
    }

    private fun makeSuperMajorityAssertions(votes: Map<Int, Int>): List<Assertion> {
        require(contest.info().minFraction != null)
        // each winner generates 1 assertion. SHANGRLA 2.3
        val assertions = mutableListOf<Assertion>()
        contest.winners().forEach { winner ->
            val assorter = SuperMajorityAssorter.makeWithVotes(contest, winner, contest.info().minFraction!!, votes)
            assertions.add(Assertion(contest.info(), assorter))
        }
        return assertions
    }

    // TODO could move to test
    fun makeClcaAssertions(cvrs : Iterable<Cvr>): ContestUnderAudit {
        val assertionMap = pollingAssertions.map { Pair(it, Welford()) }
        cvrs.filter { it.hasContest(id) }.forEach { cvr ->
            assertionMap.map { (assertion, welford) ->
                welford.update(assertion.assorter.assort(cvr, usePhantoms = false))  //TODO usePhantoms?
            }
        }
        return makeClcaAssertions(assertionMap)
    }

    fun makeClcaAssertions(assertionMap: List<Pair<Assertion, Welford>> ): ContestUnderAudit {
        require(isComparison) { "makeComparisonAssertions() can be called only on comparison contest"}

        this.clcaAssertions = assertionMap.filter { it.first.info.id == this.id }
            .map { (assertion, welford) ->
                val clcaAssorter = makeClcaAssorter(assertion, welford.mean)
                ClcaAssertion(contest.info(), clcaAssorter)
            }
        return this
    }

    // This is more robust than averaging cvrs, since cvrs have to be complete and accurate.
    // OTOH, need complete and accurate CardLocation Manifest anyway!!
    fun makeClcaAssertionsFromReportedMargin(): ContestUnderAudit {
        require(isComparison) { "makeComparisonAssertions() can be called only on comparison contest"}

        this.clcaAssertions = pollingAssertions.map { assertion ->
            val clcaAssorter = makeClcaAssorter(assertion, null)
            ClcaAssertion(contest.info(), clcaAssorter)
        }
        return this
    }

    open fun makeClcaAssorter(assertion: Assertion, assortValueFromCvrs: Double?): ClcaAssorter {
        return ClcaAssorter(contest.info(), assertion.assorter, assortValueFromCvrs, hasStyle=hasStyle)
    }

    fun assertions(): List<Assertion> {
        return if (isComparison) clcaAssertions else pollingAssertions
    }

    fun minClcaAssertion(): ClcaAssertion? {
        val margins = clcaAssertions.map { it.assorter.reportedMargin()  }
        val minMargin = if (margins.isEmpty()) 0.0 else margins.min()
        return clcaAssertions.find { it.assorter.reportedMargin() == minMargin }
    }

    fun minPollingAssertion(): Assertion? {
        val margins = pollingAssertions.map { it.assorter.reportedMargin() }
        val minMargin = if (margins.isEmpty()) 0.0 else margins.min()
        return pollingAssertions.find { it.assorter.reportedMargin() == minMargin }
    }

    fun minAssertion(): Assertion? {
        return if (isComparison) minClcaAssertion() else minPollingAssertion()
    }

    fun minMargin(): Double {
        return if (isComparison) (minClcaAssertion()?.assorter?.reportedMargin() ?: 0.0)
        else (minPollingAssertion()?.assorter?.reportedMargin() ?: 0.0)
    }

    open fun recountMargin(): Double {
        var pct = -1.0
        val minAssertion: Assertion = minAssertion() ?: return pct
        if (contest is Contest) {
            val votes = contest.votes
            val winner = votes[minAssertion.assorter.winner()]!!
            val loser = votes[minAssertion.assorter.loser()]!!
            pct = (winner - loser) / (winner.toDouble())
        }
        return pct
    }

    override fun toString() = contest.toString()

    open fun show() = buildString {
        val votes = if (contest is Contest) contest.votesAndUndervotes() else emptyMap()
        appendLine("${contest.javaClass.simpleName} '$name' ($id) votesAndUndervotes=${votes}")
        appendLine(" margin=${df(minMargin())} recount=${df(recountMargin())} Nc=$Nc Np=$Np")
        appendLine(" choiceFunction=${choiceFunction} nwinners=${contest.info().nwinners}, winners=${contest.winners()}")
        append(showCandidates())
    }

    open fun showCandidates() = buildString {
        val votes = if (contest is Contest) contest.votes else emptyMap()
        contest.info().candidateNames.forEach { (name, id) ->
            appendLine("   $id '$name': votes=${votes[id]}") }
        append("    Total=${votes.values.sum()}")
    }

    open fun showShort() = buildString {
        val votes = if (contest is Contest) contest.votes.toString() else "N/A"
        append("$name ($id) votes=${votes} Nc=$Nc minMargin=${df(minMargin())}")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContestUnderAudit

        if (isComparison != other.isComparison) return false
        if (hasStyle != other.hasStyle) return false
        if (!contest.equals(other.contest)) return false
        if (preAuditStatus != other.preAuditStatus) return false
        if (pollingAssertions != other.pollingAssertions) return false
        if (clcaAssertions != other.clcaAssertions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isComparison.hashCode()
        result = 31 * result + hasStyle.hashCode()
        result = 31 * result + contest.hashCode()
        result = 31 * result + preAuditStatus.hashCode()
        result = 31 * result + pollingAssertions.hashCode()
        result = 31 * result + clcaAssertions.hashCode()
        return result
    }

}

// make ClcaAssertions for multiple Contests from one iteration over the Cvrs
// The Cvrs must have the undervotes recorded
fun makeClcaAssertions(contestsUA: List<ContestUnderAudit>, cvrs: Iterator<Cvr>, show: Boolean = false) {
    val assertionMap = mutableListOf<Pair<Assertion, Welford>>()
    contestsUA.forEach { contestUA ->
        contestUA.pollingAssertions.forEach { assertion ->
            assertionMap.add(Pair(assertion, Welford()))
        }
    }

    cvrs.forEach { cvr ->
        assertionMap.map { (assertion, welford) ->
            if (cvr.hasContest(assertion.info.id)) {
                welford.update(assertion.assorter.assort(cvr, usePhantoms = false))
            }
        }
    }
    if (show) {
        assertionMap.forEach { (assert, welford) -> println("contest $assert : ${welford.mean}") }
    }

    contestsUA.forEach { it.makeClcaAssertions(assertionMap) }
}

