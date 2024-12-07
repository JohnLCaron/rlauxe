package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.makeContestFromCvrs
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
        require(choiceFunction != SocialChoiceFunction.SUPERMAJORITY || minFraction != null)
        candidateIds = candidateNames.toList().map { it.second }
    }

    override fun toString() = buildString {
        append("${name} ($id) ncands=${candidateIds.size}")
    }
}

interface ContestIF {
    val info: ContestInfo
    val Nc: Int

    val winnerNames: List<String>
    val winners: List<Int>
    val losers: List<Int>
}

/**
 * Contest with the reported results
 * @parameter votes: candidateId -> reported number of votes. keys must be in info.candidateIds, though zeros may be ommitted.
 * @parameter Nc: maximum ballots/cards that contain this contest, independently verified (not from cvrs).
 */
class Contest(
        override val info: ContestInfo,
        voteInput: Map<Int, Int>,
        override val Nc: Int,
    ): ContestIF {
    val id = info.id
    val name = info.name
    val choiceFunction = info.choiceFunction

    val votes: Map<Int, Int>
    override val winnerNames: List<String>
    override val winners: List<Int>
    override val losers: List<Int>

    init {
        // construct votes, adding 0 votes if needed
        voteInput.forEach {
            require(info.candidateIds.contains(it.key))
        }
        val voteBuilder = mutableMapOf<Int, Int>()
        voteBuilder.putAll(voteInput)
        info.candidateIds.forEach {
            if (!voteInput.contains(it)) {
                voteBuilder[it] = 0
            }
        }
        votes = voteBuilder.toMap()

        // find winners, check that the minimum value is satisfied
        //val sortedVotes: List<Pair<Int, Int>> = votes.toList().sortedBy{ it.second }.reversed() // could keep the sorted list
        //winners = sortedVotes.subList(0, info.nwinners).map { it.first }

        // This works for PLURALITY, APPROVAL, SUPERMAJORITY.  TODO what about IRV ?
        val useMin = info.minFraction ?: 0.0
        val totalVotes = votes.values.sum() // so this is plurality of the votes, not of the cards or the ballots
        val overTheMin = votes.toList().filter{ it.second.toDouble()/totalVotes >= useMin }.sortedBy{ it.second }.reversed()
        val useNwinners = min(overTheMin.size, info.nwinners)
        winners = overTheMin.subList(0, useNwinners).map { it.first }
        val mapIdToName: Map<Int, String> = info.candidateNames.toList().associate { Pair(it.second, it.first) }
        winnerNames = winners.map { mapIdToName[it]!! }

        // find losers
        val mlosers = mutableListOf<Int>()
        // could require that all candidates are in votes, but this allows candidates with no votes to not be in votes
        info.candidateNames.forEach { (_, id) ->
            if (!winners.contains(id)) mlosers.add(id)
        }
        losers = mlosers.toList()
    }


    override fun toString() = buildString {
        append("${info} Nc= $Nc votes=${votes}")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Contest

        if (Nc != other.Nc) return false
        if (info != other.info) return false
        if (votes != other.votes) return false
        if (winnerNames != other.winnerNames) return false
        if (winners != other.winners) return false
        if (losers != other.losers) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Nc
        result = 31 * result + info.hashCode()
        result = 31 * result + votes.hashCode()
        result = 31 * result + winnerNames.hashCode()
        result = 31 * result + winners.hashCode()
        result = 31 * result + losers.hashCode()
        return result
    }
}

/**
 * Mutable form of Contest.
 * @parameter ncvrs: count of cvrs for this contest
 */
open class ContestUnderAudit(
    val contest: ContestIF,
    var ncvrs: Int = 0,
    val isComparison: Boolean = true,
    val hasStyle: Boolean = true,
) {
    val id = contest.info.id
    val name = contest.info.name
    val choiceFunction = contest.info.choiceFunction
    val ncandidates = contest.info.candidateIds.size

    var Nc = contest.Nc // TODO

    var pollingAssertions: List<Assertion> = emptyList()
    var comparisonAssertions: List<ComparisonAssertion> = emptyList()

    var estSampleSize = 0 // Estimate of the sample size required to confirm the contest
    var availableInSample = 0 // number of samples available in the current consistent sampling based on estSampleSize
    var done = false
    var status = TestH0Status.NotStarted // or its own enum ??
    var estTotalSampleSize = 0 // number of total samples estimated needed (no style)

    constructor(info: ContestInfo, cvrs: List<CvrIF>, isComparison: Boolean=true, hasStyle: Boolean=true):
            this( makeContestFromCvrs(info, cvrs), cvrs.count { it.hasContest(info.id) }, isComparison, hasStyle)

    override fun toString() = buildString {
        append("${name} ($id) Nc=$Nc minMargin=${df(minMargin())} est=$estSampleSize")
    }

    open fun makePollingAssertions(votes: Map<Int, Int>?=null): ContestUnderAudit {
        require(!isComparison)
        val useVotes = if (votes != null) votes else (contest as Contest).votes

        this.pollingAssertions = when (choiceFunction) {
            SocialChoiceFunction.APPROVAL,
            SocialChoiceFunction.PLURALITY -> makePluralityAssertions(useVotes)
            SocialChoiceFunction.SUPERMAJORITY -> makeSuperMajorityAssertions(useVotes)
            else -> throw RuntimeException(" choice function ${choiceFunction} is not supported")
        }
        return this
    }

    fun makePluralityAssertions(votes: Map<Int, Int>): List<Assertion> {
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

    open fun makeComparisonAssertions(cvrs : Iterable<CvrIF>, votes: Map<Int, Int>? = null): ContestUnderAudit {
        require(isComparison)
        val useVotes = if (votes != null) votes else (contest as Contest).votes
        val assertions = when (contest.info.choiceFunction) {
            SocialChoiceFunction.APPROVAL,
            SocialChoiceFunction.PLURALITY, -> makePluralityAssertions(useVotes)
            SocialChoiceFunction.SUPERMAJORITY -> makeSuperMajorityAssertions(useVotes)
            else -> throw RuntimeException(" choice function ${contest.info.choiceFunction} is not supported")
        }

        this.comparisonAssertions = assertions.map { assertion ->
            val welford = Welford()
            cvrs.forEach { cvr ->
                if (cvr.hasContest(id)) {
                    welford.update(assertion.assorter.assort(cvr))
                }
            }
            val comparisonAssorter = ComparisonAssorter(contest, assertion.assorter, welford.mean, hasStyle=hasStyle)
            ComparisonAssertion(contest, comparisonAssorter)
        }
        return this
    }

    fun assertions(): List<Assertion> {
        return if (isComparison) comparisonAssertions else pollingAssertions
    }

    fun minComparisonAssertion(): ComparisonAssertion? {
        val margins = comparisonAssertions.map { it.cassorter.margin }
        val minMargin = if (margins.isEmpty()) 0.0 else margins.min()
        return comparisonAssertions.find { it.cassorter.margin == minMargin }
    }

    fun minPollingAssertion(): Assertion? {
        val margins = pollingAssertions.map { it.margin }
        val minMargin = if (margins.isEmpty()) 0.0 else margins.min()
        return pollingAssertions.find { it.margin == minMargin }
    }

    fun minAssertion(): Assertion? {
        return if (isComparison) minComparisonAssertion() else minPollingAssertion()
    }

    fun minMargin(): Double {
        return if (isComparison) (minComparisonAssertion()?.margin ?: 0.0) else (minPollingAssertion()?.margin ?: 0.0)
    }
}
