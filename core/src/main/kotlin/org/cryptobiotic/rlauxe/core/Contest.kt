package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.makeContestFromCvrs
import kotlin.math.min

enum class SocialChoiceFunction { PLURALITY, APPROVAL, SUPERMAJORITY, IRV }

/** pre election information **/
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
}

/**
 * Contest and the reported results
 * @parameter votes: candidateId -> reported number of votes. keys must be in contest.candidateIds, though zeros may be ommitted
 * @parameter Nc: maximum ballots/cards that contain this contest, independently verified (not from cvrs).
 */
data class Contest(val info: ContestInfo, val votes: Map<Int, Int>, val Nc: Int) {
    val id = info.id
    val name = info.name
    val choiceFunction = info.choiceFunction

    val winnerNames: List<String>
    val winners: List<Int>
    val losers: List<Int>
    // var margin = 0.0 // temp debug

    init {
        votes.forEach {
            require(info.candidateIds.contains(it.key))
        }
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
}

/**
 * Mutable form of Contest.
 * @parameter ncvrs: count of cvrs for this contest // WHY?
 */
open class ContestUnderAudit(val contest: Contest, var ncvrs: Int = 0) {
    val id = contest.id
    val name = contest.name
    var Nc = contest.Nc
    var done = false

    constructor(info: ContestInfo, cvrs: List<CvrIF>) : this(makeContestFromCvrs(info, cvrs), cvrs.filter { it.hasContest(info.id) }.count())

    var estSampleSize = 0 // Estimate of the sample size required to confirm the contest
    // var sampleThreshold = 0L // highest sample.sampleNum for this contest, used when running the audit, to include only mvrs needed
    var actualAvailable = 0 // number of samples available in the current consistent sampling based on estSampleSize

    var pollingAssertions: List<Assertion> = emptyList()
    var comparisonAssertions: List<ComparisonAssertion> = emptyList()

    override fun toString() = buildString {
        append("contest: ${contest.info.name} ($id) ncvrs = $ncvrs")
    }

    open fun makePollingAssertions(): ContestUnderAudit {
        this.pollingAssertions = when (contest.choiceFunction) {
            SocialChoiceFunction.APPROVAL,
            SocialChoiceFunction.PLURALITY -> makePluralityAssertions()
            SocialChoiceFunction.SUPERMAJORITY -> makeSuperMajorityAssertions()
            else -> throw RuntimeException(" choice function ${contest.choiceFunction} is not supported")
        }
        return this
    }

    fun makePluralityAssertions(): List<Assertion> {
        // test that every winner beats every loser. SHANGRLA 2.1
        val assertions = mutableListOf<Assertion>()
        contest.winners.forEach { winner ->
            contest.losers.forEach { loser ->
                val assorter = PluralityAssorter(contest, winner, loser)
                assertions.add(Assertion(contest, assorter))
            }
        }
        return assertions
    }

    // needed
    fun makeSuperMajorityAssertions(): List<Assertion> {
        // each winner generates 1 assertion. SHANGRLA 2.3
        val assertions = mutableListOf<Assertion>()
        contest.winners.forEach { winner ->
            val assorter = SuperMajorityAssorter(contest, winner, contest.info.minFraction!!)
            assertions.add(Assertion(contest, assorter))
        }
        return assertions
    }

    open fun makeComparisonAssertions(cvrs : Iterable<CvrIF>): ContestUnderAudit {
        val assertions = when (contest.choiceFunction) {
            SocialChoiceFunction.APPROVAL,
            SocialChoiceFunction.PLURALITY, -> makePluralityAssertions()
            SocialChoiceFunction.SUPERMAJORITY -> makeSuperMajorityAssertions()
            else -> throw RuntimeException(" choice function ${contest.choiceFunction} is not supported")
        }

        this.comparisonAssertions = assertions.map { assertion ->
            val welford = Welford()
            cvrs.forEach { cvr ->
                if (cvr.hasContest(contest.id)) {
                    welford.update(assertion.assorter.assort(cvr))
                }
            }
            val comparisonAssorter = ComparisonAssorter(contest, assertion.assorter, welford.mean)
            ComparisonAssertion(contest, comparisonAssorter)
        }
        return this
    }

    fun minComparisonAssertion(): ComparisonAssertion {
        val margins = comparisonAssertions.map { it.assorter.margin }
        val minMargin = if (margins.isEmpty()) 0.0 else margins.min()
        return comparisonAssertions.find { it.assorter.margin == minMargin }!!
    }

    fun minPollingAssertion(): Assertion {
        val margins = pollingAssertions.map { it.margin }
        val minMargin = if (margins.isEmpty()) 0.0 else margins.min()
        return pollingAssertions.find { it.margin == minMargin }!!
    }
}
