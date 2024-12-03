package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.Welford
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

const val eps = 2.220446049250313e-16

// estimate the population mean for the jth sample from the previous j-1 samples
interface EstimFn {
    fun eta(prevSamples: Samples): Double
}

class TruncShrinkage(
    val N: Int,
    val withoutReplacement: Boolean = true,
    val upperBound: Double,
    val minsd: Double, // only used if f > 0
    val eta0: Double,
    val c: Double,
    val d: Int,
    val f: Double = 0.0,
) : EstimFn {
    val capAbove = upperBound * (1 - eps)
    val wterm = d * eta0  // eta0 given weight of d. eta is weighted average of eta0 and samples

    init {
        require(upperBound > 0.0)
//        require(eta0 < upperBound) // ?? otherwise the math in alphamart gets wierd
        if (eta0 < 0.5) {
            println("eta0 < 0.5")
        }
//         require(eta0 >= 0.5) // ??
        require(c > 0.0)
        require(d >= 0)
    }

    val welford = Welford()

    // estimate population mean from previous samples
    override fun eta(prevSamples: Samples): Double {
        val lastj = prevSamples.numberOfSamples()
        val dj1 = (d + lastj).toDouble()

        val sampleSum = if (lastj == 0) 0.0 else {
            welford.update(prevSamples.last())
            prevSamples.sum()
        }

        // (2.5.2, eq 14, "truncated shrinkage")
        // weighted = ((d * eta + S) / (d + j - 1) + u * f / sdj) / (1 + f / sdj)
        // val est = ((d * eta0 + sampleSum) / dj1 + upperBound * f / sdj3) / (1 + f / sdj3)
        val est = if (f == 0.0) (d * eta0 + sampleSum) / dj1 else {
            // note stdev not used if f = 0
            val (_, variance, _) = welford.result()
            val stdev = sqrt(variance) // stddev of sample
            val sdj3 = if (lastj < 2) 1.0 else max(stdev, minsd) // LOOK
            ((d * eta0 + sampleSum) / dj1 + upperBound * f / sdj3) / (1 + f / sdj3)
        }

        // Choosing epsi . To allow the estimated winner’s share ηi to approach √ µi as the sample grows
        // (if the sample mean approaches µi or less), we shall take epsi := c/ sqrt(d + i − 1) for a nonnegative constant c,
        // for instance c = (η0 − µ)/2.
        val mean = populationMeanIfH0(N, withoutReplacement, prevSamples)
        val e_j = c / sqrt(dj1)
        val capBelow = mean + e_j

        // println("est = $est sampleSum=$sampleSum d=$d eta0=$eta0 dj1=$dj1 lastj = $lastj, capBelow=${capBelow}(${est < capBelow})")
        // println("  meanOld=$meanUnderNull mean = $mean e_j=$e_j capBelow=${capBelow}(${est < capBelow})")

        // The estimate ηi is thus the sample mean, shrunk towards η0 and truncated to the interval [µi + ǫi , upper),
        //    where ǫi → 0 as the sample size grows.
        //    return min(capAbove, max(est, capBelow)): capAbove > est > capAbove: u*(1-eps) > est > mu_j+e_j(c,j)
        val boundedEst = min(max(capBelow, est), capAbove)
        return boundedEst
    }
}

class FixedEstimFn(
    val eta0: Double,
) : EstimFn {
    override fun eta(prevSamples: Samples) = eta0
}


// wrapper around BettingMart; only use for polling
class AlphaMart(
    val estimFn : EstimFn,  // estimator of the population mean
    val N: Int,             // max number of cards for this contest
    val withoutReplacement: Boolean = true,
    val riskLimit: Double = 0.05, // α ∈ (0, 1)
    val upperBound: Double = 1.0,  // aka u
): RiskTestingFn {
    val betting: BettingMart

    init {
        val bettingFn = EstimAdapter(N, withoutReplacement, upperBound, estimFn)
        betting =  BettingMart(bettingFn, N, withoutReplacement, 0.0, riskLimit, upperBound)
    }

    override fun testH0(
        maxSample: Int,
        terminateOnNullReject: Boolean,
        showDetails: Boolean,
        startingTestStatistic: Double,
        drawSample: () -> Double,
    ): TestH0Result {
        return betting.testH0(maxSample, terminateOnNullReject, showDetails, startingTestStatistic, drawSample)
    }
}

/** Turn EstimFn into a BettingFn */
class EstimAdapter(
    val N: Int,
    val withoutReplacement: Boolean = true,
    val upperBound: Double,
    val estimFn : EstimFn,  // estimator of the population mean
): BettingFn {
    val etas = mutableListOf<Double>()
    val bets = mutableListOf<Double>()

    override fun bet(prevSamples: PrevSamplesWithRates): Double {
        // let bettingmart handle edge cases
        val mu = populationMeanIfH0(N, withoutReplacement, prevSamples)
        val eta = estimFn.eta(prevSamples)
        etas.add(eta)
        bets.add(etaToLam(eta, mu, upperBound))
        return etaToLam(eta, mu, upperBound)
    }
}
