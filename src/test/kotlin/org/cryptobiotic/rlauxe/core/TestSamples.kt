package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doublePrecision
import kotlin.test.Test
import kotlin.test.assertEquals

class TestSamples {

    @Test
    fun testSampleMeans() {
        repeat (20) {
            testSampleMeans(100, .575)
        }
    }

    fun testSampleMeans(N: Int, theta: Double, silent: Boolean = false, showContests: Boolean = true) {
        val cvrs = makeCvrsByExactMean(N, theta)
        val checkAvotes = cvrs.map {  it.hasMarkFor(0, 0)}.sum()
        assertEquals((N * theta).toInt(), checkAvotes.toInt())

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
        val contests: List<AuditContest> = makeContestsFromCvrs(votes, cardsPerContest(cvrs))
        if (!silent && showContests) {
            println("Contests")
            contests.forEach { println("  ${it}") }
        }
        contests.forEach { contest ->
            assertEquals(contest.winners, listOf(0))
        }


        // Polling Audit
        val audit = makePollingAudit(contests = contests)

        audit.assertions.map { (contest, assertions) ->
            assertions.forEach { ass ->
                assertEquals(0, (ass.assorter as PluralityAssorter).winner)
            }

            if (!silent && showContests) println("Assertions for Contest ${contest.id}")
            assertions.forEach {
                if (!silent && showContests) println("  ${it}")

                val cvrSampler = PollWithoutReplacement(cvrs, it.assorter)
                println("truePopulationCount = ${cvrSampler.truePopulationCount()}")
                println("truePopulationMean = ${cvrSampler.truePopulationMean()}")
                assertEquals((N * theta).toInt(), cvrSampler.truePopulationCount().toInt())
                assertEquals(theta.toInt(), cvrSampler.truePopulationMean().toInt())
            }
        }
    }

    @Test
    fun testmakeCvrsByExactMean() {
        repeat (20) {
            val cvrs = makeCvrsByExactMean(100, .575)
            val actual = cvrs.map { it.hasMarkFor(0, 0) }.sum()
            assertEquals(57, actual)
        }
    }

    @Test
    fun testComparisonWithErrors() {
        val contest = AuditContest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidates = listOf(0, 1, 2),
            winners = listOf(0),
        )
        val assorter = PluralityAssorter(contest, winner = 0, loser = 1)
        val cvrMean = .51
        val meanDiff = -.015
        val N = 10000
        println("testComparisonWithErrors N=$N cvrMean=$cvrMean meanDiff=$meanDiff")
        repeat (11) {
            val cvrs = makeCvrsByExactMean(N, cvrMean)
            val bassorter = ComparisonAssorter(contest, assorter, cvrMean)
            assertEquals(.02, bassorter.margin, doublePrecision)
            assertEquals(0.5050505050505051, bassorter.noerror, doublePrecision)
            assertEquals(1.0101010101010102, bassorter.upperBound, doublePrecision)

            val cs0 = ComparisonWithErrors(cvrs, bassorter, cvrMean)
            assertEquals(bassorter.noerror, cs0.sampleMean, doublePrecision)

            val cs = ComparisonWithErrors(cvrs, bassorter, cvrMean + meanDiff)
            val (margin, noerror, upperBound) = comparisonAssorterCalc(cvrMean, 1.0)
            assertEquals(margin, bassorter.margin, doublePrecision)
            assertEquals(noerror, bassorter.noerror, doublePrecision)
            assertEquals(upperBound, bassorter.upperBound, doublePrecision)

            val cvrVotes = cs.cvrs.map { assorter.assort(it) }.sum()
            val mvrVotes = cs.mvrs.map { assorter.assort(it) }.sum()
            assertEquals(cvrVotes + cs.flippedVotes, mvrVotes, doublePrecision)
            assertEquals(N * cs.mvrMean, mvrVotes, doublePrecision)

            println(" ComparisonWithErrors: cvrVotes=$cvrVotes mvrVotes=$mvrVotes sampleCount=${cs.sampleCount} sampleMean=${cs.sampleMean}")

            val expectedAssortValue = cs.flippedVotes * (2 * bassorter.noerror) + (N - cs.flippedVotes) * (bassorter.noerror)

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
}