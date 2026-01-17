package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.df
import kotlin.math.ceil
import kotlin.math.max

interface AuditRoundIF {
    val roundIdx: Int
    val contestRounds: List<ContestRound>

    var auditWasDone: Boolean
    var auditIsComplete: Boolean
    var samplePrns: List<Long> // card prns to sample for this round (complete, not just new)
    var nmvrs: Int
    var newmvrs: Int
    var auditorWantNewMvrs: Int

    fun mvrsUsed(): Int
    fun mvrsExtra(): Int

    fun show(): String
    fun createNextRound(): AuditRoundIF
}

data class AuditRound(
    override val roundIdx: Int,
    override val contestRounds: List<ContestRound>,

    override var auditWasDone: Boolean = false,
    override var auditIsComplete: Boolean = false,
    override var samplePrns: List<Long>, // card prns to sample for this round (complete, not just new)
    override var nmvrs: Int = 0,
    override var newmvrs: Int = 0,
    override var auditorWantNewMvrs: Int = -1,
) : AuditRoundIF {
    override fun show() =
        "AuditState(round = $roundIdx, nmvrs=$nmvrs, auditWasDone=$auditWasDone, auditIsComplete=$auditIsComplete)" +
                " ncontests=${contestRounds.size} ncontestsDone=${contestRounds.count { it.done }}"

    override fun createNextRound(): AuditRoundIF {
        val nextContests = contestRounds.filter { !it.status.complete }.map { it.createNextRound() }
        return AuditRound(roundIdx + 1, nextContests, samplePrns = emptyList())
    }

    //// called from viewer
    override fun mvrsUsed(): Int {
        var result = 0
        contestRounds.forEach { contest ->
            contest.assertionRounds.forEach { assertion ->
                result = max(result, assertion.auditResult?.maxBallotIndexUsed ?: 0)
            }
        }
        return result
    }

    override fun mvrsExtra() = this.nmvrs - mvrsUsed()
}

// called from rlauxe-viewer
fun List<AuditRoundIF>.previousSamples(currentRoundIdx: Int): Set<Long> {
    val result = mutableSetOf<Long>()
    this.filter { it.roundIdx < currentRoundIdx }.forEach { auditRound ->
        result.addAll(auditRound.samplePrns)
    }
    return result.toSet()
}

data class ContestRound(val contestUA: ContestWithAssertions, val assertionRounds: List<AssertionRound>, val roundIdx: Int) {
    val id = contestUA.id

    val name = contestUA.name
    val Npop = contestUA.Npop

    var maxSampleIndex = 0 // maximum index in the sample allowed to use
    var estMvrs = 0 // Estimate of the mvrs required to confirm the contest
    var estNewMvrs = 0 // Estimate of the new mvrs required to confirm the contest

    var actualMvrs = 0    // Actual number of ballots with this contest contained in this round's sample.
    var actualNewMvrs = 0 // TODO CANDIDATE FOR REMOVAL Actual number of new ballots with this contest contained in this round's sample.

    var auditorWantNewMvrs: Int = -1 // Auditor has set the new sample size for this audit round. rlauxe-viewer

    var done = false
    var included = true
    var status = contestUA.preAuditStatus

    init {
        if (status.complete) {
            included = false
            done = true
        }
    }

    constructor(contestUA: ContestWithAssertions, roundIdx: Int) :
            this(contestUA, contestUA.assertions().map{ AssertionRound(it, roundIdx, null) }, roundIdx)

    fun wantSampleSize(prevCount: Int): Int {
        return if (auditorWantNewMvrs > 0) (auditorWantNewMvrs + prevCount)
                else estMvrs
    }

    fun estSampleSizeEligibleForRemoval(): Int {
        return if (!included || auditorWantNewMvrs >= 0 ) 0 // auditor excluded or set explicitly, not eligible
               else estMvrs
    }

    fun minAssertion(): AssertionRound? {
        val minAssertion = contestUA.minAssertion()!!
        return assertionRounds.find { it.assertion == minAssertion }
    }

    fun createNextRound() : ContestRound {
        val nextAssertions =  assertionRounds.filter { !it.status.complete }.map{
            AssertionRound(it.assertion, roundIdx + 1, it.auditResult)
        }
        return ContestRound(contestUA, nextAssertions, roundIdx + 1)
    }

