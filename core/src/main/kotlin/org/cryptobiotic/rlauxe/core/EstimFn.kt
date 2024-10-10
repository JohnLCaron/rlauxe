package org.cryptobiotic.rlauxe.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

val eps = 2.220446049250313e-16

fun populationMeanIfH0(N: Int, withoutReplacement: Boolean, prevSamples: Samples): Double {
    val sampleNum = prevSamples.numberOfSamples()
    return if ((sampleNum == 0) || !withoutReplacement) 0.5 else (N * 0.5 - prevSamples.sum()) / (N - sampleNum)
}

// estimate the population mean for the jth sample from the previous j-1 samples
interface EstimFn {
    fun eta(prevSamples: Samples): Double
}

enum class TestH0Status(val fail: Boolean) {
    StatRejectNull(false), // statistical rejection of H0
    LimitReached(true), // cant tell from the number of samples allowed
    //// only when sampling without replacement all the way to N, in practice, this never happens I think
    SampleSum(false), // SampleSum > N * t, so we know H0 is false
    AcceptNull(true), // SampleSum + (all remaining ballots == 1) < N * t, so we know that H0 is true.
}

data class TestH0Result(
    val status: TestH0Status,  // how did the test conclude?
    val sampleCount: Int,   // number of samples needed to decide (or maximum allowed)
    val sampleMean: Double, // average of the assort values in the sample
    val pvalues: List<Double>,  // set of pvalues (only need for testing)
    val etajs: List<Double>,  // ni
    val mujs: List<Double>,  // mi
) {
    override fun toString() = buildString {
        append("TestH0Result status=$status")
        append("  sampleCount=$sampleCount")
        append("  sampleMean=$sampleMean")
    }
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
//        if (eta0 < 0.5 || eta0 > upperBound) {
//            println("wtf")
//        }
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

// modified version of TruncShrinkage, putting the accFactor on the entire terms, not just eta0
// doesnt work
class TruncShrinkageAccelerated(
    val N: Int,
    val withoutReplacement: Boolean = true,
    val upperBound: Double,
    val eta0: Double,
    cp: Double? = null,
    val d: Int,
    val accFactor: Double = 1.0,
) : EstimFn {
    val c = cp ?: max(eps, ((eta0 - .5) / 2))
    val capAbove = upperBound * (1 - eps)

    init {
        require(upperBound > 0.0)
        require(eta0 < upperBound) // ?? otherwise the math in alphamart gets wierd
        require(eta0 >= 0.5)
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

        val orgEst = (d * eta0 + sampleSum) / dj1
        val est = accFactor * orgEst

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

/**
 * @param rateError2 a float representing hypothesized rate of two-vote overstatements
 */
class OptimalBettingScheme(
    val upperBound: Double,
    val rateError2:Double = 0.0001 // aka p2
) : EstimFn {
    init {
        require(upperBound > 0.0)
    }

    /**
     * The value of eta corresponding to the "bet" that is optimal for ballot-level comparison audits,
     * for which overstatement assorters take a small number of possible values and are concentrated
     * on a single value when the CVRs have no errors.
     *
     * Let p0 be the rate of error-free CVRs, p1=0 the rate of 1-vote overstatements,
     * and p2= 1-p0-p1 = 1-p0 the rate of 2-vote overstatements. Then
     *
     * eta = (1-u*p0)/(2-2*u) + u*p0 - 1/2, where p0 is the rate of error-free CVRs.
     *
     * Translating to p2=1-p0 gives:
     *
     * eta = (1-u*(1-p2))/(2-2*u) + u*(1-p2) - 1/2.
     *
     * @param x an array of input data
     * @param rateError2 a float representing hypothesized rate of two-vote overstatements
     * @return eta, the estimated alternative mean to use in alpha
     */
    override fun eta(prevSamples: Samples) : Double {
        return (1 - this.upperBound * (1 - rateError2)) / (2 - 2 * this.upperBound) + this.upperBound * (1 - rateError2) - 0.5
    }
}

