package org.cryptobiotic.rlauxe.comparison

import org.cryptobiotic.rlauxe.unittest.ComparisonWithErrors
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.makeStandardComparisonAssorter
import org.cryptobiotic.rlauxe.sampling.RunTestRepeatedResult
import org.cryptobiotic.rlauxe.sampling.runTestRepeated
import org.cryptobiotic.rlauxe.sampling.Sampler
import org.cryptobiotic.rlauxe.concur.RepeatedTask
import org.cryptobiotic.rlauxe.util.mean2margin
import kotlin.math.max
import kotlin.test.assertEquals

// CANDIDATE FOR REMOVAL

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
    val compareAssorter = makeStandardComparisonAssorter(cvrMean, N)
    val theta = cvrMean + cvrMeanDiff
    var eta0: Double = 0.0

    init {
        require( N == cvrs.size)
    }

    override fun makeSampler(): Sampler {
        return ComparisonWithErrors(cvrs, compareAssorter, theta)
    }

    override fun makeTestFn(): RiskTestingFn {
        val (margin, noerrors, upperBound) = comparisonAssorterCalc(cvrMean, 1.0)
        assertEquals(margin, compareAssorter.assorter().reportedMargin(), doublePrecision)
        assertEquals(noerrors, compareAssorter.noerror, doublePrecision)
        assertEquals(upperBound, compareAssorter.upperBound, doublePrecision)

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

    // override fun maxSamples(): Int  = N
    override fun name(): String = "AlphaComparisonTask$idx"
    override fun N(): Int  = N
    override fun reportedMean() = cvrMean
    override fun reportedMeanDiff() = cvrMeanDiff
}

fun comparisonAssorterCalc(assortAvgValue:Double, assortUpperBound: Double): Triple<Double, Double, Double> {
    val margin = 2.0 * assortAvgValue - 1.0 // reported assorter margin
    val noerror = 1.0 / (2.0 - margin / assortUpperBound)  // assort value when there's no error
    val upperBound = 2.0 * noerror  // maximum assort value
    return Triple(margin, noerror, upperBound)
}

// run AlphaMart with TrunkShrinkage in repeated trials
// this creates the riskTestingFn for you
fun runAlphaMartRepeated(
    drawSample: Sampler,
    // maxSamples: Int,
    eta0: Double,
    d: Int = 500,
    withoutReplacement: Boolean = true,
    ntrials: Int = 1,
    upperBound: Double = 1.0,
    showDetails: Boolean = false,
    estimFn: EstimFn? = null, // if not supplied, use TruncShrinkage
): RunTestRepeatedResult {

    val t = 0.5
    val minsd = 1.0e-6
    val c = max(eps, ((eta0 - t) / 2))

    val useEstimFn = estimFn ?: TruncShrinkage(drawSample.maxSamples(), true, upperBound = upperBound, minsd = minsd, d = d, eta0 = eta0, c = c)

    val alpha = AlphaMart(
        estimFn = useEstimFn,
        N = drawSample.maxSamples(),
        upperBound = upperBound,
        withoutReplacement = withoutReplacement,
    )

    return runTestRepeated(
        drawSample = drawSample,
        // maxSamples = maxSamples,
        terminateOnNullReject = true,
        ntrials = ntrials,
        testFn = alpha,
        testParameters = mapOf("eta0" to eta0, "d" to d.toDouble()),
        showDetails = showDetails,
        margin = mean2margin(eta0),
        Nc=drawSample.maxSamples(), // TODO ??
    )
}