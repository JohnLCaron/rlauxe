package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.betting.ClcaErrorRates
import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.betting.makeAprioriErrorRates
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.util.Quantiles
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.roundUp

interface AuditRoundIF {
    val roundIdx: Int
    val contestRounds: List<ContestRound>

    var auditWasDone: Boolean
    var auditIsComplete: Boolean
    var samplePrns: List<Long> // card prns to sample for just this round (complete, not just new)
    var nmvrs: Int      // number of mvrs in round
    var newmvrs: Int    // number of new mvrs in round
    var mvrsUsed: Int
    var mvrsUnused: Int

    fun show(): String
    fun createNextRound(): AuditRound
}

 // Note: mutable
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
) : AuditRoundIF {

    override fun toString() = show()

    override fun show() =
        "AuditState(round = $roundIdx, nmvrs=$nmvrs, auditWasDone=$auditWasDone, auditIsComplete=$auditIsComplete)" +
                " ncontests=${contestRounds.size} ncontestsDone=${contestRounds.count { it.done }}"

    override fun createNextRound(): AuditRound {
        val nextContests = contestRounds.filter { !it.status.complete }.map { contestRound ->
            val prevContestRound = this.contestRounds.find { it.id == contestRound.id }
            contestRound.createNextRound(prevContestRound)
        }
        return AuditRound(roundIdx + 1, nextContests, samplePrns = emptyList())
    }
}

// called from AuditWorkflow.startNewRound() and rlauxe-viewer
// All the Prns that have been sampled so far
fun List<AuditRoundIF>.previousSamplePrns(currentRoundIdx: Int): Set<Long> {
    val result = mutableSetOf<Long>()
    this.filter { it.roundIdx < currentRoundIdx }.forEach { auditRound ->
        result.addAll(auditRound.samplePrns)
    }
    return result.toSet()
}

// Note: mutable
// TODO so what happens if we add new AssertionRound at round > 1 (Dhondt) ?
data class ContestRound(val contestUA: ContestWithAssertions, val assertionRounds: List<AssertionRound>, val roundIdx: Int) {
    val id = contestUA.id

    val name = contestUA.name
    val Npop = contestUA.Npop

    var maxSampleAllowed: Int? = null // maximum index in this round's sample that you are allowed to use; only estMvrs are guarenteed to be there.
    var estMvrs = 0 // Estimate of the mvrs required to confirm the contest
    var estNewMvrs = 0 // Estimate of the new mvrs required to confirm the contest

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

    fun maxSamplesUsed(): Int {
        return assertionRounds.maxOfOrNull { it.auditResult?.samplesUsed ?: 0 } ?: 0
    }

    /* called by viewer
    fun corlaCalc(alpha: Double): Int {
        val minAssertion = minAssertion()
        if (minAssertion == null) return 0
        val lastResult = minAssertion.auditResult ?: minAssertion.prevAuditResult
        if (lastResult == null) return 0

        val errorCounts = lastResult.clcaErrorTracker.measuredClcaErrorCounts()

        // fun estimateSampleSizeSimple(
        //    riskLimit: Double,
        //    dilutedMargin: Double,
        //    gamma: Double = 1.03905,
        //    twoOver: Int = 0,
        //    oneOver: Int = 0,
        //    oneUnder: Int = 0,
        //    twoUnder: Int = 0,
        //)

        return estimateCorla(
            alpha,
            dilutedMargin = minAssertion.assertion.assorter.dilutedMargin(),
            twoOver = errorCounts.getNamedCount("p2o") ?: 0,
            oneOver = errorCounts.getNamedCount("p1o") ?: 0,
            oneUnder = errorCounts.getNamedCount("p1u") ?: 0,
            twoUnder = errorCounts.getNamedCount("p2u") ?: 0,
        )
    } */

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContestRound

        if (roundIdx != other.roundIdx) return false
        if (estNewMvrs != other.estNewMvrs) return false
        if (estMvrs != other.estMvrs) return false
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

// Note: mutable
data class AssertionRound(val assertion: Assertion, val roundIdx: Int, var prevAssertionRound: AssertionRound?) {
    val noerror = if (assertion is ClcaAssertion) assertion.cassorter.noerror() else 0.0
    val upper = assertion.upper
    val prevAuditResult = prevAssertionRound?.auditResult

