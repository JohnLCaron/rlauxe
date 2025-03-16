package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.PollWithoutReplacement
import org.cryptobiotic.rlauxe.workflow.Sampler
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

                val cvrSampler = PollWithoutReplacement(contestUA.id, cvrs, it.assorter)
            }
        }
    }

    @Test
    fun testMakeCvrsByExactMean() {
        repeat(20) {
            val cvrs = makeCvrsByExactMean(100, .573)
            val actual = cvrs.sumOf { it.hasMarkFor(0, 0) }
            assertEquals(57, actual)
        }
    }

}

fun testLimits(sampler: Sampler, nsamples: Int, upper: Double) {
    repeat(nsamples) {
        val ss = sampler.sample()
        assertTrue(ss >= 0)
        assertTrue(ss <= upper)
    }
}

fun countAssortValues(sampler: Sampler, nsamples: Int, assortValue: Double): Int {
    sampler.reset()
    var count = 0
    repeat(nsamples) {
        val ss = sampler.sample()
        if (doubleIsClose(ss, assortValue))
            count++
    }
    return count
}