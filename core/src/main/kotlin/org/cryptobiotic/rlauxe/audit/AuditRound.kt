package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.df
import kotlin.math.max

data class AuditRound(
    val roundIdx: Int,
    val contestRounds: List<ContestRound>,

    var auditWasDone: Boolean = false,
    var auditIsComplete: Boolean = false,
    var samplePrns: List<Long>, // card prns to sample for this round
    var nmvrs: Int = 0,
    var newmvrs: Int = 0,
    var auditorWantNewMvrs: Int = -1,
) {
    fun show() =
        "AuditState(round = $roundIdx, nmvrs=$nmvrs, auditWasDone=$auditWasDone, auditIsComplete=$auditIsComplete)" +
                " ncontests=${contestRounds.size} ncontestsDone=${contestRounds.count { it.done }}"

    fun createNextRound() : AuditRound {
        val nextContests = contestRounds.filter { !it.status.complete }.map{ it.createNextRound() }
        return AuditRound(roundIdx + 1, nextContests, samplePrns = emptyList())
    }

    //// called from viewer
    fun maxBallotsUsed(): Int {
        var result = 0
        contestRounds.forEach { contest ->
            contest.assertionRounds.forEach { assertion ->
                result = max(result, assertion.auditResult?.maxBallotIndexUsed ?: 0)
            }
        }
        return result
    }
}

fun List<AuditRound>.previousSamples(currentRoundIdx: Int): Set<Long> {
    val result = mutableSetOf<Long>()
    this.filter { it.roundIdx < currentRoundIdx }.forEach { auditRound ->
        // result.addAll(auditRound.samplePrns.map { it })
        result.addAll(auditRound.samplePrns)
    }
    return result.toSet()
}

data class ContestRound(val contestUA: ContestUnderAudit, val assertionRounds: List<AssertionRound>, val roundIdx: Int) {
    val id = contestUA.id
    val name = contestUA.name
    val Nc = contestUA.Nc

    var actualMvrs = 0 // Actual number of ballots with this contest contained in this round's sample.
    var actualNewMvrs = 0 // Actual number of new ballots with this contest contained in this round's sample.

    var estNewSamples = 0 // Estimate of the new sample size required to confirm the contest
    var estSampleSize = 0 // number of total samples estimated needed, consistentSampling
    var estSampleSizeNoStyles = 0 // number of total samples estimated needed, uniformSampling
    var auditorWantNewMvrs: Int = -1 // Auditor has set the new sample size for his audit round.

    var done = false
    var included = true
    var status = contestUA.preAuditStatus

    init {
        if (status.complete) {
            included = false
            done = true
        }
    }

    constructor(contestUA: ContestUnderAudit, roundIdx: Int) :
            this(contestUA, contestUA.assertions().map{ AssertionRound(it, roundIdx, null) }, roundIdx)

    fun wantSampleSize(prevCount: Int): Int {
        return if (auditorWantNewMvrs > 0) (auditorWantNewMvrs + prevCount)
                else if (contestUA.hasStyle) estSampleSize else estSampleSizeNoStyles
    }

    fun estSampleSizeEligibleForRemoval(): Int {
        return if (!included || auditorWantNewMvrs >= 0 ) 0 // auditor excluded or set explicitly, not eligible
               else if (contestUA.hasStyle) estSampleSize
               else estSampleSizeNoStyles
    }

    fun minAssertion(): AssertionRound? {
        return assertionRounds.minByOrNull { it.assertion.assorter.reportedMargin() }
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
        if (estNewSamples != other.estNewSamples) return false
        if (estSampleSize != other.estSampleSize) return false
        if (estSampleSizeNoStyles != other.estSampleSizeNoStyles) return false
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
        result = 31 * result + estNewSamples
        result = 31 * result + estSampleSize
        result = 31 * result + estSampleSizeNoStyles
        result = 31 * result + auditorWantNewMvrs
        result = 31 * result + done.hashCode()
        result = 31 * result + included.hashCode()
        result = 31 * result + contestUA.hashCode()
        result = 31 * result + assertionRounds.hashCode()
        result = 31 * result + status.hashCode()
        return result
    }

