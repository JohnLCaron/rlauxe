package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.doubleIsClose
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestSampleGenerator {

    @Test
    fun testSampleMeans() {
        repeat(20) {
            testSampleMeans(100, .575)
        }
    }

    fun testSampleMeans(N: Int, theta: Double, silent: Boolean = false, showContests: Boolean = true) {
        val cvrs = makeCvrsByExactMean(N, theta)
        val checkAvotes = cvrs.map { it.hasMarkFor(0, 0) }.sum()
        assertEquals((N * theta).toInt(), checkAvotes)

        if (!silent) println(" N=${cvrs.size} theta=$theta withoutReplacement")

        // count actual votes
        val votes: Map<Int, Map<Int, Int>> = tabulateVotes(cvrs) // contest -> candidate -> count
        if (!silent && showContests) {
            votes.forEach { key, cands ->
                println("contest ${key} ")
                cands.forEach { println("  ${it} ${it.value.toDouble() / cvrs.size}") }
            }
        }

        // make contests from cvrs
        val contests: List<Contest> = makeContestsFromCvrs(cvrs)
        if (!silent && showContests) {
            println("Contests")
            contests.forEach { println("  ${it}") }
        }
        contests.forEach { contest ->
            if (contest.winners != listOf(0)) {
                makeContestsFromCvrs(votes, cardsPerContest(cvrs))
            }
            assertEquals(contest.winners, listOf(0))
        }
        val contestsUA = contests.map { ContestUnderAudit(it, isComparison = false).makePollingAssertions() }

        contestsUA.forEach { contestUA ->
            contestUA.pollingAssertions.forEach { ass ->
                assertEquals(0, (ass.assorter as PluralityAssorter).winner)
            }

            if (!silent && showContests) println("Assertions for Contest ${contestUA.id}")
            contestUA.pollingAssertions.forEach {
                if (!silent && showContests) println("  ${it}")

                val cvrSampler = PollWithoutReplacement(contestUA, cvrs, it.assorter)
            }
        }
    }

    @Test
    fun testMakeCvrsByExactMean() {
        repeat(20) {
            val cvrs = makeCvrsByExactMean(100, .573)
            val actual = cvrs.map { it.hasMarkFor(0, 0) }.sum()
            assertEquals(57, actual)
        }
    }

    @Test
    fun testComparisonWithErrors() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "Alice", "Bob", "Candy"),
        )

        val cvrMean = .51
        val meanDiff = -.015
        val N = 10000
        println("testComparisonWithErrors N=$N cvrMean=$cvrMean meanDiff=$meanDiff")
        repeat(11) {
            val cvrs = makeCvrsByExactMean(N, cvrMean)
            val contest = ContestUnderAudit(info, cvrs).makeComparisonAssertions(cvrs)
            val bassorter = contest.comparisonAssertions.first().cassorter
            assertEquals(.02, bassorter.margin, doublePrecision)
            assertEquals(0.5050505050505051, bassorter.noerror, doublePrecision)
            assertEquals(1.0101010101010102, bassorter.upperBound, doublePrecision)

            val cs0 = ComparisonWithErrors(cvrs, bassorter, cvrMean)
            assertEquals(bassorter.noerror, cs0.sampleMean, doublePrecision)

            val cs = ComparisonWithErrors(cvrs, bassorter, cvrMean + meanDiff)
            val assorter = bassorter.assorter
            val cvrVotes = cs.cvrs.map { assorter.assort(it) }.sum()
            val mvrVotes = cs.mvrs.map { assorter.assort(it) }.sum()
            assertEquals(cvrVotes - cs.flippedVotes, mvrVotes, doublePrecision)
            assertEquals(N * cs.mvrMean, mvrVotes, doublePrecision)

            println(" ComparisonWithErrors: cvrVotes=$cvrVotes mvrVotes=$mvrVotes sampleCount=${cs.sampleCount} sampleMean=${cs.sampleMean}")

            val expectedAssortValue = (N - cs.flippedVotes) * (bassorter.noerror)

            testLimits(cs, N, bassorter.upperBound)

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
    fun testComparisonWithErrorsLimits() {
        val N = 20000
        val reportedMargin = .05
        val reportedAvg = margin2mean(reportedMargin)
        val cvrs = makeCvrsByExactMean(N, reportedAvg)
        val contest = makeContestsFromCvrs(cvrs).first()
        val contestUA = ContestUnderAudit(contest).makeComparisonAssertions(cvrs)
        val compareAssorter = contestUA.comparisonAssertions.first().cassorter

        val meanDiff = .01
        val sampler = ComparisonWithErrors(cvrs, compareAssorter, reportedAvg-meanDiff)
        testLimits(sampler, N, compareAssorter.upperBound)

        val noerror = compareAssorter.noerror
        assertEquals((meanDiff*N).toInt(), countAssortValues(sampler, N, 0.0))
        assertEquals(0, countAssortValues(sampler, N, noerror/2))
        assertEquals(((1.0-meanDiff)*N).toInt(), countAssortValues(sampler, N, noerror))
        assertEquals(0, countAssortValues(sampler, N, 3*noerror/2))
        assertEquals(0, countAssortValues(sampler, N, 2*noerror))
    }

    @Test
    fun testComparisonWithP2ErrorRates() {
        val N = 20000
        val margins = listOf(.017, .03, .05)
        val p2s = listOf(.015, .01, .005, .001, .000)
        for (margin in margins) {
            for (p2 in p2s) {

                val theta = margin2mean(margin)
                val cvrs = makeCvrsByExactMean(N, theta)
                val contest = makeContestsFromCvrs(cvrs).first()
                val contestUA = ContestUnderAudit(contest).makeComparisonAssertions(cvrs)
                val compareAssorter = contestUA.comparisonAssertions.first().cassorter

                val sampler = ComparisonWithErrorRates(cvrs, compareAssorter, p2)
                testLimits(sampler, N, compareAssorter.upperBound)

                val noerror = compareAssorter.noerror
                assertEquals((p2 * N).toInt(), countAssortValues(sampler, N, 0.0))
                assertEquals(0, countAssortValues(sampler, N, noerror / 2))
                assertEquals(((1.0 - p2) * N).toInt(), countAssortValues(sampler, N, noerror))
                assertEquals(0, countAssortValues(sampler, N, 3 * noerror / 2))
                assertEquals(0, countAssortValues(sampler, N, 2 * noerror))
            }
        }
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
                    val contest = makeContestsFromCvrs(cvrs).first()
                    val contestUA = ContestUnderAudit(contest).makeComparisonAssertions(cvrs)
                    val compareAssorter = contestUA.comparisonAssertions.first().cassorter

                    val sampler = ComparisonWithErrorRates(cvrs, compareAssorter, p2, p1, true) // false just makes the numbers imprecise
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

fun testLimits(sampler: SampleGenerator, nsamples: Int, upper: Double) {
    repeat(nsamples) {
        val ss = sampler.sample()
        assertTrue(ss >= 0)
        assertTrue(ss <= upper)
    }
}

fun countAssortValues(sampler: SampleGenerator, nsamples: Int, assortValue: Double): Int {
    sampler.reset()
    var count = 0
    repeat(nsamples) {
        val ss = sampler.sample()
        if (doubleIsClose(ss, assortValue))
            count++
    }
    return count
}