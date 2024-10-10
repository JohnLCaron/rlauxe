package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.makeStandardComparisonAssorter
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals

class TestAlphaMartComparison {

    @Test
    fun testComparisonUpper() {
        val N = 10000
        val cvrMean = .52
        val cvrs = makeCvrsByExactMean(N, cvrMean)

        val compareAssorter = makeStandardComparisonAssorter(cvrMean)

        val sampler = ComparisonNoErrors(cvrs, compareAssorter)
        val theta = sampler.sampleMean()
        val expected = 1.0 / (3 - 2 * cvrMean)
        assertEquals(expected, theta, doublePrecision)

        val eta0 = theta
        val d = 100

        println("N=$N cvrMean=$cvrMean theta=$theta eta0=$eta0, d=$d compareAssorter.upperBound=${compareAssorter.upperBound}")

        val result = doOneAlphaMartRun(sampler, N, eta0 = eta0, d = d, u = compareAssorter.upperBound)
        println("\n${result}")

        assertEquals(TestH0Status.StatRejectNull, result.status)
    }

    @Test
    fun testComparisonAccelerated() {
        val N = 10000
        val m = N
        val cvrMean = .509
        val cvrs = makeCvrsByExactMean(N, cvrMean)

        val compareAssorter = makeStandardComparisonAssorter(cvrMean)
        val sampler = ComparisonNoErrors(cvrs, compareAssorter)
        val assorterMean = sampler.sampleMean()
        val expected = 1.0 / (3 - 2 * cvrMean)
        assertEquals(expected, assorterMean, doublePrecision)

        val factor = 1.9
        val d = 100

        println("N=$N cvrMean=$cvrMean assorterMean=$assorterMean factor=$factor, d=$d u=${compareAssorter.upperBound}")

        val resultAcc = doOneAlphaMartAcc(sampler, m, eta0 = assorterMean, d = d, u = compareAssorter.upperBound, accFactor = factor)
        println("doOneAlphaMartAcc: ${resultAcc}")
        assertEquals(TestH0Status.StatRejectNull, resultAcc.status)
    }

    @Test
    fun testTrunkVsAccelerated() {
        val N = 10000
        val m = N
        val cvrMean = .509
        val cvrs = makeCvrsByExactMean(N, cvrMean)

        val compareAssorter = makeStandardComparisonAssorter(cvrMean)
        val sampler = ComparisonNoErrors(cvrs, compareAssorter)
        val assorterMean = sampler.sampleMean()
        val expected = 1.0 / (3 - 2 * cvrMean)
        assertEquals(expected, assorterMean, doublePrecision)

        val factor = 1.2
        val d = 100

        println("N=$N cvrMean=$cvrMean assorterMean=$assorterMean factor=$factor, d=$d u=${compareAssorter.upperBound}")

        val result = doOneAlphaMartRun(sampler, m, eta0 = factor * assorterMean, d = d, u = compareAssorter.upperBound)
        println("doOneAlphaMartRun: ${result}\n")
        assertEquals(TestH0Status.StatRejectNull, result.status)

        sampler.reset()
        val resultAcc = doOneAlphaMartAcc(sampler, m, eta0 = assorterMean, d = d, u = compareAssorter.upperBound, accFactor = factor)
        println("doOneAlphaMartAcc: ${resultAcc}")
        assertEquals(TestH0Status.StatRejectNull, resultAcc.status)
        assertTrue( resultAcc.sampleCount < result.sampleCount)

        resultAcc.etajs.forEachIndexed{ idx, etajAcc ->
            val etaj = result.etajs[idx]
            // println("etaj=$etaj etajAcc=$etajAcc ratio=${etajAcc/etaj}")
            assertTrue( etajAcc >= etaj)
        }
    }
}

fun doOneAlphaMartRun(
    drawSample: SampleFn,
    maxSamples: Int,
    eta0: Double,
    d: Int,
    u: Double,
    withoutReplacement: Boolean = true,
    showDetails: Boolean = true,
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

    return alpha.testH0(maxSamples, terminateOnNullReject = true, showDetails = showDetails) { drawSample.sample() }
}

fun doOneAlphaMartAcc(
        drawSample: SampleFn,
        maxSamples: Int,
        eta0: Double,
        d: Int,
        u: Double,
        withoutReplacement: Boolean = true,
        showDetails: Boolean = true,
        accFactor: Double,
    ): TestH0Result {

    val N = drawSample.N()
    val t = 0.5
    val upperBound = u
    val c = max(eps, ((eta0 - t) / 2))

    val estimFn = TruncShrinkageAccelerated(
        N = N,
        withoutReplacement = withoutReplacement,
        upperBound = upperBound,
        eta0 = eta0,
        cp = c,
        d = d,
        accFactor = accFactor,
    )

    val alpha = AlphaMart(
        estimFn = estimFn,
        N = N,
        upperBound = upperBound,
        withoutReplacement = withoutReplacement,
    )

    return alpha.testH0(maxSamples, terminateOnNullReject = true, showDetails = showDetails) { drawSample.sample() }
}