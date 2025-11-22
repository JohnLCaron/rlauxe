package org.cryptobiotic.rlauxe.core


import org.apache.commons.math3.analysis.UnivariateFunction
import org.apache.commons.math3.optim.MaxEval
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType
import org.apache.commons.math3.optim.univariate.BrentOptimizer
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction
import org.apache.commons.math3.optim.univariate.SearchInterval
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

// use Kelly optimization of lambda parameter for the BettingFn
// generalize AdaptiveBetting for any clca assorter

private val showRates = false
private val showBets = false

// TODO what about OneAudit?? given pool sizes and avgs, could optimize....
class GeneralAdaptiveBetting(
    val N: Int, // population size for this contest
    val noerror: Double, // value of bassort when theres no error
    val upper: Double, // primitive assorter upper bound, is always > 1/2
    val d: Int = 100,  // trunc weight
    val minRate: Double = .00001, // this bounds how close lam gets to 2.0; might be worth playing with
    val withoutReplacement: Boolean = true,
) : BettingFn {
    var called = 0
    var lastBet = 0.0
    val bassortValues: List<Double>

    init {
        // [2, 1+1/2u, 2-1/2u,  1, 1-1/2u, 1/2u, 0] * noerror (l==0) (we will assume this)
        val u12 = 1.0 / (2 * upper)
        val taus = listOf(0.0, u12, 1 - u12, 2 - u12, 1 + u12, 2.0)
        bassortValues = taus.map { it * noerror }.toSet().toList().sorted()
        // println("  bassortValues = ${bassortValues}")
    }

    override fun bet(prevSamples: SampleTracker): Double {
        val valueTracker = prevSamples as ClcaErrorTracker

        // estimated rates for each bassort value, minimum rate is minRate
        val estRates = if (valueTracker.numberOfSamples() == 0) emptyMap() else {
            bassortValues.associate { bassort ->
                bassort to estimateRate(valueTracker.numberOfSamples(), d, 0.0,
                    valueTracker.valueCounter[bassort] ?: 0)
            }
        }
        val p0p = 1.0 - estRates.map{ it.value }.sum()
        if (showRates) {
            val p0 = valueTracker.noerrorCount / valueTracker.numberOfSamples().toDouble()
            println("  gRates = ${estRates.toSortedMap()} p0=$p0 p0p = $p0p nsamples=${valueTracker.numberOfSamples()}")
        }

        called++
        val mui = populationMeanIfH0(N, withoutReplacement, valueTracker)
        val kelly = GeneralOptimalLambda(noerror = noerror, valueTracker, mui, estRates)
        val bet = kelly.solve()
        if (showBets && lastBet != 0.0 && bet < lastBet) {
            println("gggggeneral lastBet=$lastBet bet=$bet")
            if (!showRates) println("    gRates = ${estRates.toSortedMap()} nsamples=${valueTracker.numberOfSamples()}")
        }
        lastBet = bet
        return bet
    }

    // For k ∈ {1, 2} we set a value d_k ≥ 0, capturing the degree of shrinkage to the a priori estimate p̃_k ,
    // and a truncation factor eps_k ≥ 0, enforcing a lower bound on the estimated rate.
    // Let p̂_ki be the sample rates at time i, e.g., p̂_2i = Sum(1{Xj = 0})/i , j=1..i
    // Then the shrink-trunc estimate is:
    //   p_̃ki := (d_k * p̃_k + i * p̂_k(i−1)) / (d_k + i − 1) ∨ epsk  ; COBRA eq (4)

    fun estimateRate(
        sampleNum: Int,
        d: Int,
        apriori: Double,
        sampleCount: Int,
    ): Double {
        // “shrink-trunc” estimator; measured error rate is sampleCount/N
        //   (d_k * p̃_k + i * p̂_k(i−1)) / (d_k + i − 1) ∨ minRate  ; COBRA eq (4)
        val est = (d * apriori + sampleCount) / (d + sampleNum - 1)
        val boundedBelow = max(est, minRate) // lower bound on the estimated rate
        val boundedAbove = min(1.0, boundedBelow) // upper bound on the estimated rate
        return boundedAbove
    }

    class GeneralOptimalLambda(val noerror: Double, val valueTracker: ClcaErrorTracker, val mui: Double, val estRates: Map<Double, Double>) {
        val debug = false
        val p0 = 1.0 - estRates.map{ it.value }.sum()

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

            // Optimize the function within the given range [start, end] TODO check range is correct
            val start = 0.0
            val end = 2.0
            val result: UnivariatePointValuePair = optimizer.optimize(
                UnivariateObjectiveFunction(function),
                SearchInterval(start, end),
                GoalType.MAXIMIZE,
                MaxEval(1000)
            )
            if (debug) println("  gKelly: ${valueTracker.valueCounter} point=${result.point}")
            return result.point
        }

        fun expectedValueLogt(lam: Double): Double {
            val nsamples = valueTracker.numberOfSamples()

            if (nsamples == 0) {
                return ln(1.0 + lam * (noerror - mui))
            }

            var sumLn = ln(1.0 + lam * (noerror - mui)) * p0 //  valueTracker.noerrorCount / nsamples.toDouble()

            // from BettingMart: val ttj = 1.0 + lamj * (xj - mj) // (1 + λi (Xi − µi )) ALPHA eq 10, SmithRamdas eq 34 (without replacement)
            estRates.forEach { (sampleValue: Double, rate: Double) ->
               sumLn += ln(1.0 + lam * (sampleValue - mui)) * rate
            }
            return sumLn
        }
    }
}