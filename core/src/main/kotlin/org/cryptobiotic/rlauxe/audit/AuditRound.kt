package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.betting.TestH0Status
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
    var mvrsUsed: Int
    var mvrsUnused: Int

    fun show(): String
    fun createNextRound(): AuditRound
}

data class AuditRound(
    override val roundIdx: Int,
    override val contestRounds: List<ContestRound>,

    override var auditWasDone: Boolean = false,
    override var auditIsComplete: Boolean = false,
    override var samplePrns: List<Long>, // card prns to sample for this round (complete, not just new).
                                         // duplicates samplePrnsFile, so no need to serialze
    override var nmvrs: Int = 0,    // mvrs in the round
    override var newmvrs: Int = 0,  // new mvrs in the round
    override var mvrsUnused: Int = 0,
    override var mvrsUsed: Int = 0,
    override var auditorWantNewMvrs: Int = -1,
) : AuditRoundIF {

    override fun show() =
        "AuditState(round = $roundIdx, nmvrs=$nmvrs, auditWasDone=$auditWasDone, auditIsComplete=$auditIsComplete)" +
                " ncontests=${contestRounds.size} ncontestsDone=${contestRounds.count { it.done }}"

    override fun createNextRound(): AuditRound {
        val nextContests = contestRounds.filter { !it.status.complete }.map { it.createNextRound() }
        return AuditRound(roundIdx + 1, nextContests, samplePrns = emptyList())
    }
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

    var maxSampleAllowed: Int? = null // maximum index in the sample allowed to use
    var estMvrs = 0 // Estimate of the mvrs required to confirm the contest
    var estNewMvrs = 0 // Estimate of the new mvrs required to confirm the contest

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

    fun createNextRound() : ContestRound {
        val nextAssertions =  assertionRounds.filter { !it.status.complete }.map{
            AssertionRound(it.assertion, roundIdx + 1, it.auditResult)
        }
        return ContestRound(contestUA, nextAssertions, roundIdx + 1)
    }

    fun minAssertion(): AssertionRound? {
        // cant use contestUA.minAssertion, since some of the assertions may have finished being audit, ie are not in assertionRounds
        if (assertionRounds.isEmpty()) return null
        if (contestUA.isClca) {
            val margins = assertionRounds.map { Pair(it, it.noerror) }
            val minMargin = margins.sortedBy { it.second }
            return minMargin.first().first
        } else {
            val margins = assertionRounds.map { Pair(it, it.assertion.assorter.dilutedMargin()) }
            val minMargin = margins.sortedBy { it.second }
            return minMargin.first().first
        }
    }

    fun countCvrsUsedInAudit(): Int {
        return assertionRounds.maxOfOrNull { it.auditResult?.samplesUsed ?: 0 } ?: 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContestRound

        if (roundIdx != other.roundIdx) return false
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
    val noerror = if (assertion is ClcaAssertion) assertion.cassorter.noerror() else 0.0
    val upper = assertion.upper

    // these values are set during estimateSampleSizes()
    var estMvrs = 0   // estimated sample size for current round
    var estNewMvrs = 0   // estimated new sample size for current round
    var estimationResult: EstimationRoundResult? = null

    // these values are set during runAudit()
    var auditResult: AuditRoundResult? = null
    var status = TestH0Status.InProgress
    var roundProved = 0           // round when set to proved or disproved

    // we get the results from the audit, not the estimation
    fun previousErrorCounts(): ClcaErrorCounts {
        require(assertion is ClcaAssertion)
        if (roundIdx == 1 || prevAuditResult == null || prevAuditResult!!.measuredCounts == null)
            return ClcaErrorCounts.empty(noerror, upper)

        val prevResult = prevAuditResult!!
        return ClcaErrorCounts(prevResult.measuredCounts!!.errorCounts, prevResult.samplesUsed, noerror, upper)
    }

    // return (calculated new mvrs needed, optimalBet) based on prevAuditResult.measuredCounts or apriori.errorCounts
    // TODO  maxLoss: Double, riskLimit: Double, config always come from config
    fun calcNewMvrsNeeded(contest: ContestWithAssertions, maxLoss: Double, riskLimit: Double): Pair<Int, Double> {
        require(assertion is ClcaAssertion)

        // payoff^n = Tprev
        // Tprev * Tnow = T = 1/risklimit
        // Tnow = T / Tprev = (1/risklimit) / (1/plast)
        // alpha_now = 1 / Tnow = = (1/plast) / (1/risklimit) = risklimit/plast

        // because we start from previous rounds, we are calculating new mvrs
        var alpha = riskLimit
        if (this.prevAuditResult != null) {
            alpha /= this.prevAuditResult!!.plast
        }
        return assertion.cassorter.estWithOptimalBet2(contest, maxLoss, alpha, previousErrorCounts())
    }
}

data class EstimationRoundResult(
    val roundIdx: Int,
    val strategy: String,
    val calcNewMvrsNeeded: Int, // could just calculate on the fly ?
    val startingTestStatistic: Double,
    val startingErrorRates: Map<Double, Double>? = null, // error rates used for estimation
    val estimatedDistribution: List<Int>,   // distribution of estimated sample size as deciles
    val ntrials: Int,
    val simNewMvrsNeeded: Int,
) {
    override fun toString() = "round=$roundIdx strategy=$strategy calcMvrsNeeded=$calcNewMvrsNeeded estimatedDistribution=$estimatedDistribution ($ntrials) " +
            "simNewMvrs=$simNewMvrsNeeded startingErrorRates=$startingErrorRates"

    fun startingErrorRates() = buildString {
        if (startingErrorRates == null) append("N/A") else {
            startingErrorRates.filter { it.value != 0.0 }.forEach { append("${df(it.key)}=${df(it.value)}, ") }
        }
    }
}

fun roundUp(x: Double) = ceil(x).toInt()

data class AuditRoundResult(
    val roundIdx: Int,
    val nmvrs: Int,                 // number of mvrs available for this contest for this round
    val plast: Double,              // last pvalue when testH0 terminates
    val pmin: Double,               // minimum pvalue reached
    val samplesUsed: Int,           // sample count when testH0 terminates
    val status: TestH0Status,       // testH0 status
    val measuredCounts: ClcaErrorCounts? = null, // measured error counts (clca only)
    val params: Map<String, Double> = emptyMap(),
) {

    override fun toString() = buildString {
        append("round=$roundIdx pmin=${df(pmin)} plast=${df(plast)} nmvrs=$nmvrs samplesUsed=$samplesUsed status=$status")
        append(" measuredCounts=${measuredCounts()}")
    }

    fun measuredCounts() = buildString {
        if (measuredCounts == null) append("empty") else {
            append(measuredCounts.show())
        }
    }

}