    fun equivalent(other: ContestRound): Boolean {
        if (!this.contestUA.equals(other.contestUA)) {
            return false
        }
        val assertionsZip = this.assertionRounds.zip(other.assertionRounds)
        assertionsZip.forEach { (myAssertionRound, otherAssertionRound) ->
            if (!myAssertionRound.equals(otherAssertionRound)) {
                println(myAssertionRound)
                println(otherAssertionRound)
                return false
            }
        }
        return true
    }
}

data class AssertionRound(val assertion: Assertion, val roundIdx: Int, var prevAuditResult: AuditRoundResult?) {
    // these values are set during estimateSampleSizes()
    var estSampleSize = 0   // estimated sample size for current round
    var estNewSampleSize = 0   // estimated new sample size for current round
    var estimationResult: EstimationRoundResult? = null

    // these values are set during runAudit()
    var auditResult: AuditRoundResult? = null
    var status = TestH0Status.InProgress
    var round = 0           // round when set to proved or disproved

    fun accumulatedErrorCounts(contestRound: ContestRound): ClcaErrorCounts {
        val (_, auditRoundResults) = contestRound.resultsForAssertion(assertion.assorter.hashcodeDesc())

        val sumOfCounts = mutableMapOf<Double, Int>()
        var totalSamples = 0
        auditRoundResults.forEach { auditRoundResult ->
            if (auditRoundResult.measuredCounts != null) {
                totalSamples += auditRoundResult.samplesUsed
                auditRoundResult.measuredCounts.forEach { (key, value) ->
                    val sum =  sumOfCounts.getOrPut(key) { 0 }
                    sumOfCounts[key] = sum + value
                }
            }
        }

        val noerror = (assertion as ClcaAssertion).cassorter.noerror()
        val upper = (assertion as ClcaAssertion).cassorter.upperBound()
        return ClcaErrorCounts(sumOfCounts, totalSamples, noerror, upper)
    }
}

data class EstimationRoundResult(
    val roundIdx: Int,
    val strategy: String,
    val fuzzPct: Double?,
    val startingTestStatistic: Double,
    val startingRates: Map<Double, Double>? = null, // error rates used for estimation
    val estimatedDistribution: List<Int>,   // distribution of estimated sample size; currently deciles
    val firstSample: Int,
) {
    override fun toString() = "round=$roundIdx estimatedDistribution=$estimatedDistribution fuzzPct=$fuzzPct " +
            " startingRates=$startingRates"

    fun startingRates() = buildString {
        startingRates?.toSortedMap()?.forEach { append( "${df(it.key)}=${df(it.value)}, " ) } ?: append("empty")
    }
}

data class AuditRoundResult(
    val roundIdx: Int,
    val nmvrs: Int,               // number of mvrs available for this contest for this round
    val maxBallotIndexUsed: Int,  // maximum ballot index (for multicontest audits)
    val pvalue: Double,       // last pvalue when testH0 terminates
    val samplesUsed: Int,     // sample count when testH0 terminates
    val status: TestH0Status, // testH0 status
    val measuredMean: Double, // measured population mean TODO used?
    val startingRates: Map<Double, Double>? = null, // starting error rates (clca only)
    val measuredCounts: Map<Double, Int>? = null, // measured error counts (clca only)
) {

    override fun toString() = buildString {
        append("round=$roundIdx pvalue=${df(pvalue)} nmvrs=$nmvrs samplesUsed=$samplesUsed status=$status")
        append(" startingRates=${startingRates()}")
        append(" measuredCounts=${measuredCounts()}")
    }

    fun measuredCounts() = buildString {
        measuredCounts?.toSortedMap()?.forEach { append( "${df(it.key)}=${it.value}, " ) } ?: append("empty")
    }

    fun startingRates() = buildString {
        startingRates?.toSortedMap()?.forEach { append( "${df(it.key)}=${df(it.value)}, " ) } ?: append("empty")
    }
}
