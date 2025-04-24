package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.audit.Sampler
import org.cryptobiotic.rlauxe.util.makeContestsFromCvrs
import org.cryptobiotic.rlauxe.estimate.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.audit.makeClcaNoErrorSampler
import kotlin.test.Test
import kotlin.test.assertEquals

class TestAlphaMartComparison {

    @Test
    fun testAlphaMartComparison() {
        val N = 10000
        val cvrMean = .52
        val cvrs = makeCvrsByExactMean(N, cvrMean)
        val contest = makeContestsFromCvrs(cvrs).first()
        val contestUA = ContestUnderAudit(contest).makeClcaAssertions(cvrs)
        val compareAssorter = contestUA.clcaAssertions.first().cassorter
        val calcMargin = compareAssorter.calcClcaAssorterMargin(cvrs.zip(cvrs))

        val theta = compareAssorter.noerror()
        val expected = 1.0 / (3 - 2 * cvrMean)
        assertEquals(expected, theta, 3.0/N)

        val eta0 = theta
        val d = 100

        println("N=$N cvrMean=$cvrMean theta=$theta eta0=$eta0, d=$d compareAssorter.upperBound=${compareAssorter.upperBound()}")
        val sampler = makeClcaNoErrorSampler(contest.id, true, cvrs, compareAssorter)
        val result = doOneAlphaMartRun(sampler, N, eta0 = eta0, d = d, u = compareAssorter.upperBound())
        println("\n${result}")

        assertEquals(TestH0Status.StatRejectNull, result.status)
    }
}

fun doOneAlphaMartRun(
    drawSample: Sampler,
    maxSamples: Int,
    eta0: Double,
    d: Int,
    u: Double,
    withoutReplacement: Boolean = true,
): TestH0Result {
    val t = 0.5
    val upperBound = u

    val estimFn = TruncShrinkage(
        N = drawSample.maxSamples(), withoutReplacement = withoutReplacement, upperBound = upperBound,
        eta0 = eta0,
        d = d
    )

    val alpha = AlphaMart(
        estimFn = estimFn,
        N = drawSample.maxSamples(),
        upperBound = upperBound,
        withoutReplacement = withoutReplacement,
    )

    return alpha.testH0(maxSamples, terminateOnNullReject = true) { drawSample.sample() }
}