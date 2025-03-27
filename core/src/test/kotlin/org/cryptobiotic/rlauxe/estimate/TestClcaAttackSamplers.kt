package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TestClcaAttackSamplers {

    @Test
    fun testClcaFlipErrorsSampler() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap("Alice", "Bob", "Candy"),
        )

        val cvrMean = .51
        val meanDiff = -.015
        val N = 10000
        println("testComparisonWithErrors N=$N cvrMean=$cvrMean meanDiff=$meanDiff")
        repeat(11) {
            val cvrs = makeCvrsByExactMean(N, cvrMean)
            val contest = makeContestUAfromCvrs(info, cvrs).makeClcaAssertions()
            val bassorter = contest.clcaAssertions.first().cassorter as ClcaAssorter
            assertEquals(.02, bassorter.assorter().reportedMargin(), doublePrecision)
            assertEquals(0.5050505050505051, bassorter.noerror(), doublePrecision)
            assertEquals(1.0101010101010102, bassorter.upperBound(), doublePrecision)

            val cs0 = ClcaFlipErrorsSampler(cvrs, bassorter, cvrMean)
            assertEquals(bassorter.noerror(), cs0.sampleMean, doublePrecision)

            val cs = ClcaFlipErrorsSampler(cvrs, bassorter, cvrMean + meanDiff)
            val assorter = bassorter.assorter()
            val cvrVotes = cs.cvrs.map { assorter.assort(it) }.sum()
            val mvrVotes = cs.mvrs.map { assorter.assort(it) }.sum()
            assertEquals(cvrVotes - cs.flippedVotes, mvrVotes, doublePrecision)
            assertEquals(N * cs.mvrMean, mvrVotes, doublePrecision)

            println(" ComparisonWithErrors: cvrVotes=$cvrVotes mvrVotes=$mvrVotes sampleCount=${cs.sampleCount} sampleMean=${cs.sampleMean}")

            val expectedAssortValue = (N - cs.flippedVotes) * (bassorter.noerror())

            testLimits(cs, N, bassorter.upperBound())

            repeat(11) {
                cs.reset()
                var assortValue = 0.0
                repeat(N) { assortValue += cs.sample() }
                assertEquals(expectedAssortValue, assortValue, doublePrecision)
                assertEquals(cs.sampleCount, assortValue, doublePrecision)
                assertEquals(cs.sampleMean, assortValue / N, doublePrecision)
            }
        }
    }

    @Test
    fun testClcaFlipErrorsSampler2() {
        val N = 20000
        val reportedMargin = .05
        val reportedAvg = margin2mean(reportedMargin)
        val cvrs = makeCvrsByExactMean(N, reportedAvg)
        val contest = makeContestsFromCvrs(cvrs).first()
        val contestUA = ContestUnderAudit(contest).makeClcaAssertions()
        val compareAssorter = contestUA.clcaAssertions.first().cassorter as ClcaAssorter

        val meanDiff = .01
        val sampler = ClcaFlipErrorsSampler(cvrs, compareAssorter, reportedAvg - meanDiff)
        testLimits(sampler, N, compareAssorter.upperBound())

        val noerror = compareAssorter.noerror()
        assertEquals((meanDiff * N).toInt(), countAssortValues(sampler, N, 0.0))
        assertEquals(0, countAssortValues(sampler, N, noerror / 2))
        assertEquals(
            ((1.0 - meanDiff) * N).toInt(),
            countAssortValues(sampler, N, noerror)
        )
        assertEquals(0, countAssortValues(sampler, N, 3 * noerror / 2))
        assertEquals(0, countAssortValues(sampler, N, 2 * noerror))
    }

    @Test
    fun testClcaAttackSampler() {
        val N = 20000
        val margins = listOf(.017, .03, .05)
        val p2s = listOf(.015, .01, .005, .001, .000)
        for (margin in margins) {
            for (p2 in p2s) {

                val theta = margin2mean(margin)
                val cvrs = makeCvrsByExactMean(N, theta)
                val contest = makeContestsFromCvrs(cvrs).first()
                val contestUA = ContestUnderAudit(contest).makeClcaAssertions()
                val compareAssorter = contestUA.clcaAssertions.first().cassorter as ClcaAssorter

                val sampler = ClcaAttackSampler(cvrs, compareAssorter, p2)
                testLimits(sampler, N, compareAssorter.upperBound())

                val noerror = compareAssorter.noerror()
                assertEquals((p2 * N).toInt(), countAssortValues(sampler, N, 0.0))
                assertEquals(0, countAssortValues(sampler, N, noerror / 2))
                assertEquals(
                    ((1.0 - p2) * N).toInt(),
                    countAssortValues(sampler, N, noerror)
                )
                assertEquals(0, countAssortValues(sampler, N, 3 * noerror / 2))
                assertEquals(0, countAssortValues(sampler, N, 2 * noerror))
            }
        }
    }

    @Test
    fun testClcaAttackSamplerBoth() {
        val N = 20000
        val margins = listOf(.017, .03, .05)
        val p1s = listOf(.01, .001)
        val p2s = listOf(.01, .001, .0001)
        for (margin in margins) {
            for (p2 in p2s) {
                for (p1 in p1s) {
                    val theta = margin2mean(margin)
                    val cvrs = makeCvrsByExactMean(N, theta)
                    val contest = makeContestsFromCvrs(cvrs).first()
                    val contestUA = ContestUnderAudit(contest).makeClcaAssertions()
                    val compareAssorter = contestUA.clcaAssertions.first().cassorter as ClcaAssorter

                    val sampler = ClcaAttackSampler(
                        cvrs,
                        compareAssorter,
                        p2,
                        p1,
                        true
                    ) // false just makes the numbers imprecise
                    testLimits(sampler, N, compareAssorter.upperBound())
                    println("\nmargin=$margin p2 = $p2 p1= $p1")
                    val noerror = compareAssorter.noerror()
                   // println(" p2 = ${countAssortValues(sampler, N, 0.0)} expect ${(p2 * N)}")
                    // println(" p1 = ${countAssortValues(sampler, N, noerror / 2)} expect ${(p1 * N)}")

                    assertEquals(0, countAssortValues(sampler, N, 2 * noerror))
                    assertEquals(0, countAssortValues(sampler, N, 3 * noerror / 2))
                    assertEquals((p2 * N).toInt(), countAssortValues(sampler, N, 0.0))
                    assertEquals(
                        (p1 * N).toInt(),
                        countAssortValues(sampler, N, noerror / 2)
                    )
                    assertEquals(
                        ((1.0 - p2 - p1) * N).toInt(),
                        countAssortValues(sampler, N, noerror)
                    )
                }
            }
        }
    }

    @Test
    fun testMakeFlippedMvrs() {
        val N = 20000
        val margins = listOf(.0021, .0041, .011)
        val p2s = listOf(.015, .01, .005, .001, .000)
        for (margin in margins) {
            for (p2 in p2s) {
                println("\nmargin=$margin p2 = $p2")

                val theta = margin2mean(margin)
                val cvrs = makeCvrsByExactMean(N, theta)
                val orgMargin = showMargin("cvrs", cvrs)
                assertEquals(margin, orgMargin, doublePrecision )

                val mvrs = makeFlippedMvrs(cvrs, N, p2, null)
                val newMargin = showMargin("mvrs", mvrs)
                assertEquals(orgMargin-2*p2, newMargin, doublePrecision )
                assertEquals(margin2mean(orgMargin)-p2, margin2mean(newMargin), doublePrecision )
            }
        }
    }
}

fun showMargin(what: String, cvrs: List<Cvr>): Double {
    val votes: Map<Int, Map<Int, Int>> = tabulateVotes(cvrs)
    assertEquals(1, votes.size )
    val contest0 =  votes[0]!!.toSortedMap()
    assertEquals(2, contest0.size )
    print(" $what votes=${contest0}")
    val vote0 = contest0[0]!!
    val vote1 = contest0[1]!!
    val N = cvrs.size
    assertEquals(N, vote0 + vote1 )
    val margin = (vote0-vote1)/N.toDouble()
    println("  margin=$margin")
    return margin
}