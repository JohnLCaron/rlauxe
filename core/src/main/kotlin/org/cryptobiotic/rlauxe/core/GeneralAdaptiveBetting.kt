package org.cryptobiotic.rlauxe.core


import org.apache.commons.math3.analysis.UnivariateFunction
import org.apache.commons.math3.optim.MaxEval
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType
import org.apache.commons.math3.optim.univariate.BrentOptimizer
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction
import org.apache.commons.math3.optim.univariate.SearchInterval
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair
import org.cryptobiotic.rlauxe.util.df
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

// generalize AdaptiveBetting for any clca assorter
// Kelly optimization of lambda parameter with estimated error rates

private val showRates = false
private val showBets = false
private val showCounts = false

// TODO what about OneAudit?? given pool sizes and avgs, could optimize....
class GeneralAdaptiveBetting(
    val N: Int, // population size for this contest
    val startingErrorRates: ClcaErrorCounts, // note, not apriori
    val d: Int = 100,  // trunc weight
    val minRate: Double = .00001, // this bounds how close lam gets to 2.0; might be worth playing with
    val withoutReplacement: Boolean = true,
    val poolAvg: Double? = null,
) : BettingFn {
    private var called = 0
    private var lastBet = 0.0
    private var prevErrors: ClcaErrorCounts? = null
    private var showFunction: Boolean = false

    override fun bet(prevSamples: SampleTracker): Double {
        val tracker = prevSamples as ClcaErrorTracker
        val trackerErrors = tracker.measuredCounts()
        showFunction = showCounts && (prevErrors != null) && trackerErrors.changedFrom(prevErrors!!)
        prevErrors = trackerErrors

        if (showCounts) {
            println("measuredCounts= ${trackerErrors.showShort(poolAvg)}")
            println("bassort= ${trackerErrors.bassortValues(poolAvg)}")
        }

        // estimated rates for each bassort value; minimum rate is minRate
        val sampleNumber = startingErrorRates.totalSamples + tracker.numberOfSamples()
        val estRates =
            trackerErrors.bassortValues(poolAvg).associate { bassort -> // TODO could get in trouble over non-exact floating point
                val est = estimateRate(apriori=0.0,
                    errorCount=(tracker.valueCounter[bassort] ?: 0) + (startingErrorRates.errorCounts()[bassort] ?: 0),
                    sampleNum=sampleNumber,
                )
                bassort to est
            }

        val p0p = 1.0 - estRates.map{ it.value }.sum()
        if (showRates) {
            val p0 = tracker.noerrorCount / tracker.numberOfSamples().toDouble()
            println("  gRates = ${estRates.toSortedMap()} p0=$p0 p0p = $p0p nsamples=${tracker.numberOfSamples()}")
        }

        called++
        val mui = populationMeanIfH0(N, withoutReplacement, tracker)
        val kelly = GeneralOptimalLambda(noerror = startingErrorRates.noerror, tracker, mui, estRates)
        val bet = kelly.solve()

        if (showBets && lastBet != 0.0 && bet < lastBet) {
            println("lastBet=$lastBet bet=$bet")
            // if (!showRates) println("    gRates = ${estRates.toSortedMap()} nsamples=${tracker.numberOfSamples()}")
        }

        if (showCounts) {
            println("  bet=$bet")
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
    fun estimateRate(
        apriori: Double,
        errorCount: Int,
        sampleNum: Int,
    ): Double {
        if (sampleNum == 0) return minRate
        if (errorCount == 0) return 0.0 // experiment
        val est = (d * apriori + errorCount) / (d + sampleNum - 1)
        val boundedBelow = max(est, minRate) // lower bound on the estimated rate
        val boundedAbove = min(1.0, boundedBelow) // upper bound on the estimated rate
        return boundedAbove
    }

    inner class GeneralOptimalLambda(val noerror: Double, val valueTracker: ClcaErrorTracker, val mui: Double, val estRates: Map<Double, Double>) {
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

            if (showFunction) print("   for lam=$lam, noerror=${df(sumLn)} rate=${df(p0)}")

            // from BettingMart: val ttj = 1.0 + lamj * (xj - mj) // (1 + λi (Xi − µi )) ALPHA eq 10, SmithRamdas eq 34 (without replacement)
            estRates.filter { it.value != 0.0 }.forEach { (sampleValue: Double, rate: Double) ->
                val ln = ln(1.0 + lam * (sampleValue - mui)) * rate
                if (showFunction) print(", ${df(sampleValue)}=$ln rate=${df(rate)}")
                sumLn += ln
            }

            if (showFunction) {
                println(", func=$sumLn")
            }

            return sumLn
        }
    }
}