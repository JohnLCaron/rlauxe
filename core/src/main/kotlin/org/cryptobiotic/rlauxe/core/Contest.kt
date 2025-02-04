package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.margin2mean
import kotlin.math.min

enum class SocialChoiceFunction { PLURALITY, APPROVAL, SUPERMAJORITY, IRV }

/** pre-election information **/
data class ContestInfo(
    val name: String,
    val id: Int,
    val candidateNames: Map<String, Int>, // candidate name -> candidate id
    val choiceFunction: SocialChoiceFunction,
    val nwinners: Int = 1,
    val minFraction: Double? = null, // supermajority only.
) {
    val candidateIds: List<Int>

    init {
        require(choiceFunction != SocialChoiceFunction.SUPERMAJORITY || minFraction != null) { "SUPERMAJORITY requires minFraction"}
        require(choiceFunction == SocialChoiceFunction.SUPERMAJORITY || minFraction == null) { "only SUPERMAJORITY can have minFraction"}
        require(minFraction == null || minFraction in (0.0..1.0)) { "minFraction between 0 and 1"}
        require(nwinners in (1..candidateNames.size)) { "nwinners between 1 and candidateNames.size"}

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
        append("$name ($id) candidates=${candidateNames}")
    }
}

// Needed to allow RaireContest as subclass, which does not have votes: Map<Int, Int>
interface ContestIF {
    val info: ContestInfo
    val id: Int
    val Nc: Int
    val Np: Int
    val ncandidates: Int
    val choiceFunction: SocialChoiceFunction

    val winnerNames: List<String>
    val winners: List<Int>
    val losers: List<Int>
    fun show() : String = ""
}

//    When we have styles, we can calculate Nb_c = physical ballots for contest C.
//    Let V_c = reported votes for contest C; V_c <= Nb_c <= N_c.

//    Let N_c = upper bound on ballots for contest C.
//    Let V_c = reported votes for contest C
//    Let U_c = undervotes for contest C; U_c = Nb_c - V_c >= 0.
//    Let Np_c = nphantoms for contest C; Np_c = N_c - Nb_c.
//    Then N_c = V_c + U_c + Np_c.

/**
 * Contest with the reported results.
 * @parameter voteInput: candidateId -> reported number of votes. keys must be in info.candidateIds, though zeros may be ommitted.
 * @parameter Nc: maximum ballots/cards that contain this contest, independently verified (not from cvrs).
 * @parameter Np: number of phantoms for this contest.
 */
