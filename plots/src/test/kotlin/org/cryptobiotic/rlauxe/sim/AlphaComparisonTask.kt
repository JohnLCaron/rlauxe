package org.cryptobiotic.rlauxe.sim

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.makeStandardComparisonAssorter
import kotlin.math.max

data class AlphaComparisonTask(
    val idx: Int,
    val N: Int,
    val cvrMean: Double,
    val cvrMeanDiff: Double,
    val eta0Factor: Double,
    val d: Int, // parameter for shrinkTruncate
    val cvrs: List<Cvr>,
    val withoutReplacement: Boolean = true,
    val estimFn: EstimFn? = null, // if not supplied, use TruncShrinkage
): RepeatedTask {
    val compareAssorter = makeStandardComparisonAssorter(cvrMean)
    val theta = cvrMean + cvrMeanDiff
    var eta0: Double = 0.0

    init {
        require( N == cvrs.size)
    }

    override fun makeSampler(): SampleFn {
        return ComparisonWithErrors(cvrs, compareAssorter, theta)
    }

    override fun makeTestFn(): RiskTestingFn {
        val (_, noerrors, upperBound) = comparisonAssorterCalc(cvrMean, compareAssorter.upperBound)
        eta0 = eta0Factor * noerrors
        val t = 0.5
        val minsd = 1.0e-6
        val c = max(eps, ((eta0 - t) / 2))

        val useEstimFn = estimFn ?: TruncShrinkage(
            N, withoutReplacement = withoutReplacement, upperBound = upperBound,
            minsd = minsd, d = d, eta0 = eta0, c = c
        )

        return AlphaMart(
            estimFn = useEstimFn,
            N = N,
            upperBound = upperBound,
            withoutReplacement = withoutReplacement,
        )
    }

    override fun makeTestParameters(): Map<String, Double> {
        return mapOf("eta0" to eta0, "d" to d.toDouble())
    }

    override fun maxSamples(): Int  = N
    override fun name(): String = "AlphaComparisonTask$idx"
    override fun N(): Int  = N
    override fun reportedMean() = cvrMean
    override fun reportedMeanDiff() = cvrMeanDiff
}