package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.makeContestFromCvrs
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

// Needed to allow RaireContest as subclass, which does not use votes: Map<Int, Int>
interface ContestIF {
    val info: ContestInfo
    val Nc: Int

    val winnerNames: List<String>
    val winners: List<Int>
    val losers: List<Int>
}

/**
 * Contest with the reported results.
 * @parameter voteInput: candidateId -> reported number of votes. keys must be in info.candidateIds, though zeros may be ommitted.
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

        //// find winners, check that the minimum value is satisfied
        // This works for PLURALITY, APPROVAL, SUPERMAJORITY.  IRV handled by RaireContest
        val useMin = info.minFraction ?: 0.0
        val totalVotes = votes.values.sum() // this is plurality of the votes, not of the cards or the ballots
        require(totalVotes <= Nc) { "Nc $Nc must be >= totalVotes ${totalVotes}"}

        // todo why use totalVotes instead of Nc?
        val overTheMin = votes.toList().filter{ it.second.toDouble()/totalVotes >= useMin }.sortedBy{ it.second }.reversed()
        val useNwinners = min(overTheMin.size, info.nwinners)
        winners = overTheMin.subList(0, useNwinners).map { it.first }
        // invert the map
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
 * @parameter ncvrs: count of cvrs for this contest (comparison only)
 */
open class ContestUnderAudit(
    val contest: ContestIF,
    val isComparison: Boolean = true,
    val hasStyle: Boolean = true,
) {
    val id = contest.info.id
    val name = contest.info.name
    val choiceFunction = contest.info.choiceFunction
    val ncandidates = contest.info.candidateIds.size
    val Nc = contest.Nc

    var pollingAssertions: List<Assertion> = emptyList()
    var comparisonAssertions: List<ComparisonAssertion> = emptyList()

    var estSampleSize = 0 // Estimate of the sample size required to confirm the contest
    var done = false
    var status = TestH0Status.NotStarted // or its own enum ??
    var estSampleSizeNoStyles = 0 // number of total samples estimated needed, uniformPolling (Polling, no style only)

    // should only be used for testing i think
    constructor(info: ContestInfo, cvrs: List<CvrIF>, isComparison: Boolean=true, hasStyle: Boolean=true):
            this( makeContestFromCvrs(info, cvrs), isComparison, hasStyle)

    override fun toString() = buildString {
        append("${name} ($id) Nc=$Nc minMargin=${df(minMargin())} est=$estSampleSize")
    }

    open fun makePollingAssertions(votes: Map<Int, Int>?=null): ContestUnderAudit {
        require(!isComparison) { "makePollingAssertions() can be called only on polling contest"}
        val useVotes = if (votes != null) votes else (contest as Contest).votes

        this.pollingAssertions = when (choiceFunction) {
            SocialChoiceFunction.APPROVAL,
            SocialChoiceFunction.PLURALITY -> makePluralityAssertions(useVotes)
            SocialChoiceFunction.SUPERMAJORITY -> makeSuperMajorityAssertions(useVotes)
            else -> throw RuntimeException("choice function ${choiceFunction} is not supported")
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
        require(isComparison) { "makeComparisonAssertions() can be called only on comparison contest"}
        val useVotes = if (votes != null) votes else (contest as Contest).votes
        val assertions = when (contest.info.choiceFunction) {
            SocialChoiceFunction.APPROVAL,
            SocialChoiceFunction.PLURALITY, -> makePluralityAssertions(useVotes)
            SocialChoiceFunction.SUPERMAJORITY -> makeSuperMajorityAssertions(useVotes)
            else -> throw RuntimeException("choice function ${contest.info.choiceFunction} is not supported")
        }

        this.comparisonAssertions = assertions.map { assertion ->
            val margin = assertion.assorter.calcAssorterMargin(id, cvrs)
            val comparisonAssorter = ComparisonAssorter(contest, assertion.assorter, margin2mean(margin), hasStyle=hasStyle)
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