class Contest(
        override val info: ContestInfo,
        voteInput: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
        override val Nc: Int,
        override val Np: Int,       // may not know this, if !hasStyles
        // val hasStyles: Boolean,
        // val Nb: Int?,  // needed to form the factor N / Nc when !hasStyles
    ): ContestIF {
    override val id = info.id
    val name = info.name
    override val choiceFunction = info.choiceFunction
    override val ncandidates = info.candidateIds.size

    val votes: Map<Int, Int>  // same as voteInput except zero vote candidates have been added
    override val winnerNames: List<String>
    override val winners: List<Int>
    override val losers: List<Int>

    val undervotes: Int
    val minMargin: Double  // minimum margin between top winner and top loser

    init {
        // construct votes, adding 0 votes if needed
        voteInput.forEach {
            require(info.candidateIds.contains(it.key)) { "'${it.key}' not found in contestInfo candidateIds ${info.candidateIds}"}
        }
        val voteBuilder = mutableMapOf<Int, Int>()
        voteBuilder.putAll(voteInput)
        info.candidateIds.forEach {
            if (!voteInput.contains(it)) {
                voteBuilder[it] = 0
            }
        }
        votes = voteBuilder.toMap()
        val nvotes = votes.values.sum()
        require(nvotes <= Nc) { "Nc $Nc must be <= totalVotes ${nvotes}"}
        undervotes = Nc - nvotes - Np

        //// find winners, check that the minimum value is satisfied
        // This works for PLURALITY, APPROVAL, SUPERMAJORITY.  IRV handled by RaireContest
        val useMin = info.minFraction ?: 0.0

        // "A winning candidate must have a minimum fraction f ∈ (0, 1) of the valid votes to win". assume that means nvotes, not Nc.
        val overTheMin = votes.toList().filter{ it.second.toDouble()/nvotes >= useMin }.sortedBy{ it.second }.reversed()
        val useNwinners = min(overTheMin.size, info.nwinners)
        winners = overTheMin.subList(0, useNwinners).map { it.first } // TODO deal with when there are no winners
        val mapIdToName: Map<Int, String> = info.candidateNames.toList().associate { Pair(it.second, it.first) } // invert the map
        winnerNames = winners.map { mapIdToName[it]!! }

        // find losers
        val mlosers = mutableListOf<Int>()
        info.candidateNames.forEach { (_, id) ->
            if (!winners.contains(id)) mlosers.add(id)
        }
        losers = mlosers.toList()

        // TODO this assumes plurality with nwinners = 1. Maybe get rid of this ??
        val sortedVotes = votes.toList().sortedBy{ it.second }.reversed()
        minMargin = (sortedVotes[0].second - sortedVotes[1].second) / Nc.toDouble()
    }

    override fun toString() = buildString {
        append("$name ($id) Nc=$Nc Np=$Np votes=${votes} minMargin=${df(minMargin)}")
    }

    fun calcMargin(winner: Int, loser: Int): Double {
        val winnerVotes = votes[winner] ?: 0
        val loserVotes = votes[loser] ?: 0
        val reportedMargin = (winnerVotes - loserVotes) / Nc.toDouble()
        return reportedMargin
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Contest

        if (Nc != other.Nc) return false
        if (Np != other.Np) return false
        if (minMargin != other.minMargin) return false
        if (info != other.info) return false
        if (votes != other.votes) return false
        if (winnerNames != other.winnerNames) return false
        if (winners != other.winners) return false
        if (losers != other.losers) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Nc
        result = 31 * result + Np
        result = 31 * result + minMargin.hashCode()
        result = 31 * result + info.hashCode()
        result = 31 * result + votes.hashCode()
        result = 31 * result + winnerNames.hashCode()
        result = 31 * result + winners.hashCode()
        result = 31 * result + losers.hashCode()
        return result
    }

    override fun show(): String {
        return "Contest(info=$info, Nc=$Nc, Np=$Np, id=$id, name='$name', choiceFunction=$choiceFunction, ncandidates=$ncandidates, votes=$votes, winnerNames=$winnerNames, winners=$winners, losers=$losers, undervotes=$undervotes, minMargin=$minMargin)"
    }

    companion object {
        fun makeWithCandidateNames(info: ContestInfo, votesByName: Map<String, Int>, Nc: Int, Np: Int): Contest {
            val votesById = votesByName.map { (key, value) -> Pair(info.candidateNames[key]!!, value) }.toMap()
            return Contest(info, votesById, Nc, Np)
        }
    }
}

