package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import kotlin.math.max

data class AuditRound(
    val roundIdx: Int,
    val contests: List<ContestRound>,

    val auditWasDone: Boolean = false,
    var auditIsComplete: Boolean = false,
    var sampledIndices: List<Int>, // TODO this is stored separately; called when user chooses new algorithm for sampling
    val nmvrs: Int = 0, // TODO whats ?

    ) {
    fun show() =
        "AuditState(round = $roundIdx, nmvrs=$nmvrs, auditWasDone=$auditWasDone, auditIsComplete=$auditIsComplete)" +
                " ncontests=${contests.size} ncontestsDone=${contests.filter { it.done }.count()}"

    var newSamples: Set<Int> = emptySet()
    var previousSetCopy: Set<Int> = emptySet()

    fun createNextRound() : AuditRound {
        val next = contests.filter { !it.status.complete }.map{ it.createNextRound() }
        return AuditRound(roundIdx + 1, next, sampledIndices = emptyList())
    }

    /* TODO this sucks, not needed?
    fun setPreviousRound(previousRound : AuditRound) {
        contests.forEach { contest ->
            val previousContest = previousRound.contests.find { it.id == contest.id }!!
            contest.assertions.forEach { assertion ->
                val previousAssertion = previousContest.assertions.find { assertion == it }!!
                assertion.prevAuditResult = previousAssertion.auditResult
            }
        }
    } */

    //// called from viewer

    fun recalcSamples(sampledIndices: List<Int>, cvrs: List<BallotOrCvr>) {
        this.sampledIndices = sampledIndices
        calcNewSamples()
        calcContestMvrs(cvrs)
    }

    fun calcNewSamples(previousSet: Set<Int>? = null) {
        if (previousSet != null) this.previousSetCopy = setOf(*previousSet.toTypedArray())
        newSamples = sampledIndices.filter { it !in previousSetCopy }.toSet()
    }

    fun calcContestMvrs(cvrs: List<BallotOrCvr>) {
        val actualMvrsCount = mutableMapOf<ContestRound, Int>() // contestId -> mvrs in sample
        val newMvrsCount = mutableMapOf<ContestRound, Int>() // contestId -> new mvrs in sample
        sampledIndices.forEach { sidx ->
            val boc = cvrs[sidx] // TODO this requires the cvrs be in order!!
            contests.forEach { contest ->
                if (boc.hasContest(contest.id)) {
                    actualMvrsCount[contest] = actualMvrsCount[contest]?.plus(1) ?: 1
                    if (sidx in newSamples) {
                        newMvrsCount[contest] = newMvrsCount[contest]?.plus(1) ?: 1
                    }
                }
            }
        }
        actualMvrsCount.forEach { contest, count ->  contest.actualMvrs = count }
        newMvrsCount.forEach { contest, count ->  contest.actualNewMvrs = count }
    }

    fun maxBallotsUsed(): Int {
        var result = 0
        contests.forEach { contest ->
            contest.assertions.forEach { assertion ->
                result = max(result, assertion.auditResult?.maxBallotIndexUsed ?: 0)
            }
        }
        return result
    }
}

data class ContestRound(val contestUA: ContestUnderAudit, val assertions: List<AssertionRound>, val roundIdx: Int,) {
    val id = contestUA.id
    val name = contestUA.name
    val Nc = contestUA.Nc

    var actualMvrs = 0 // Actual number of ballots with this contest contained in this round's sample.
    var actualNewMvrs = 0 // Actual number of new ballots with this contest contained in this round's sample.

    var estMvrs = 0 // Estimate of the sample size required to confirm the contest
    var estSampleSizeNoStyles = 0 // number of total samples estimated needed, uniformPolling (Polling, no style only)
    var done = false
    var included = true
    var status = TestH0Status.InProgress // or its own enum ??

    constructor(contestUA: ContestUnderAudit, roundIdx: Int) :
            this(contestUA, contestUA.assertions().map{ AssertionRound(it, roundIdx, null) }, roundIdx)

    fun minAssertion(): AssertionRound {
        val assertionUA = contestUA.minAssertion()
        return assertions.find { it.assertion == assertionUA }!!
    }

    fun createNextRound() : ContestRound {
        val next =  assertions.filter { !it.status.complete }.map{
            AssertionRound(it.assertion, roundIdx + 1, it.auditResult)
        }
        return ContestRound(contestUA, next, roundIdx + 1)
    }
}

data class AssertionRound(val assertion: Assertion, val roundIdx: Int, var prevAuditResult: AuditRoundResult?) {
    // these values are set during estimateSampleSizes()
    var estSampleSize = 0   // estimated sample size for current round
    var estimationResult: EstimationRoundResult? = null

    // these values are set during runAudit()
    var auditResult: AuditRoundResult? = null
    var status = TestH0Status.InProgress
    var round = 0           // round when set to proved or disproved
}

data class EstimationRoundResult(
    val roundIdx: Int,
    val strategy: String,
    val fuzzPct: Double,
    val startingTestStatistic: Double,
    val startingRates: ClcaErrorRates? = null, // apriori error rates (clca only)
    val estimatedDistribution: List<Int>,   // distribution of estimated sample size; currently deciles
) {
    override fun toString() = "round=$roundIdx estimatedDistribution=$estimatedDistribution fuzzPct=$fuzzPct " +
            " startingRates=$startingRates"
}

data class AuditRoundResult(
    val roundIdx: Int,
    val estSampleSize: Int,   // estimated sample size
    val maxBallotIndexUsed: Int,  // maximum ballot index (for multicontest audits)
    val pvalue: Double,       // last pvalue when testH0 terminates
    val samplesNeeded: Int,   // first sample when pvalue < riskLimit
    val samplesUsed: Int,     // sample count when testH0 terminates
    val status: TestH0Status, // testH0 status
    val measuredMean: Double, // measured population mean
    val startingRates: ClcaErrorRates? = null, // apriori error rates (clca only)
    val measuredRates: ClcaErrorRates? = null, // measured error rates (clca only)
) {
    override fun toString() = "round=$roundIdx estSampleSize=$estSampleSize maxBallotIndexUsed=$maxBallotIndexUsed " +
            " pvalue=$pvalue samplesNeeded=$samplesNeeded samplesUsed=$samplesUsed status=$status"
}
