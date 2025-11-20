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
// generalize AdaptiveBetting for any assorter

class GeneralAdaptiveBetting(
    val N: Int, // max number of cards for this contest
    val noerror: Double, // a priori estimate of the error rates
    val withoutReplacement: Boolean = true,
    val d: Int,  // trunc weight
    val lowLimit: Double = .00001    // TODO I think we picked this number out of a hat.
) : BettingFn {
    var called = 0
    var lastBet = 0.0

    override fun bet(prevSamples: SampleTracker): Double {
        /* val lastj = valueTracker.numberOfSamples() // we know this is > 0 ??
        val p2oest = if (p2o < 0.0 || lastj == 0) 0.0 else estimateRate(d, p2o, prevSamples.countP2o().toDouble() / lastj, lastj, eps)
        val p1oest = if (p1o < 0.0 || lastj == 0) 0.0 else estimateRate(d, p1o, prevSamples.countP1o().toDouble() / lastj, lastj, eps)
        val p1uest = if (p1u < 0.0 || lastj == 0) 0.0 else estimateRate(d, p1u, prevSamples.countP1u().toDouble() / lastj, lastj, eps)
        val p2uest = if (p2u < 0.0 || lastj == 0) 0.0 else estimateRate(d, p2u, prevSamples.countP2u().toDouble() / lastj, lastj, eps)
         */

        called++
        val valueTracker =  prevSamples as ClcaErrorTracker
        val mui = populationMeanIfH0(N, withoutReplacement, valueTracker)
        val kelly = GeneralOptimalLambda(noerror=noerror, valueTracker, mui, d, lowLimit)
        val bet = kelly.solve()
        //if (lastBet != 0.0 && bet < lastBet)
        //    println("lastBet=$lastBet bet=$bet")
        lastBet = bet
        return bet
    }

    // For k ∈ {1, 2} we set a value d_k ≥ 0, capturing the degree of shrinkage to the a priori estimate p̃_k ,
    // and a truncation factor eps_k ≥ 0, enforcing a lower bound on the estimated rate.
    // Let p̂_ki be the sample rates at time i, e.g., p̂_2i = Sum(1{Xj = 0})/i , j=1..i
    // Then the shrink-trunc estimate is:
    //   p_̃ki := (d_k * p̃_k + i * p̂_k(i−1)) / (d_k + i − 1) ∨ epsk  ; COBRA eq (4)
    fun estimateRate(d: Int, apriori: Double, sampleRate: Double, sampleNum: Int, eps: Double): Double {
        //   (d_k * p̃_k + i * p̂_k(i−1)) / (d_k + i − 1) ∨ epsk  ; COBRA eq (4)
        val est = (d * apriori + sampleNum * sampleRate) / (d + sampleNum - 1)
        val lower =  max(est, eps) // lower bound on the estimated rate
        return min(1.0, lower) // upper bound on the estimated rate
    }
}

class GeneralOptimalLambda(val noerror: Double, val valueTracker: ClcaErrorTracker, val mui: Double, val d: Int, val lowLimit: Double) {
    val debug = false

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

        // Optimize the function within the given range [start, end] TODO check correct
        val start = 0.0
        val end = 2.0
        val result: UnivariatePointValuePair = optimizer.optimize(
            UnivariateObjectiveFunction(function),
            SearchInterval(start, end),
            GoalType.MAXIMIZE,
            MaxEval(1000)
        )
        if (debug) println( "Kelly: valueTracker=${valueTracker} point=${result.point}")
        return result.point
    }

    fun expectedValueLogt(lam: Double): Double {
        // TODO should not be called for first bet, N = 0
        val N = valueTracker.numberOfSamples() // TODO n = 0 or maybe "warm up" or trunc_estimate ??
        var sumLn = 0.0

        if (N == 0) {
            sumLn = ln(1.0 + lam * (noerror - mui))
            return sumLn
        }

        sumLn = ln(1.0 + lam * (noerror - mui)) * valueTracker.noerrorCount / N.toDouble()

        // from BettingMart: val ttj = 1.0 + lamj * (xj - mj) // (1 + λi (Xi − µi )) ALPHA eq 10, SmithRamdas eq 34 (WoR)
        // measured error rate is count/N
        valueTracker.valueCounter.forEach { (sampleValue: Double, count: Int) ->
            if (count > 0) {
                // “shrink-trunc” estimator with apriori = 0.0 for other values
                // TODO the estimateRate once for one solve(), could cache if needed
                sumLn += ln(1.0 + lam * (sampleValue - mui)) * estimateRate(d, 0.0, count, N, lowLimit)
            }
        }

        return sumLn
    }

    fun estimateRate(d: Int, apriori: Double, sampleCount: Int, sampleNum: Int, lowLimit: Double): Double {
        //   (d_k * p̃_k + i * p̂_k(i−1)) / (d_k + i − 1) ∨ epsk  ; COBRA eq (4)
        val est = (d * apriori + sampleCount) / (d + sampleNum - 1)
        val boundedBelow =  max(est, lowLimit) // lower bound on the estimated rate
        val boundedAbove =  min(1.0, boundedBelow) // upper bound on the estimated rate
        return boundedAbove
    }
}

//     fun estimateRate(d: Int, apriori: Double, sampleRate: Double, sampleNum: Int, eps: Double): Double {
//        //   (d_k * p̃_k + i * p̂_k(i−1)) / (d_k + i − 1) ∨ epsk  ; COBRA eq (4)
//        val est = (d * apriori + sampleNum * sampleRate) / (d + sampleNum - 1)
//        val lower =  max(est, eps) // lower bound on the estimated rate
//        return min(1.0, lower) // upper bound on the estimated rate
//    }
