package org.cryptobiotic.rlauxe.unittest

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.sampling.Sampler
import org.cryptobiotic.rlauxe.sampling.flipExactVotes
import org.cryptobiotic.rlauxe.sampling.add2voteOverstatements
import org.cryptobiotic.rlauxe.sampling.makeContestsFromCvrs
import org.cryptobiotic.rlauxe.sampling.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.util.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestSampleGenerator {

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
            val bassorter = contest.comparisonAssertions.first().cassorter as ComparisonAssorter
            assertEquals(.02, bassorter.margin(), doublePrecision)
            assertEquals(0.5050505050505051, bassorter.noerror(), doublePrecision)
            assertEquals(1.0101010101010102, bassorter.upperBound(), doublePrecision)

            val cs0 = ComparisonWithErrors(cvrs, bassorter, cvrMean)
            assertEquals(bassorter.noerror(), cs0.sampleMean, doublePrecision)

            val cs = ComparisonWithErrors(cvrs, bassorter, cvrMean + meanDiff)
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
    fun testComparisonWithErrorsLimits() {
        val N = 20000
        val reportedMargin = .05
        val reportedAvg = margin2mean(reportedMargin)
        val cvrs = makeCvrsByExactMean(N, reportedAvg)
        val contest = makeContestsFromCvrs(cvrs).first()
        val contestUA = ContestUnderAudit(contest).makeComparisonAssertions(cvrs)
        val compareAssorter = contestUA.comparisonAssertions.first().cassorter as ComparisonAssorter

        val meanDiff = .01
        val sampler = ComparisonWithErrors(cvrs, compareAssorter, reportedAvg - meanDiff)
        testLimits(sampler, N, compareAssorter.upperBound())

        val noerror = compareAssorter.noerror()
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
                val compareAssorter = contestUA.comparisonAssertions.first().cassorter as ComparisonAssorter

                val sampler = ComparisonWithErrorRates(cvrs, compareAssorter, p2)
                testLimits(sampler, N, compareAssorter.upperBound())

                val noerror = compareAssorter.noerror()
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
                    val compareAssorter = contestUA.comparisonAssertions.first().cassorter as ComparisonAssorter

                    val sampler = ComparisonWithErrorRates(
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
                    assertEquals((p1 * N).toInt(), countAssortValues(sampler, N, noerror / 2))
                    assertEquals(((1.0 - p2 - p1) * N).toInt(), countAssortValues(sampler, N, noerror))
                }
            }
        }
    }

}


// TODO candidate for removal
// generate mvr by starting with cvrs and flipping exact # votes (type 2 errors only)
// to make mvrs have mvrMean.
data class ComparisonWithErrors(val cvrs : List<Cvr>, val cassorter: ComparisonAssorter, val mvrMean: Double,
                                val withoutReplacement: Boolean = true): Sampler {
    val maxSamples = cvrs.count { it.hasContest(cassorter.contest.info.id) }
    val mvrs : List<Cvr>
    val permutedIndex = MutableList(cvrs.size) { it }
    val sampleMean: Double
    val sampleCount: Double
    val flippedVotes: Int
    var idx = 0

    init {
        reset()

        // we want to flip the exact number of votes, for reproducibility
        val mmvrs = mutableListOf<Cvr>()
        mmvrs.addAll(cvrs)
        flippedVotes = flipExactVotes(mmvrs, mvrMean)
        mvrs = mmvrs.toList()

        sampleCount = cvrs.mapIndexed { idx, it -> cassorter.bassort(mvrs[idx], it)}.sum()
        sampleMean = sampleCount / cvrs.size
    }

    override fun sample(): Double {
        val assortVal = if (withoutReplacement) {
            val cvr = cvrs[permutedIndex[idx]]
            val mvr = mvrs[permutedIndex[idx]]
            idx++
            cassorter.bassort(mvr, cvr)
        } else {
            val chooseIdx = Random.nextInt(cvrs.size) // with Replacement
            val cvr = cvrs[chooseIdx]
            val mvr = mvrs[chooseIdx]
            cassorter.bassort(mvr, cvr)
        }
        return assortVal
    }

    override fun reset() {
        permutedIndex.shuffle(Random)
        idx = 0
    }

    fun sampleMean() = sampleMean
    fun sampleCount() = sampleCount
    override fun maxSamples() = maxSamples
}

// TODO candidate for removal
// generate mvr by starting with cvrs and flipping (N * p2) votes (type 2 errors) and (N * p1) votes (type 1 errors)
// TODO: generalize to p3, p4
data class ComparisonWithErrorRates(val cvrs : List<Cvr>, val cassorter: ComparisonAssorter,
                                    val p2: Double, val p1: Double = 0.0,
                                    val withoutReplacement: Boolean = true): Sampler {
    val maxSamples = cvrs.count { it.hasContest(cassorter.contest.info.id) }
    val N = cvrs.size
    val mvrs : List<Cvr>
    val permutedIndex = MutableList(N) { it }
    val sampleMean: Double
    val sampleCount: Double
    val flippedVotes2: Int
    val flippedVotes1: Int

    var idx = 0

    init {
        reset()

        // we want to flip the exact number of votes, for reproducibility
        val mmvrs = mutableListOf<Cvr>()
        mmvrs.addAll(cvrs)
        flippedVotes2 = add2voteOverstatements(mmvrs, needToChangeVotesFromA = (N * p2).toInt())
        flippedVotes1 =  if (p1 == 0.0) 0 else {
            add1voteOverstatements(mmvrs, needToChangeVotesFromA = (N * p1).toInt())
        }
        mvrs = mmvrs.toList()

        sampleCount = cvrs.mapIndexed { idx, it -> cassorter.bassort(mvrs[idx], it)}.sum()
        sampleMean = sampleCount / N
    }

    override fun sample(): Double {
        val assortVal = if (withoutReplacement) {
            val cvr = cvrs[permutedIndex[idx]]
            val mvr = mvrs[permutedIndex[idx]]
            idx++
            cassorter.bassort(mvr, cvr)
        } else {
            val chooseIdx = Random.nextInt(N) // with Replacement
            val cvr = cvrs[chooseIdx]
            val mvr = mvrs[chooseIdx]
            cassorter.bassort(mvr, cvr)
        }
        return assortVal
    }

    override fun reset() {
        permutedIndex.shuffle(Random)
        idx = 0
    }

    fun sampleMean() = sampleMean
    fun sampleCount() = sampleCount
    override fun maxSamples() = maxSamples
}

///////////////////////
// TODO candidates for removal

// change cvrs to add the given number of one-vote overstatements.
private fun add1voteOverstatements(cvrs: MutableList<Cvr>, needToChangeVotesFromA: Int): Int {
    if (needToChangeVotesFromA == 0) return 0
    val ncards = cvrs.size
    val startingAvotes = cvrs.sumOf { it.hasMarkFor(0, 0) }
    var changed = 0
    while (changed < needToChangeVotesFromA) {
        val cvrIdx = Random.nextInt(ncards)
        val cvr = cvrs[cvrIdx]
        if (cvr.hasMarkFor(0, 0) == 1) {
            val votes = mutableMapOf<Int, IntArray>()
            votes[0] = intArrayOf(2)
            cvrs[cvrIdx] = Cvr("card-$cvrIdx", votes)
            changed++
        }
    }
    val checkAvotes = cvrs.sumOf { it.hasMarkFor(0, 0) }
    // if (debug) println("flipped = $needToChangeVotesFromA had $startingAvotes now have $checkAvotes votes for A")
    require(checkAvotes == startingAvotes - needToChangeVotesFromA)
    return changed
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