    fun resultsForAssertion(assorterDesc: String): Pair<List<EstimationRoundResult>, List<AuditRoundResult>> {
        val estList = mutableListOf<EstimationRoundResult>()
        val auditList = mutableListOf<AuditRoundResult>()
        assertionRounds.filter { it.assertion.assorter.hashcodeDesc() == assorterDesc }
            .forEach { assertionRound ->
                if (assertionRound.estimationResult != null) estList.add(assertionRound.estimationResult!!)
                if (assertionRound.prevAuditResult != null) auditList.add(assertionRound.prevAuditResult!!)
            }

        return Pair(estList, auditList)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContestRound

        if (roundIdx != other.roundIdx) return false
        if (actualMvrs != other.actualMvrs) return false
        if (actualNewMvrs != other.actualNewMvrs) return false
        if (estNewMvrs != other.estNewMvrs) return false
        if (estMvrs != other.estMvrs) return false
        if (auditorWantNewMvrs != other.auditorWantNewMvrs) return false
        if (done != other.done) return false
        if (included != other.included) return false
        if (contestUA != other.contestUA) return false
        if (assertionRounds != other.assertionRounds) return false
        if (status != other.status) return false

        return true
    }

    override fun hashCode(): Int {
        var result = roundIdx
        result = 31 * result + actualMvrs
        result = 31 * result + actualNewMvrs
        result = 31 * result + estNewMvrs
        result = 31 * result + estMvrs
        result = 31 * result + auditorWantNewMvrs
        result = 31 * result + done.hashCode()
        result = 31 * result + included.hashCode()
        result = 31 * result + contestUA.hashCode()
        result = 31 * result + assertionRounds.hashCode()
        result = 31 * result + status.hashCode()
        return result
    }

}

data class AssertionRound(val assertion: Assertion, val roundIdx: Int, var prevAuditResult: AuditRoundResult?) {
    // these values are set during estimateSampleSizes()
    var estMvrs = 0   // estimated sample size for current round
    var estNewMvrs = 0   // estimated new sample size for current round
    var estimationResult: EstimationRoundResult? = null

    // these values are set during runAudit()
    var auditResult: AuditRoundResult? = null
    var status = TestH0Status.InProgress
    var roundProved = 0           // round when set to proved or disproved

    fun accumulatedErrorCounts(contestRound: ContestRound): ClcaErrorCounts {
        val (_, auditRoundResults) = contestRound.resultsForAssertion(assertion.assorter.hashcodeDesc())

        val sumOfCounts = mutableMapOf<Double, Int>()
        var totalSamples = 0
        auditRoundResults.forEach { auditRoundResult ->
            if (auditRoundResult.measuredCounts != null) {
                totalSamples += auditRoundResult.samplesUsed
                auditRoundResult.measuredCounts.errorCounts.forEach { (key, value) ->
                    val sum =  sumOfCounts.getOrPut(key) { 0 }
                    sumOfCounts[key] = sum + value
                }
            }
        }

        val noerror = (assertion as ClcaAssertion).cassorter.noerror()
        val upper = assertion.cassorter.assorter.upperBound()
        return ClcaErrorCounts(sumOfCounts, totalSamples, noerror, upper)
    }
}

data class EstimationRoundResult(
    val roundIdx: Int,
    val strategy: String,
    val fuzzPct: Double?,
    val startingTestStatistic: Double,
    val startingErrorRates: Map<Double, Double>? = null, // error rates used for estimation
    val estimatedDistribution: List<Int>,   // distribution of estimated sample size; currently deciles
    val firstSample: Int,
) {
    var estNewMvrs: Int = 0

    override fun toString() = "round=$roundIdx estimatedDistribution=$estimatedDistribution fuzzPct=$fuzzPct " +
            " estNewMvrs=$estNewMvrs startingErrorRates=$startingErrorRates"

    fun startingErrorRates() = buildString {
        if (startingErrorRates == null) return "N/A"
        startingErrorRates.filter{ it.value != 0.0 }.forEach { append( "${df(it.key)}=${df(it.value)}, " ) }
    }
}

fun roundUp(x: Double) = ceil(x).toInt()


data class AuditRoundResult(
    val roundIdx: Int,
    val nmvrs: Int,               // number of mvrs available for this contest for this round
    val maxBallotIndexUsed: Int,  // maximum ballot index (for multicontest audits)
    val pvalue: Double,       // last pvalue when testH0 terminates
    val samplesUsed: Int,     // sample count when testH0 terminates
    val status: TestH0Status, // testH0 status
    // val startingRates: Map<Double, Double>? = null, // cant use prevResults, so just phantoms, not really needed.
    val measuredCounts: ClcaErrorCounts? = null, // measured error counts (clca only)
    val params: Map<String, Double> = emptyMap(),
) {

    override fun toString() = buildString {
        append("round=$roundIdx pvalue=${df(pvalue)} nmvrs=$nmvrs samplesUsed=$samplesUsed status=$status")
        append(" measuredCounts=${measuredCounts()}")
    }

    fun measuredCounts() = buildString {
        if (measuredCounts == null) append("empty") else {
            append(measuredCounts.show())
        }
    }

    /*
    fun startingErrorRates() = buildString {
        if (startingRates == null) return "N/A"
        startingRates.filter{ it.value != 0.0 }.forEach { append( "${df(it.key)}=${df(it.value)}, " ) }
    } */

}
