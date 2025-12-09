package org.cryptobiotic.rlauxe.core


import org.apache.commons.math3.analysis.UnivariateFunction
import org.apache.commons.math3.optim.MaxEval
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType
import org.apache.commons.math3.optim.univariate.BrentOptimizer
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction
import org.apache.commons.math3.optim.univariate.SearchInterval
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair
import org.cryptobiotic.rlauxe.oneaudit.OneAuditErrorRates
import org.cryptobiotic.rlauxe.util.df
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.ln
import kotlin.math.min

// generalize AdaptiveBetting for any clca assorter, including OneAudit
// Kelly optimization of lambda parameter with estimated error rates

private val showRates = false
private val showBets = false

// TODO can use for CLCA?
class GeneralAdaptiveBetting(
    val Npop: Int, // population size for this contest
    // val accumErrorCounts: ClcaErrorCounts, // propable illegal to do (cant use prior knowlege of the sample)
    val oaErrorRates: OneAuditErrorRates,
    val d: Int = 100,  // trunc weight
    val maxRisk: Double, // this bounds how close lam gets to 2.0; TODO study effects of
    val withoutReplacement: Boolean = true,
    val debug: Boolean = false,
) : BettingFn {
    // debugging
    private var lastBet = 0.0
    private var prevErrors: ClcaErrorCounts? = null

    override fun bet(prevSamples: SampleTracker): Double {
        val tracker = prevSamples as ClcaErrorTracker
        val trackerErrors = tracker.measuredErrorCounts()
        prevErrors = trackerErrors

        // estimated rates for each clca bassort value
        val scaled = (Npop - oaErrorRates.totalInPools) / Npop.toDouble()
        val sampleNumber = tracker.numberOfSamples()
        val estRates = trackerErrors.errorCounts.mapValues {
            scaled * shrinkTruncEstimateRate(
                    apriori = 0.0,
                    errorCount = it.value,
                    sampleNum = sampleNumber,
                )
            }

        if (showRates) {
            val p0 = tracker.noerrorCount / tracker.numberOfSamples().toDouble()
            println("  gRates = ${estRates.toSortedMap()} scaled=$scaled nsamples=${tracker.numberOfSamples()}")
        }

        val mui = populationMeanIfH0(Npop, withoutReplacement, tracker)
        val kelly = OneAuditOptimalLambda(tracker.noerror, estRates, oaErrorRates.rates, mui, debug = debug)

        // limit the bet to the maximum risk we are willing to take
        val bet = min(kelly.solve(), 2*maxRisk)

        if (showBets && lastBet != 0.0 && bet < lastBet) {
            println("lastBet=$lastBet bet=$bet")
            // if (!showRates) println("    gRates = ${estRates.toSortedMap()} nsamples=${tracker.numberOfSamples()}")
        }

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
        val est = (d * apriori + errorCount) / (d + sampleNum - 1)
        // we have a limit on the bet, so I think we dont need to do it here
        //val boundedBelow = max(est, minRate) // lower bound on the estimated rate
        //val boundedAbove = min(1.0, boundedBelow) // upper bound on the estimated rate
        return est
    }
}

class OneAuditOptimalLambda(val noerror: Double, val clcaErrorRates: Map<Double, Double>, val oaErrorRates: Map<Double, Double>, val mui: Double, val debug: Boolean=false) {
    val p0: Double

    init {
        p0 = 1.0 - clcaErrorRates.map{ it.value }.sum() - oaErrorRates.map{ it.value }.sum()
        require (p0 >= 0.0)
        if (debug) {
            print("OneAuditOptimalLambda init: ")
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
        val end = 2.0
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

        //if (nsamples == 0) {
        //    return ln(1.0 + lam * (noerror - mui))
        //}

        val noerrorTerm = ln(1.0 + lam * (noerror - mui)) * p0

        var sumClcaTerm = 0.0
        clcaErrorRates.filter { it.value != 0.0 }.forEach { (sampleValue: Double, rate: Double) ->
            sumClcaTerm += ln(1.0 + lam * (sampleValue - mui)) * rate
        }

        var sumOneAuditTerm = 0.0
        oaErrorRates.filter { it.value != 0.0 }.forEach { (sampleValue: Double, rate: Double) ->
            sumOneAuditTerm += ln(1.0 + lam * (sampleValue - mui)) * rate
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