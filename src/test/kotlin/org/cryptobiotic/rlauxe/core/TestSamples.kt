package org.cryptobiotic.rlauxe.core

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
        val cvrs = makeCvrsByExactMargin(N, theta2margin(theta))
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
    fun testMakeCvrsByExactMargin() {
        repeat (20) {
            val cvrs = makeCvrsByExactMargin(100, theta2margin(.575))
            val actual = cvrs.map { it.hasMarkFor(0, 0) }.sum()
            assertEquals(57, actual)
        }
    }
}