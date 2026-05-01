@file:OptIn(ExperimentalAtomicApi::class)

package org.cryptobiotic.rlauxe.betting

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.math3.analysis.UnivariateFunction
import org.apache.commons.math3.optim.MaxEval
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType
import org.apache.commons.math3.optim.univariate.BrentOptimizer
import org.apache.commons.math3.optim.univariate.SearchInterval
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair
import org.cryptobiotic.rlauxe.oneaudit.OneAuditAssortValueRates
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.doubleIsClose
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

// new design
//  first round: use apriori and nphantoms for initial rate estimate
//  estimation will jigger the tracker in gaBetting.bet(tracker) to continue on from previous round
//  audit always starts from beginning
//  bets will do shrinkTrunc of starting and measured.

data class GeneralAdaptiveBetting(
    val Npop: Int, // population size for this contest
    val aprioriErrorRates: ClcaErrorRates, // apriori rates not counting phantoms, non-null so we always have noerror and upper
    val nphantoms: Int, // number of phantoms in the population
    val maxLoss: Double, // between 0 and 1; this bounds how close lam can get to 2.0; maxBet = maxLoss / mui

    val oaAssortRates: OneAuditAssortValueRates?, // non-null for OneAudit
    val d: Int = 100,  // trunc weight
) : BettingFn {
    val noerror = aprioriErrorRates.noerror
    val upper = aprioriErrorRates.upper
    val taus = Taus(upper)
    val aprioriRates: Map<Double, Double>  // bassort -> rate

    var prevBet: Double = 0.0
    var prevRates: Map<Double, Double> = emptyMap()
    var avoidSeqCount = 0

    init {
        aprioriRates = makeAprioriErrorRates(aprioriErrorRates, nphantoms/Npop.toDouble())
    }

    fun estimatedErrorRates(trackerErrors: ClcaErrorCounts? = null): Map<Double, Double> { // bassort -> rate
        if (trackerErrors == null || trackerErrors.errorCounts.isEmpty()) return aprioriRates

        val errorRates = trackerErrors.errorRates()
        val scaled = if (oaAssortRates == null) 1.0 else (Npop - oaAssortRates.ncardsInPools) / Npop.toDouble()

        val estRates = taus.namesNoErrors().map { name ->
            val tauValue = taus.valueOf(name)!!
            val bassort = tauValue * noerror
            val aprioriRate = aprioriRates[bassort] ?: 0.0
            val rate = scaled * shrinkTruncEstimateRate(
                aprioriRate = aprioriRate,
                measuredRate = errorRates[bassort] ?: 0.0,
                sampleNum = trackerErrors.totalSamples, // TODO total samples increases, which would effect the rate
            )
            Pair(bassort, rate)
        }.toMap()

        return estRates
    }

    // ease the first d samples in gradually
    // changes from COBRA: 1) use maxBet instead of eps_k; k != 2, its 5 or 7 (see ClcaErrors.md).
    //  For k ∈ {1, 2} we set a value d_k ≥ 0, capturing the degree of shrinkage to the a priori estimate p̃_k ,
    //  and a truncation factor eps_k ≥ 0, enforcing a lower bound on the estimated rate.
    //  Let p̂_ki be the sample rates at time i, e.g., p̂_2i = Sum(1{Xj = 0})/i , j=1..i
    //  Then the shrink-trunc estimate is:
    //    p_̃ki := (d_k * p̃_k + i * p̂_k(i−1)) / (d_k + i − 1) ∨ epsk  ; COBRA eq (4)

    private fun shrinkTruncEstimateRate(
        aprioriRate: Double,
        measuredRate: Double,
        sampleNum: Int,
    ): Double {
        if (sampleNum == 0) return aprioriRate
        val d1 = max(1, d)
        val est = (d1 * aprioriRate + sampleNum * measuredRate) / (d1 + sampleNum - 1)
        return est
    }

    override fun bet(prevSamples: Tracker, show: Boolean): Double {
        val errorTracker = prevSamples as ErrorTracker
        val trackerErrors = errorTracker.measuredClcaErrorCounts()

        // TODO this assumes null mean = 1/2, and so is eta oblivious (?)
        // calling routine should check if mui < 0 or > assorter.upperBound() before calling bet()
        val mui = populationMeanIfH0(Npop, withoutReplacement=true, prevSamples)
        val maxBet = maxLoss / mui

        allCount.fetchAndAdd(1)

        val errorRates = trackerErrors.errorRates()
        val nonzeroRates = errorRates.filter { it.value > 0 }

        // detect when optimization will always return maxBet
        if (nonzeroRates.isEmpty() && oaAssortRates == null) return maxBet

        // for OneAudit we have trackerErrors.totalSamples changing. So only avoid for 10 samples at a time
        if (errorRates == prevRates && avoidSeqCount < 10) {
            avoidSeqCount++
            avoidCount.fetchAndAdd(1)
            return min(prevBet, maxBet) // comment out to run anyway and see how we would do...
        }
        avoidSeqCount = 0
        prevRates = errorRates

        val kelly = GeneralOptimalLambda(errorTracker.noerror(), nonzeroRates, oaAssortRates, mui=mui, maxBet=maxBet, debug = debug)
        val bet = kelly.solve()
        if (show && bet > 2.0) {
            println("bet $bet > 2; maxBet = $maxBet mui=$mui, nsamplesLeft=${Npop - prevSamples.numberOfSamples()}")
        }

        if (showAvoid) {
            // TODO how sensitive is payoff to small changes in the bet ??
            //   according ClcaErrors.md, to "Only p2o is strongly dependent on the choice of lamda"
            //   and BettingRiskFunctions "Betting Patoff" plot show that the payoff (sum) curve changes slowly
            if (doubleIsClose(bet, prevBet, .0001)) count0001.fetchAndAdd(1)
            else if (doubleIsClose(bet, prevBet, .001)) count001.fetchAndAdd(1)
            else if (doubleIsClose(bet, prevBet, .01)) count01.fetchAndAdd(1)
            else if (doubleIsClose(bet, prevBet, .05)) count05.fetchAndAdd(1)
        }

        prevBet = bet
        return bet
    }

    companion object {
        private val logger = KotlinLogging.logger("GeneralAdaptiveBetting")
        private val debug = false
        private val showAvoid = true

        // not clear we need AtomicInt; objects may be thread confined
        val avoidCount = AtomicInt(0)
        val allCount = AtomicInt(0)
        val count05 = AtomicInt(0)
        val count01 = AtomicInt(0)
        val count001 = AtomicInt(0)
        val count0001 = AtomicInt(0)

        fun showCounts(prefix: String = "") {
            val avoidPct = avoidCount.load() / allCount.load().toDouble()
            println("$prefix avoid = $avoidCount ($avoidPct %) betCounts = $count0001 $count001 $count01 $count05 ")
            logger.info{ "$prefix avoid = $avoidCount ($avoidPct %) betCounts = $count0001 $count001 $count01 $count05 " }
        }
    }
}