/** Mutable form of Contest. */
open class ContestUnderAudit(
    val contest: ContestIF,
    val isComparison: Boolean = true, // TODO change to AuditType?
    val hasStyle: Boolean = true,
) {
    val id = contest.info.id
    val name = contest.info.name
    val choiceFunction = contest.info.choiceFunction
    val ncandidates = contest.info.candidateIds.size
    val Nc = contest.Nc
    val Np = contest.Np

    var pollingAssertions: List<Assertion> = emptyList()
    var clcaAssertions: List<ClcaAssertion> = emptyList()

    var estSampleSize = 0 // Estimate of the sample size required to confirm the contest
    var done = false
    var status = TestH0Status.InProgress // or its own enum ??
    var estSampleSizeNoStyles = 0 // number of total samples estimated needed, uniformPolling (Polling, no style only)

    // open fun makePollingAssertions(votes: Map<Int, Int>?=null): ContestUnderAudit {
    open fun makePollingAssertions(): ContestUnderAudit {
        require(!isComparison) { "makePollingAssertions() can be called only on polling contest"}
        // val useVotes = if (votes != null) votes else (contest as Contest).votes
        val useVotes = (contest as Contest).votes

        this.pollingAssertions = when (choiceFunction) {
            SocialChoiceFunction.APPROVAL,
            SocialChoiceFunction.PLURALITY -> makePluralityAssertions(useVotes)
            SocialChoiceFunction.SUPERMAJORITY -> makeSuperMajorityAssertions(useVotes)
            else -> throw RuntimeException("choice function ${choiceFunction} is not supported")
        }
        return this
    }

    open fun makePluralityAssertions(votes: Map<Int, Int>): List<Assertion> {
        // test that every winner beats every loser. SHANGRLA 2.1
        val assertions = mutableListOf<Assertion>()
        contest.winners.forEach { winner ->
            contest.losers.forEach { loser ->
                val assorter = PluralityAssorter.makeWithVotes(contest, winner, loser, votes)
                assertions.add(Assertion(contest, assorter))
            }
        }
        return assertions
    }

    fun makeSuperMajorityAssertions(votes: Map<Int, Int>): List<Assertion> {
        require(contest.info.minFraction != null)
        // each winner generates 1 assertion. SHANGRLA 2.3
        val assertions = mutableListOf<Assertion>()
        contest.winners.forEach { winner ->
            val assorter = SuperMajorityAssorter.makeWithVotes(contest, winner, contest.info.minFraction!!, votes)
            assertions.add(Assertion(contest, assorter))
        }
        return assertions
    }

    // cvrs must be complete in order to get the margin right.
    // open fun makeComparisonAssertions(cvrs : Iterable<Cvr>, votes: Map<Int, Int>? = null): ContestUnderAudit {
    open fun makeClcaAssertions(cvrs : Iterable<Cvr>): ContestUnderAudit {
        require(isComparison) { "makeComparisonAssertions() can be called only on comparison contest"}
        // val useVotes = if (votes != null) votes else (contest as Contest).votes
        val useVotes = (contest as Contest).votes
        val assertions = when (contest.info.choiceFunction) {
            SocialChoiceFunction.APPROVAL,
            SocialChoiceFunction.PLURALITY, -> makePluralityAssertions(useVotes)
            SocialChoiceFunction.SUPERMAJORITY -> makeSuperMajorityAssertions(useVotes)
            else -> throw RuntimeException("choice function ${contest.info.choiceFunction} is not supported")
        }

        this.clcaAssertions = assertions.map { assertion ->
            val margin = assertion.assorter.calcAssorterMargin(id, cvrs)
            val clcaAssorter = ClcaAssorter(contest, assertion.assorter, margin2mean(margin), hasStyle=hasStyle)
            ClcaAssertion(contest, clcaAssorter)
        }
        return this
    }

    fun assertions(): List<Assertion> {
        return if (isComparison) clcaAssertions else pollingAssertions
    }

    fun minClcaAssertion(): ClcaAssertion? {
        val margins = clcaAssertions.map { it.assorter.reportedMargin()  }
        val minMargin = if (margins.isEmpty()) 0.0 else margins.min()
        return clcaAssertions.find { it.assorter.reportedMargin()  == minMargin }
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


    override fun toString() = buildString {
        append("${name} ($id) Nc=$Nc minMargin=${df(minMargin())} est=$estSampleSize")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContestUnderAudit

        if (isComparison != other.isComparison) return false
        if (hasStyle != other.hasStyle) return false
        if (estSampleSize != other.estSampleSize) return false
        if (done != other.done) return false
        if (estSampleSizeNoStyles != other.estSampleSizeNoStyles) return false
        if (contest != other.contest) return false
        if (pollingAssertions != other.pollingAssertions) return false
        if (clcaAssertions != other.clcaAssertions) return false
        if (status != other.status) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isComparison.hashCode()
        result = 31 * result + hasStyle.hashCode()
        result = 31 * result + estSampleSize
        result = 31 * result + done.hashCode()
        result = 31 * result + estSampleSizeNoStyles
        result = 31 * result + contest.hashCode()
        result = 31 * result + pollingAssertions.hashCode()
        result = 31 * result + clcaAssertions.hashCode()
        result = 31 * result + status.hashCode()
        return result
    }

}

