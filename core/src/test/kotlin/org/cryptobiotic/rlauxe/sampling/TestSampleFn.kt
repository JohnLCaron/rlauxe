package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.doubleIsClose
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.makeStandardComparisonAssorter
import org.cryptobiotic.rlauxe.makeStandardContest
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestSampleFn {

    @Test
    fun testComparisonSamplerForEstimation() {
        val N = 20000
        val margins = listOf(.017, .03, .05)
        val p2s = listOf(.015, .01, .005, .001, .000)
        for (margin in margins) {
            for (p2 in p2s) {
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
                print("ok ")
            }
        }
        println()
    }

    @Test
    fun testComparisonWithBothErrorRates() {
        val N = 20000
        val margins = listOf(.017, .03, .05)
        val p1s = listOf(.01, .001)
        val p2s = listOf(.01, .001, .0001)
        for (margin in margins) {
            for (p2 in p2s) {
                for (p1 in p1s) {
                    val theta = margin2mean(margin)
                    val cvrs = makeCvrsByExactMean(N, theta)
                    val compareAssorter = makeStandardComparisonAssorter(theta)
                    val sampler = ComparisonWithErrorRates(
                        cvrs,
                        compareAssorter,
                        p2,
                        p1,
                        true
                    ) // false just makes the numbers imprecise
                    testLimits(sampler, N, compareAssorter.upperBound)
                    println("\nmargin=$margin p2 = $p2 p1= $p1")
                    val noerror = compareAssorter.noerror
                    // println(" p2 = ${countAssortValues(sampler, N, 0.0)} expect ${(p2 * N)}")
                    // println(" p1 = ${countAssortValues(sampler, N, noerror / 2)} expect ${(p1 * N)}")

                    assertEquals(0, countAssortValues(sampler, N, 2 * noerror))
                    assertEquals(0, countAssortValues(sampler, N, 3 * noerror / 2))
                    assertEquals((p2 * N).toInt(), countAssortValues(sampler, N, 0.0))
                    assertEquals((p1 * N).toInt(), countAssortValues(sampler, N, noerror / 2))
                    assertEquals(((1.0 - p2 - p1) * N).toInt(), countAssortValues(sampler, N, noerror))
                }
            }
        }
    }

}