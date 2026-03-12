package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.betting.ClcaErrorRates
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.betting.makeAprioriErrorRates
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.estimateSampleSizeSimple
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.util.Quantiles.percentiles
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.roundUp

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
    fun createNextRound(prevAuditRound: AuditRound?): AuditRound
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

    override fun toString() = show()

    override fun show() =
        "AuditState(round = $roundIdx, nmvrs=$nmvrs, auditWasDone=$auditWasDone, auditIsComplete=$auditIsComplete)" +
                " ncontests=${contestRounds.size} ncontestsDone=${contestRounds.count { it.done }}"

    override fun createNextRound(prevAuditRound: AuditRound?): AuditRound {
        val nextContests = contestRounds.filter { !it.status.complete }.map { contestRound ->
            val prevContestRound = prevAuditRound?.contestRounds?.find { it.id == contestRound.id }
            contestRound.createNextRound(prevContestRound)
        }
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

    fun createNextRound(prevContestRound: ContestRound?) : ContestRound {
        val nextAssertions =  assertionRounds.filter { !it.status.complete }.map{ assertionRound ->
            val assertion = assertionRound.assertion
            val prevAssertionRound = prevContestRound?.assertionRounds?.find { it.assertion.assorter.hashcodeDesc() == assertion.assorter.hashcodeDesc() }
            AssertionRound(assertion, roundIdx + 1, prevAssertionRound)
        }
        return ContestRound(contestUA, nextAssertions, roundIdx + 1)
    }

    fun minAssertion(): AssertionRound? {
        // cant use contestUA.minAssertion, since some of the assertions may have finished being audited, ie are not in assertionRounds
        if (assertionRounds.isEmpty()) return null
        if (contestUA.isClca) {
            val noerrors = assertionRounds.map { Pair(it, it.noerror) }
            val sortedNoerrors = noerrors.sortedBy { it.second }
            return sortedNoerrors.first().first
        } else {
            val margins = assertionRounds.map { Pair(it, it.assertion.assorter.dilutedMargin()) }
            val minMargin = margins.sortedBy { it.second }
            return minMargin.first().first
        }
    }

    fun countCvrsUsedInAudit(): Int {
        return assertionRounds.maxOfOrNull { it.auditResult?.samplesUsed ?: 0 } ?: 0
    }

    // called by viewer
    fun corlaCalc(alpha: Double): Int {
        val minAssertion = minAssertion()
        if (minAssertion == null) return 0
        val lastResult = minAssertion.auditResult ?: minAssertion.prevAuditResult
        if (lastResult == null) return 0

        val errorCounts = lastResult.measuredCounts

        // fun estimateSampleSizeSimple(
        //    riskLimit: Double,
        //    dilutedMargin: Double,
        //    gamma: Double = 1.03905,
        //    twoOver: Int = 0,
        //    oneOver: Int = 0,
        //    oneUnder: Int = 0,
        //    twoUnder: Int = 0,
        //)

        return estimateSampleSizeSimple(
            alpha,
            dilutedMargin = minAssertion.assertion.assorter.dilutedMargin(),
            twoOver = errorCounts?.getNamedCount("p2o") ?: 0,
            oneOver = errorCounts?.getNamedCount("p1o") ?: 0,
            oneUnder = errorCounts?.getNamedCount("p1u") ?: 0,
            twoUnder = errorCounts?.getNamedCount("p2u") ?: 0,
        )
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

    override fun toString(): String {
        return "ContestRound(roundIdx=$roundIdx, id=$id, estMvrs=$estMvrs, estNewMvrs=$estNewMvrs, status=$status)"
    }
}

data class AssertionRound(val assertion: Assertion, val roundIdx: Int, var prevAssertionRound: AssertionRound?) {
    val noerror = if (assertion is ClcaAssertion) assertion.cassorter.noerror() else 0.0
    val upper = assertion.upper
    val prevAuditResult = prevAssertionRound?.auditResult

    // these values are set during estimateSampleSizes()
    var estMvrs = 0   // estimated sample size for current round
    var estNewMvrs = 0   // estimated new sample size for current round
    var estimationResult: EstimationRoundResult? = null

    // these values are set during runAudit()
    var auditResult: AuditRoundResult? = null
    var status = TestH0Status.InProgress
    var roundProved = 0           // round when set to proved or disproved

    // we get the results from the audit, not the estimation
    fun previousErrorCounts(): ClcaErrorCounts? {
        require(assertion is ClcaAssertion)
        if (roundIdx == 1 || prevAuditResult == null || prevAuditResult.measuredCounts == null)
            return null

        val prevResult = prevAuditResult
        return ClcaErrorCounts(prevResult.measuredCounts.errorCounts, prevResult.samplesUsed, noerror, upper)
    }

    // return (calculated new mvrs needed, optimalBet) based on prevAuditResult.measuredCounts or apriori.errorCounts
    fun calcNewMvrsNeeded(contest: ContestWithAssertions, auditConfig : AuditConfig): Int {
        require(assertion is ClcaAssertion)
        val cassorter = assertion.cassorter

        // because we start from previous rounds, we are calculating new mvrs
        // payoff^n = Tprev
        // Tprev * Tnow = T = 1/risklimit
        // Tnow = T / Tprev = (1/risklimit) / (1/plast)
        // alpha_now = 1 / Tnow = = (1/plast) / (1/risklimit) = risklimit/plast
        var alpha = auditConfig.riskLimit
        if (this.prevAuditResult != null) {
            alpha /= this.prevAuditResult.plast
        }

        val aprioriRates = auditConfig.clcaConfig.apriori.makeErrorRates(noerror, upper)
        val ratesWithPhantoms =  makeAprioriErrorRates(aprioriRates, contest.Nphantoms/contest.Npop.toDouble())

        return if (cassorter is OneAuditClcaAssorter) {
            val clcaErrorRates =  ClcaErrorRates(noerror, upper, ratesWithPhantoms)
            val pair = assertion.cassorter.estWithOptimalBet2(contest, auditConfig.clcaConfig.maxLoss, alpha, clcaErrorRates)
            pair.first
        } else {
            val maxBet = 2 * auditConfig.clcaConfig.maxLoss // TODO ??
            cassorter.sampleSizeWithErrors(maxBet, alpha, ClcaErrorRates(noerror, upper, ratesWithPhantoms))
        }
    }

    override fun toString(): String {
        return "AssertionRound(roundIdx=$roundIdx, estMvrs=$estMvrs, estNewMvrs=$estNewMvrs, status=$status)"
    }
}

data class EstimationRoundResult(
    val roundIdx: Int,
    val strategy: String,
    val calcNewMvrsNeeded: Int, // could just calculate on the fly ?
    val startingTestStatistic: Double,
    val startingErrorRates: Map<Double, Double>? = null, // error rates used for estimation; informational
    val estimatedDistribution: List<Int>,   // distribution of estimated sample sizes
    val lastIndex: Int,
    val quantile: Int,
    val ntrials: Int,
    val simNewMvrsNeeded: Int,
    val simMvrsNeeded: Int = 0,
) {
    override fun toString() = "round=$roundIdx strategy=$strategy calcMvrsNeeded=$calcNewMvrsNeeded estimatedDistribution=$estimatedDistribution ($ntrials) " +
            "simNewMvrs=$simNewMvrsNeeded startingErrorRates=$startingErrorRates"

    fun startingErrorRates() = buildString {
        if (startingErrorRates == null) append("N/A") else {
            startingErrorRates.filter { it.value != 0.0 }.forEach { append("${df(it.key)}=${df(it.value)}, ") }
        }
    }

    fun deciles(): List<Int> {
        val decilePcts = IntArray(10) { 10 * (it+1) }
        val wtf: MutableMap<Int?, Double?> = percentiles().indexes(*decilePcts).compute(*estimatedDistribution.toIntArray())
        return wtf.values.map { roundUp(it!!) }
    }

}

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
