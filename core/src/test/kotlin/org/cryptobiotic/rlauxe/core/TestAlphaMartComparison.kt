package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.sampling.ComparisonNoErrors
import org.cryptobiotic.rlauxe.sampling.SampleGenerator
import org.cryptobiotic.rlauxe.util.makeContestsFromCvrs
import org.cryptobiotic.rlauxe.util.makeCvrsByExactMean
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals

class TestAlphaMartComparison {

    @Test
    fun testComparisonUpper() {
        val N = 10000
        val cvrMean = .52
        val cvrs = makeCvrsByExactMean(N, cvrMean)
        val contest = makeContestsFromCvrs(cvrs).first()
        val contestUA = ContestUnderAudit(contest).makeComparisonAssertions(cvrs, contest.votes)
        val compareAssorter = contestUA.comparisonAssertions.first().cassorter

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
}

fun doOneAlphaMartRun(
    drawSample: SampleGenerator,
    maxSamples: Int,
    eta0: Double,
    d: Int,
    u: Double,
    withoutReplacement: Boolean = true,
    showDetails: Boolean = true,
): TestH0Result {
    val t = 0.5
    val upperBound = u
    val minsd = 1.0e-6
    val c = max(eps, ((eta0 - t) / 2))

    val estimFn = TruncShrinkage(
        N = drawSample.maxSamples(), withoutReplacement = withoutReplacement, upperBound = upperBound,
        minsd = minsd, eta0 = eta0, c = c, d = d
    )

    val alpha = AlphaMart(
        estimFn = estimFn,
        N = drawSample.maxSamples(),
        upperBound = upperBound,
        withoutReplacement = withoutReplacement,
    )

    return alpha.testH0(maxSamples, terminateOnNullReject = true, showDetails = showDetails) { drawSample.sample() }
}