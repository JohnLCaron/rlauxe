package org.cryptobiotic.rlauxe.betting

import org.apache.commons.math3.analysis.UnivariateFunction
import org.apache.commons.math3.optim.MaxEval
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType
import org.apache.commons.math3.optim.univariate.BrentOptimizer
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction
import org.apache.commons.math3.optim.univariate.SearchInterval
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair
import org.cryptobiotic.rlauxe.core.SampleTracker
import org.cryptobiotic.rlauxe.oneaudit.OneAuditAssortValueRates
import org.cryptobiotic.rlauxe.util.df
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

// generalize AdaptiveBetting for any clca assorter, including OneAudit
// Kelly optimization of lambda parameter with estimated error rates

class GeneralAdaptiveBetting(
    val Npop: Int, // population size for this contest
    val startingErrors: ClcaErrorCounts,  // zero for auditing
    val nphantoms: Int, // number of phantoms in the population
    val oaAssortRates: OneAuditAssortValueRates?, // only for OneAudit
    val d: Int = 100,  // trunc weight
    val maxRisk: Double, // this bounds how close lam gets to 2.0
    val withoutReplacement: Boolean = true,
    val debug: Boolean = false,
) : BettingFn {
    private var lastBet = 0.0 // debugging

    fun startingErrorRates(): Map<Double, Double> {
        // estimated rates for each clca bassort value
        val scaled = if (oaAssortRates == null) 1.0 else (Npop - oaAssortRates.totalInPools) / Npop.toDouble()
        val sampleNumber = startingErrors.totalSamples
        val estRates = startingErrors.bassortValues().map { bassort ->
            val allCount = (startingErrors.errorCounts()[bassort] ?: 0)
            var rate = scaled * shrinkTruncEstimateRate(
                apriori = 0.0,
                errorCount = allCount,
                sampleNum = sampleNumber,
            )
            // rate of phantoms is the minimum "oth-los" rate
            if (startingErrors.isPhantom(bassort)) {
                rate = max( rate, nphantoms / Npop.toDouble())
            }
            Pair(bassort, rate)
        }.toMap()
        return estRates
    }

    override fun bet(prevSamples: SampleTracker): Double {
        val tracker = prevSamples as ClcaErrorTracker
        val trackerErrors = tracker.measuredClcaErrorCounts()

        // estimated rates for each clca bassort value
        val scaled = if (oaAssortRates == null) 1.0 else (Npop - oaAssortRates.totalInPools) / Npop.toDouble()
        val sampleNumber = (startingErrors.totalSamples) + tracker.numberOfSamples()
        val estRates = trackerErrors.errorCounts.map { (assort, errorCount) ->
            val allCount = errorCount + (startingErrors.errorCounts()[assort] ?: 0)
            var rate = scaled * shrinkTruncEstimateRate(
                apriori = 0.0,
                errorCount = allCount,
                sampleNum = sampleNumber,
            )
            // rate of phantoms is the minimum "oth-los" rate
            if (startingErrors.isPhantom(assort)) {
                rate = max( rate, nphantoms / Npop.toDouble())
            }
            Pair(assort, rate)
        }.toMap()

        val mui = populationMeanIfH0(Npop, withoutReplacement, tracker)

        //    tj is how much you win or lose
        //    tj = 1 + lamj * (xj - mj)
        //    tj = 1 - lamj * mj when x == 0 (smallest value x can be)
        //
        //    how much above 0 should it be?
        //    limit your bets so at most you lose maxRisk for any one bet:
        //
        //    tj > (1 - maxRisk)
        //    1 - lamj * mj > 1 - maxRisk   when x = 0
        //    lamj * mj < maxRisk
        //    lamj <  maxRisk / mj
        //    maxBet = maxRisk / mj

        val maxBet = maxRisk / mui
        val kelly = GeneralOptimalLambda(tracker.noerror, estRates, oaAssortRates?.rates, mui=mui, maxBet=maxBet, debug = debug)
        val bet = kelly.solve()

        lastBet = bet
        return bet
    }

    // For k ∈ {1, 2} we set a value d_k ≥ 0, capturing the degree of shrinkage to the a priori estimate p̃_k ,
    // and a truncation factor eps_k ≥ 0, enforcing a lower bound on the estimated rate.
    // Let p̂_ki be the sample rates at time i, e.g., p̂_2i = Sum(1{Xj = 0})/i , j=1..i
    // Then the shrink-trunc estimate is:
    //   p_̃ki := (d_k * p̃_k + i * p̂_k(i−1)) / (d_k + i − 1) ∨ epsk  ; COBRA eq (4)

    // ease the first d samples in slowly
    fun shrinkTruncEstimateRate(
        apriori: Double,
        errorCount: Int,
        sampleNum: Int,
    ): Double {
        if (sampleNum == 0) return 0.0
        if (errorCount == 0) return 0.0 // experiment
        val used = min(d, 1)
        val est = (used * apriori + errorCount) / (used + sampleNum - 1)
        return est
    }
}

class GeneralOptimalLambda(val noerror: Double, val clcaErrorRates: Map<Double, Double>, val oaErrorRates: Map<Double, Double>?,
                           val mui: Double, val maxBet: Double, val debug: Boolean=false) {
    val p0: Double

    init {
        require (mui > 0.0) {
            "mui=$mui"
        }
        require (maxBet > 0.0)

        val oasum = if (oaErrorRates == null) 0.0 else oaErrorRates.map{ it.value }.sum()
        val clcasum = clcaErrorRates.map{ it.value }.sum()
        p0 = 1.0 - clcasum - oasum    // calculate against other values to get it exact
        if (p0 < 0.0)
            print("")
        require (p0 >= 0.0)
        if (debug) {
            print("OneAuditOptimalLambda init: mui=$mui ")
            expectedValueLogt(1.0, true)
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

    fun expectedValueLogt(lam: Double, show: Boolean = false): Double {
        val noerrorTerm = ln(1.0 + lam * (noerror - mui)) * p0

        var sumClcaTerm = 0.0
        clcaErrorRates.forEach { (sampleValue: Double, rate: Double) ->
            sumClcaTerm += ln(1.0 + lam * (sampleValue - mui)) * rate
        }

        var sumOneAuditTerm = 0.0
        if (oaErrorRates != null) { // probably dont need the filter
            oaErrorRates.filter { it.value != 0.0 }.forEach { (sampleValue: Double, rate: Double) ->
                sumOneAuditTerm += ln(1.0 + lam * (sampleValue - mui)) * rate
            }
        }
        val total = noerrorTerm + sumClcaTerm + sumOneAuditTerm

        if (debug) println("  lam=$lam, noerrorTerm=${df(noerrorTerm)} sumClcaTerm=${df(sumClcaTerm)} " +
                "sumOneAuditTerm=${df(sumOneAuditTerm)} expectedValueLogt=${total} ")

        return total
    }
}


// Sum (1.0 + lam * (sampleValue - mui)) * rate)
// Sum(rate) + lam * Sum( (sampleValue_k - mui) * rate_k)
// 1 + lam * Sum( (sampleValue_k - mui) * rate_k)

// val tj = 1.0 + lamj * (xj - mj)
// Tj = Prod(tj)
// ln (Tj) = Sum(ln(tj)) = Sum( ln(1.0 + lamj * (xk - mj)) * prob(k))