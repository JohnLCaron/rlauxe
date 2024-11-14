package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.makeStandardComparisonAssorter
import org.cryptobiotic.rlauxe.makeStandardContest
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TestSampleFn {

    @Test
    fun testComparisonSamplerForEstimation() {
        val N = 20000
        val margins = listOf(.017, .03, .05)
        for (margin in margins) {
            val theta = margin2mean(margin)
            val cvrs: List<CvrIF> = makeCvrsByExactMean(N, theta)
            val cvrsUA = cvrs.map { CvrUnderAudit(it as Cvr, false) }
            val contest = makeStandardContest()
            val contestUA = ContestUnderAudit(contest)
            val compareAssorter = makeStandardComparisonAssorter(theta)
            val sampler = ComparisonSamplerForEstimation(cvrsUA, contestUA, compareAssorter)
            testLimits(sampler, N, compareAssorter.upperBound)

            assertEquals(sampler.p1 * N, sampler.flippedVotes1.toDouble())
            assertEquals(sampler.p2 * N, sampler.flippedVotes2.toDouble())
            assertEquals(sampler.p3 * N, sampler.flippedVotes3.toDouble())
            assertEquals(sampler.p4 * N, sampler.flippedVotes4.toDouble())

            val noerror = compareAssorter.noerror
            val p = 1.0 - sampler.p1 - sampler.p2 - sampler.p3 - sampler.p4
            assertEquals(sampler.p1 * N, countAssortValues(sampler, N, noerror / 2).toDouble())
            assertEquals(sampler.p2 * N, countAssortValues(sampler, N, 0.0).toDouble())
            assertEquals(p * N, countAssortValues(sampler, N, noerror).toDouble())
            assertEquals(sampler.p3 * N, countAssortValues(sampler, N, 3 * noerror / 2).toDouble())
            assertEquals(sampler.p4 * N, countAssortValues(sampler, N, 2 * noerror).toDouble())
        }
    }

}