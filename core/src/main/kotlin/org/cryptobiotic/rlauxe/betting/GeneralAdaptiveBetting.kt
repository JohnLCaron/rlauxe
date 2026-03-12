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
import kotlin.math.ln

private val logger = KotlinLogging.logger("GeneralAdaptiveBetting")

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
    var debug = false

    init {
        aprioriRates = makeAprioriErrorRates(aprioriErrorRates, nphantoms/Npop.toDouble())
    }

    fun estimatedErrorRates(trackerErrors: ClcaErrorCounts? = null): Map<Double, Double> { // bassort -> rate
        if (trackerErrors == null || trackerErrors.errorCounts.isEmpty()) return aprioriRates

        val errorRates = trackerErrors.errorRates()
        val scaled = if (oaAssortRates == null) 1.0 else (Npop - oaAssortRates.totalInPools) / Npop.toDouble()

        val estRates = taus.namesNoErrors().map { name ->
            val tauValue = taus.valueOf(name)!!
            val bassort = tauValue * noerror
            val aprioriRate = aprioriRates[bassort] ?: 0.0
            val rate = scaled * shrinkTruncEstimateRate2(
                aprioriRate = aprioriRate,
                measuredRate = errorRates[bassort] ?: 0.0,
                sampleNum = trackerErrors.totalSamples,
            )
            Pair(bassort, rate)
        }.toMap()
        return estRates
    }

    // ease the first d samples in gradually
    // For k ∈ {1, 2} we set a value d_k ≥ 0, capturing the degree of shrinkage to the a priori estimate p̃_k ,
    // and a truncation factor eps_k ≥ 0, enforcing a lower bound on the estimated rate.
    // Let p̂_ki be the sample rates at time i, e.g., p̂_2i = Sum(1{Xj = 0})/i , j=1..i
    // Then the shrink-trunc estimate is:
    //   p_̃ki := (d_k * p̃_k + i * p̂_k(i−1)) / (d_k + i − 1) ∨ epsk  ; COBRA eq (4)

    fun shrinkTruncEstimateRate2(
        aprioriRate: Double,
        measuredRate: Double,
        sampleNum: Int,
    ): Double {
        if (sampleNum == 0) return aprioriRate
        val d1 = if (d<1) 1 else d
        val est = (d1 * aprioriRate + sampleNum * measuredRate) / (d1 + sampleNum - 1)
        return est
    }

    override fun bet(prevSamples: Tracker): Double {
        val errorTracker = prevSamples as ErrorTracker
        val trackerErrors = errorTracker.measuredClcaErrorCounts()

        val estRates = estimatedErrorRates(trackerErrors)
        val mui = populationMeanIfH0(Npop, withoutReplacement=true, prevSamples)
        val maxBet = maxLoss / mui

        if (estRates.isEmpty()) return maxBet // TODO better

        val kelly = GeneralOptimalLambda(errorTracker.noerror(), estRates, oaAssortRates?.rates, mui=mui, maxBet=maxBet, debug = debug)
        val bet = kelly.solve()
        return bet
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

class GeneralOptimalLambda(val noerror: Double, val clcaErrorRates: Map<Double, Double>, val oaErrorRates: Map<Double, Double>?,
                           val mui: Double, val maxBet: Double, val debug: Boolean=false) {
    var p0: Double

    init {
        require (mui > 0.0) {
            "mui=$mui"
        }
        require (maxBet > 0.0)

        val oasum = if (oaErrorRates == null) 0.0 else oaErrorRates.map{ it.value }.sum()
        val clcasum = clcaErrorRates.map{ it.value }.sum()
        p0 = 1.0 - clcasum - oasum    // calculate against other values to get it exact
        if (p0 < 0.0) {
            // logger.warn{"p0 less than 0 = $p0"}
            p0 = 0.0
        }
        require (p0 >= 0.0)
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

    fun expectedValueLogt(lam: Double, show: Boolean = false): Double {
        val noerrorTerm = ln(1.0 + lam * (noerror - mui)) * p0

        var sumClcaTerm = 0.0
        clcaErrorRates.forEach { (sampleValue: Double, rate: Double) ->
            sumClcaTerm += ln(1.0 + lam * (sampleValue - mui)) * rate
        }

        var sumOneAuditTerm = 0.0
        if (oaErrorRates != null) {
            oaErrorRates.forEach { (assortValue: Double, rate: Double) ->
                sumOneAuditTerm += ln(1.0 + lam * (assortValue - mui)) * rate
            }
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

