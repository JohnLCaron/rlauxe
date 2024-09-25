package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.makeStandardComparisonAssorter
import org.cryptobiotic.rlauxe.shangrla.eps
import kotlin.math.max
import kotlin.test.Test

class TestAlphaMartComparison {

    @Test
    fun testComparisonSingle() {
        val N = 10000
        val m = N
        val cvrMean = .509
        val cvrs = makeCvrsByExactMean(N, cvrMean)

        val compareAssorter = makeStandardComparisonAssorter(cvrMean)

        val sampler = ComparisonNoErrors(cvrs, compareAssorter)
        val assorterMean = sampler.sampleMean()

        val eta0 = 20.0
        val d = 100

        println("N=$N cvrMean=$cvrMean assorterMean=$assorterMean eta0=$eta0, d=$d u=${compareAssorter.upperBound}")

        val result = doOneAlphaMartRun(sampler, m, eta0 = eta0, d = d, u = compareAssorter.upperBound)
        println(result)
    }

    @Test
    fun testComparisonUpper() {
        val N = 10000
        val cvrMean = .52
        val cvrs = makeCvrsByExactMean(N, cvrMean)

        val compareAssorter = makeStandardComparisonAssorter(cvrMean)

        val sampler = ComparisonNoErrors(cvrs, compareAssorter)
        val theta = sampler.sampleMean()

        val eta0 = 1.0 * theta
        val d = 100

        println("N=$N cvrMean=$cvrMean theta=$theta eta0=$eta0, d=$d compareAssorter.upperBound=${compareAssorter.upperBound}")

        val result = doOneAlphaMartRun(sampler, N, eta0 = eta0, d = d, u = compareAssorter.upperBound)
        println("\n${result}")

        /*
        println("\nupper=1.0")
        sampler.reset()
        val result1 = doOneAlphaMartRun(sampler, N, eta0 = eta0, d = d, u = 1.0)
        println(result1)
        println("% improvement = ${100.0 * result.sampleCount/result1.sampleCount}")

         */
    }
}

fun doOneAlphaMartRun(
    drawSample: SampleFn,
    maxSamples: Int,
    eta0: Double,
    d: Int,
    u: Double,
    withoutReplacement: Boolean = true,
): TestH0Result {
    val N = drawSample.N()
    val t = 0.5
    val upperBound = u
    val minsd = 1.0e-6
    val c = max(eps, ((eta0 - t) / 2))

    val estimFn = TruncShrinkage(
        N = N, withoutReplacement = withoutReplacement, upperBound = upperBound,
        minsd = minsd, eta0 = eta0, c = c, d = d
    )

    val alpha = AlphaMart(
        estimFn = estimFn,
        N = N,
        upperBound = upperBound,
        withoutReplacement = withoutReplacement,
    )

    return alpha.testH0(maxSamples, terminateOnNullReject = true, showDetails = true) { drawSample.sample() }
}