// apriori: apriori rates not counting phantoms
// phantomRate: nphantoms / Npop
// return full errorRates (all taus except noerrors)
fun makeAprioriErrorRates(apriori: ClcaErrorRates, phantomRate: Double): Map<Double, Double> { // bassort -> rate
    val errorsWithPhantoms = mutableMapOf<Double, Double>()

    val noerror = apriori.noerror
    val upper = apriori.upper
    val taus = Taus(upper)

    taus.namesNoErrors().forEach { name ->
        val tauValue = taus.valueOf(name)!!
        val bassort = tauValue * noerror
        val aprioriRate = apriori.getNamedRate(name) // may be null
        val phantom = if (apriori.isPhantom(bassort) && phantomRate > 0.0) phantomRate else 0.0
        errorsWithPhantoms[bassort] = (aprioriRate?: 0.0) + phantom
    }
    return errorsWithPhantoms
}

class GeneralOptimalLambda(val noerror: Double, val clcaErrorRates: Map<Double, Double>, val oaAssortRates: OneAuditAssortValueRates?,
                           val mui: Double, val maxBet: Double, val debug: Boolean=false) {
    var p0: Double

    init {
        require (mui > 0.0) {
            "mui=$mui must be > 0"
        }
        require (maxBet > 0.0)

        val clcasum = clcaErrorRates.map{ it.value }.sum()
        val oasum = oaAssortRates?.sumRates ?: 0.0
        p0 = 1.0 - clcasum - oasum    // calculate against other values to get it exact
        require (p0 >= 0.0) { "p0=$p0 less than 0" }
        if (debug) {
            println("GeneralOptimalLambda init: mui=$mui ")
            expectedValueLogt(maxBet, true)
        }
    }

    fun solve(): Double {
        val function = UnivariateFunction { lam -> expectedValueLogt(lam) }  // The function to be optimized

        // org.apache.commons.math3.optim.univariate.BrentOptimizer
        // BrentOptimizer: For a function defined on some interval (lo, hi),
        // this class finds an approximation x to the point at which the function attains its minimum.
        // It implements Richard Brent's algorithm (from his book "Algorithms for Minimization without Derivatives", p. 79)
        // for finding minima of real univariate functions.
        // This code is an adaptation, partly based on the Python code from SciPy (module "optimize.py" v0.5);
        // the original algorithm is also modified to use an initial guess provided by the user,
        // to ensure that the best point encountered is the one returned.
        // Also see https://en.wikipedia.org/wiki/Brent%27s_method
        val optimizer = BrentOptimizer(1e-6, 1e-6)

        // Optimize the function within the given range [start, end]
        val start = 0.0
        val end = maxBet
        val result: UnivariatePointValuePair = optimizer.optimize(
            UnivariateObjectiveFunction(function),
            SearchInterval(start, end),
            GoalType.MAXIMIZE,
            MaxEval(1000)
        )
        // if (debug) println("  gKelly: ${valueTracker.valueCounter} point=${result.point}")
        return result.point
    }

    // TODO using mui here, which noramlly depends on eta, the null mean
    fun expectedValueLogt(lam: Double, show: Boolean = false): Double {
        val noerrorTerm = ln(1.0 + lam * (noerror - mui)) * p0

        var sumClcaTerm = 0.0
        clcaErrorRates.forEach { (sampleValue: Double, rate: Double) ->
            sumClcaTerm += ln(1.0 + lam * (sampleValue - mui)) * rate
        }

        var sumOneAuditTerm = 0.0
        oaAssortRates?.rates?.forEach { (assortValue: Double, rate: Double) ->
            sumOneAuditTerm += ln(1.0 + lam * (assortValue - mui)) * rate
        }
        val total = noerrorTerm + sumClcaTerm + sumOneAuditTerm

        if (debug)
            println("  lam=$lam, noerrorTerm=${dfn(noerrorTerm, 6)} sumClcaTerm=${dfn(sumClcaTerm, 6)} " +
                    "sumOneAuditTerm=${dfn(sumOneAuditTerm, 6)} expectedValueLogt=${total} ")

        return total
    }
}

// Sum (1.0 + lam * (sampleValue - mui)) * rate)
// Sum(rate) + lam * Sum( (sampleValue_k - mui) * rate_k)
// 1 + lam * Sum( (sampleValue_k - mui) * rate_k)

// val tj = 1.0 + lamj * (xj - mj)
// Tj = Prod(tj)
// ln (Tj) = Sum(ln(tj)) = Sum( ln(1.0 + lamj * (xk - mj)) * prob(k))