    // these values are set by EstimateAudit
    var estMvrs = 0   // estimated sample size for current round
    var estNewMvrs = 0   // estimated new sample size for current round
    var estimationResult: EstimationRoundResult? = null

    // these values are set during runAudit()
    var auditResult: AuditRoundResult? = null
    var status = TestH0Status.InProgress
    var roundProved = 0           // round when proved or disproved

    fun previousErrorTracker(): ClcaErrorTracker {
        require(assertion is ClcaAssertion)
        return prevAuditResult?.clcaErrorTracker?.copyAll() ?: ClcaErrorTracker(assertion.noerror, assertion.assorter.upperBound())
    }

    // same algorithm as GeneralAdaptiveBetting
    fun calcNewMvrsNeeded(contest: ContestWithAssertions, config : Config): Int {
        require(assertion is ClcaAssertion)
        val cassorter = assertion.cassorter
        val clcaConfig = config.round.clcaConfig!!

        // because we start from previous rounds, we are calculating new mvrs
        // payoff^n = Tprev
        // Tprev * Tnow = T = 1/risklimit
        // Tnow = T / Tprev = (1/risklimit) / (1/plast)
        // alpha_now = 1 / Tnow = = (1/plast) / (1/risklimit) = risklimit/plast
        var alpha = config.creation.riskLimit
        if (this.prevAuditResult != null) {
            alpha /= this.prevAuditResult.plast
        }

        val aprioriRates = clcaConfig.apriori.makeErrorRates(noerror, upper)
        val ratesWithPhantoms =  makeAprioriErrorRates(aprioriRates, contest.Nphantoms/contest.Npop.toDouble())

        return if (cassorter is OneAuditClcaAssorter) {
            val clcaErrorRates =  ClcaErrorRates(noerror, upper, ratesWithPhantoms)
            val pair = assertion.cassorter.estWithOptimalBet2(contest, clcaConfig.maxLoss, alpha, clcaErrorRates)
            pair.first
        } else {
            val maxBet = 2 * clcaConfig.maxLoss // TODO ??
            cassorter.sampleSizeWithErrors(maxBet, alpha, ClcaErrorRates(noerror, upper, ratesWithPhantoms))
        }
    }

    override fun toString(): String {
        return "AssertionRound(roundIdx=$roundIdx, estMvrs=$estMvrs, estNewMvrs=$estNewMvrs, status=$status)"
    }
}

// Note: immutable
data class EstimationRoundResult(
    val roundIdx: Int,
    val strategy: String,
    val calcNewMvrsNeeded: Int, // could just calculate on the fly ?
    val startingTestStatistic: Double,
    val startingErrorRates: Map<Double, Double>? = null, // error rates used for estimation; informational
    val estimatedDistribution: List<Int>,   // distribution of estimated sample sizes
    val lastIndex: Int,
    val percentile: Int,  // percentile of the distribution of estimate sample sample sizes
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

    // used by viewer
    fun deciles(): List<Int> {
        val decilePcts = IntArray(10) { 10 * (it+1) }
        val wtf: MutableMap<Int?, Double?> = Quantiles.percentiles().indexes(*decilePcts).compute(*estimatedDistribution.toIntArray())
        return wtf.values.map { roundUp(it!!) }
    }
}

// Note: immutable
data class AuditRoundResult(
    val roundIdx: Int,
    val nmvrs: Int,                 // total number of mvrs available for this contest for this round
    val plast: Double,              // last pvalue when testH0 terminates
    val pmin: Double,               // minimum pvalue reached
    val samplesUsed: Int,           // sample count when testH0 terminates
    val status: TestH0Status,       // testH0 status
    val clcaErrorTracker: ClcaErrorTracker?, // CLCA only; allows to start estimation from where we left off
    val params: Map<String, Double> = emptyMap(),  // TODO is this used ?
) {

    override fun toString() = buildString {
        append("round=$roundIdx pmin=${df(pmin)} plast=${df(plast)} nmvrs=$nmvrs samplesUsed=$samplesUsed status=$status")
        append(" measuredCounts=${measuredCounts()}")
    }

    fun measuredCounts() = buildString {
        when {
            clcaErrorTracker == null -> append("N/A")
            clcaErrorTracker.errorCounts.isEmpty() -> append("empty")
            else -> append(clcaErrorTracker.errorCounts.toString())
        }
    }

}
