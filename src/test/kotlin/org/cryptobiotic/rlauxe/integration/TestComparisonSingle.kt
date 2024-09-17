package org.cryptobiotic.rlauxe.integration

import org.cryptobiotic.rlauxe.core.AlphaMart
import org.cryptobiotic.rlauxe.core.AuditContest
import org.cryptobiotic.rlauxe.core.ComparisonNoErrors
import org.cryptobiotic.rlauxe.core.SampleFn
import org.cryptobiotic.rlauxe.core.TestH0Result
import org.cryptobiotic.rlauxe.core.TruncShrinkage
import org.cryptobiotic.rlauxe.core.makeComparisonAudit
import org.cryptobiotic.rlauxe.core.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.shangrla.eps
import kotlin.collections.first
import kotlin.math.max
import kotlin.test.Test

class TestComparisonSingle {

    @Test
    fun testComparisonSingle() {
        val N = 10000
        val m = N
        val theta = .509
        val cvrs = makeCvrsByExactMean(N, theta)

        val contest = AuditContest("contest0", 0, listOf(0, 1), listOf(0))
        val compareAudit = makeComparisonAudit(contests = listOf(contest), cvrs = cvrs)
        val compareAssertion = compareAudit.assertions[contest]!!.first()

        val sampler = ComparisonNoErrors(cvrs, compareAssertion.assorter)
        val assorterMean = sampler.truePopulationMean()

        val eta0 = 20.0
        val d = 100

        println("N=$N theta=$theta assorterMean=$assorterMean eta0=$eta0, d=$d u=${compareAssertion.assorter.upperBound}")

        val result = doOne(sampler, m, eta0=eta0, d=d, u=compareAssertion.assorter.upperBound)
        println(result)
    }

    fun doOne(
        drawSample: SampleFn,
        maxSamples: Int,
        eta0: Double,
        d: Int = 500,
        withoutReplacement: Boolean = true,
        u: Double = 1.0,
    ): TestH0Result {
        val N = drawSample.N()
        val t = 0.5
        val upperBound = u
        val minsd = 1.0e-6
        val c = max(eps, ((eta0 - t) / 2))

        val estimFn = TruncShrinkage(N=N, withoutReplacement=true, upperBound = upperBound,
            minsd = minsd, eta0 = eta0, c = c, d = d)

        val alpha = AlphaMart(
            estimFn = estimFn,
            N = N,
            upperBound = upperBound,
            withoutReplacement = withoutReplacement,
        )

        return alpha.testH0(maxSamples, terminateOnNullReject=true, showDetails=true) { drawSample.sample() }
    }

}