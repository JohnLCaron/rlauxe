package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.mean2margin

enum class SocialChoiceFunction { PLURALITY, APPROVAL, SUPERMAJORITY, IRV }

data class Contest(
    val name: String,
    val id: Int,
    val candidateNames: Map<String, Int>, // candidate name -> candidate id
    val winnerNames: List<String>,
    val choiceFunction: SocialChoiceFunction,
    val minFraction: Double? = null, // supermajority only.
) {
    val winners: List<Int>
    val losers: List<Int>
    val candidates: List<Int>

    init {
        require(choiceFunction != SocialChoiceFunction.SUPERMAJORITY || minFraction != null)
        val mwinners = mutableListOf<Int>()
        val mlosers = mutableListOf<Int>()
        candidateNames.forEach { (name, id) ->
            if (winnerNames.contains(name)) mwinners.add(id) else mlosers.add(id)
        }
        winners = mwinners.toList()
        losers = mlosers.toList()
        candidates = winners + losers
    }
}

/**
 * Mutable form of Contest.
 * Assume Comparison Audit, use_styles == true
 * @parameter ncvrs: count of cvrs for this contest
 * @parameter upperBound: upper bound on cards for this contest
 */
open class ContestUnderAudit(val contest: Contest, var ncvrs: Int = 0, var upperBound: Int? = null) {
    val name = contest.name
    val id = contest.id

    var minAssert: ComparisonAssertion? = null
    var sampleSize = 0 // Estimate the sample size required to confirm the contest
    var sampleThreshold = 0L // highest sample.sampleNum for this contest, used when running the audit, to include only mvrs needed

    var pollingAssertions: List<Assertion> = emptyList()
    var comparisonAssertions: List<ComparisonAssertion> = emptyList()

    override fun toString() = buildString {
        appendLine("   contest = ${contest.name}")
        appendLine("   ncards = $upperBound ncvrs = $ncvrs")
    }

    fun makePollingAssertions(cvrs: Iterable<CvrIF>) {
        val cvrsForMe = cvrs.filter { it.hasContest(contest.id) }
        this.pollingAssertions = when (contest.choiceFunction) {
            SocialChoiceFunction.APPROVAL,
            SocialChoiceFunction.PLURALITY -> makePluralityAssertions(contest, cvrsForMe)
            SocialChoiceFunction.SUPERMAJORITY -> makeSuperMajorityAssertions(contest, cvrsForMe)
            else -> throw RuntimeException(" choice function ${contest.choiceFunction} is not supported")
        }
    }

    open fun makeComparisonAssertions(cvrs : Iterable<CvrUnderAudit>) {
        val assertions = when (contest.choiceFunction) {
            SocialChoiceFunction.APPROVAL,
            SocialChoiceFunction.PLURALITY, -> makePluralityAssertions(contest, cvrs)
            SocialChoiceFunction.SUPERMAJORITY -> makeSuperMajorityAssertions(contest, cvrs)
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

        val margins = comparisonAssertions.map { assert ->
            mean2margin(assert.assorter.avgCvrAssortValue)
        }
        val minMargin = margins.min()
        this.minAssert = comparisonAssertions.find { it.assorter.avgCvrAssortValue == minMargin }
        println("min = $minMargin minAssert = $minAssert")
    }
}
