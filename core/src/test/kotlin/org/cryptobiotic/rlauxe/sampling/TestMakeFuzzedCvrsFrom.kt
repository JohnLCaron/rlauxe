package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.PrevSamplesWithRates
import org.cryptobiotic.rlauxe.oneaudit.makeContestOA
import org.cryptobiotic.rlauxe.util.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TestMakeFuzzedCvrsFrom {
    val show = false

    @Test
    fun testFuzzTwoPersonContest() {
        val avgCvrAssortValue = .505
        val mvrsFuzzPct = .10
        val ncvrs = 10000
        val testCvrs = makeCvrsByExactMean(ncvrs, avgCvrAssortValue)
        val contest = makeContestsFromCvrs(testCvrs).first()
        val contestUA = ContestUnderAudit(contest).makeClcaAssertions(testCvrs)
        val assort = contestUA.clcaAssertions.first().cassorter

        // fuzz
        val testMvrs = makeFuzzedCvrsFrom(listOf(contest), testCvrs, mvrsFuzzPct)
        var sampler = ComparisonWithoutReplacement(
            contestUA.contest as Contest,
            testMvrs.zip(testCvrs),
            assort,
            allowReset = true
        )
        var samples = PrevSamplesWithRates(assort.noerror())
        repeat(ncvrs) {
            samples.addSample(sampler.sample())
        }
        println("  errorCounts = ${samples.errorCounts()}")
        println("  errorRates =  ${samples.errorRates()}")
        assertEquals(ncvrs, samples.errorCounts().sum())

        val changes = samples.errorCounts().subList(1, samples.errorCounts().size).sum()
        val changePct = changes / samples.numberOfSamples().toDouble()
        assertEquals(mvrsFuzzPct, changePct, .01)
    }

    @Test
    fun testFuzzedCvrs() {
        val ncontests = 1
        val test = MultiContestTestData(ncontests, 1, 50000)
        println("contest = ${test.contests.first()}\n")
        val cvrs = test.makeCvrsFromContests()
        val ntrials = 2
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)

        fuzzPcts.forEach { fuzzPct ->
            val fcvrs = makeFuzzedCvrsFrom(test.contests, cvrs, fuzzPct)
            println("fuzzPct = $fuzzPct")
            val totalErrorCounts = mutableListOf(0.0, 0.0, 0.0, 0.0, 0.0)
            test.contests.forEach { contest ->
                val contestUA = makeContestUAfromCvrs(contest.info, cvrs).makeClcaAssertions(cvrs)
                val minAssert = contestUA.minClcaAssertion()
                if (minAssert != null) repeat(ntrials) { trial ->
                    val minAssort = minAssert.cassorter
                    val samples = PrevSamplesWithRates(minAssort.noerror())
                    var ccount = 0
                    var count = 0
                    fcvrs.forEachIndexed { idx, fcvr ->
                        if (fcvr.hasContest(contest.id)) {
                            samples.addSample(minAssort.bassort(fcvr, cvrs[idx]))
                            ccount++
                            if (cvrs[idx] != fcvr) count++
                        }
                    }
                    val fuzz = count.toDouble() / ccount
                    println("  $trial ${contest.name} changed = $count out of ${ccount} = ${df(fuzz)}")
                    println("    errorCounts = ${samples.errorCounts()}")
                    println("    errorRates =  ${samples.errorRates()}")

                    assertEquals(cvrs.size, samples.errorCounts().sum())

                    val changes = samples.errorCounts().subList(1, samples.errorCounts().size).sum()
                    val changePct = changes / samples.numberOfSamples().toDouble()
                    assertEquals(fuzzPct, changePct, .01)

                    samples.errorCounts()
                        .forEachIndexed { idx, it -> totalErrorCounts[idx] = totalErrorCounts[idx] + it }
                }
            }

            val total = ntrials * ncontests * cvrs.size.toDouble()
            val avgErrorRates = totalErrorCounts.map { it / total }
            println("  avgErrorRates = ${avgErrorRates}")

            val changes = totalErrorCounts.subList(1, 5).sum()
            val changePct = changes / total
            assertEquals(fuzzPct, changePct, .01)
        }
    }

    @Test
    fun testMakeFuzzedCvrsFromContestSimulation() {
        val N = 10000
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        val choiceChanges = mutableListOf<MutableMap<String, Int>>()
        fuzzPcts.forEach { fuzzPct ->
            margins.forEach { margin ->
                val sim = ContestSimulation.make2wayTestContest(Nc = N, margin, .1, 0.01)
                val cvrs = sim.makeCvrs()
                if (show) println("fuzzPct = $fuzzPct, margin = $margin ${sim.contest.votes}")
                val fuzzed = makeFuzzedCvrsFrom(listOf(sim.contest), cvrs, fuzzPct)
                val choiceChange = mutableMapOf<String, Int>()
                cvrs.zip(fuzzed).forEach { (cvr, fuzzedCvr) ->
                    val orgChoice = cvr.votes[0]!!.firstOrNull() ?: 2
                    val fuzzChoice = fuzzedCvr.votes[0]!!.firstOrNull() ?: 2
                    val change = (orgChoice).toString() + fuzzChoice.toString()
                    val count = choiceChange[change] ?: 0
                    choiceChange[change] = count + 1
                }
                if (show) {
                    println(" choiceChange")
                    choiceChange.toSortedMap().forEach { println("  $it") }
                }
                choiceChanges.add(choiceChange)

                val unchanged = choiceChange["00"]!! + choiceChange["11"]!! + choiceChange["22"]!!
                if (show) println(" unchanged $unchanged = ${unchanged / N.toDouble()}")
                assertEquals(fuzzPct, 1.0 - unchanged / N.toDouble(), .01)
            }
        }

        val totalChange = mutableMapOf<String, Int>()
        totalChange.mergeReduceS(choiceChanges)

        println(" totalChange")
        totalChange.toSortedMap().forEach { println("  $it") }

        // approx even distribution
        val totalPct = totalChange.map { (key, value) -> Pair(key, value / N.toDouble()) }.toMap()
        assertEquals(totalPct["01"]!!, totalPct["02"]!!, .03)
        assertEquals(totalPct["10"]!!, totalPct["12"]!!, .02)
        assertEquals(totalPct["20"]!!, totalPct["21"]!!, .02)
    }

    @Test
    fun testMakeFuzzedCvrsFromMultiContestTestData() {
        val N = 10000
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        val choiceChanges = mutableListOf<MutableMap<String, Int>>()
        fuzzPcts.forEach { fuzzPct ->
            margins.forEach { margin ->
                val test = MultiContestTestData(1, 1, N, margin..margin, underVotePctRange = 0.1..0.1)
                val cvrs = test.makeCvrsFromContests()
                val contest = test.contests.first()
                val ncands = contest.ncandidates

                if (show) println("ncands = $ncands fuzzPct = $fuzzPct, margin = $margin ${contest.votes}")
                val fuzzed = makeFuzzedCvrsFrom(listOf(contest), cvrs, fuzzPct)
                val choiceChange = mutableMapOf<String, Int>()
                cvrs.zip(fuzzed).forEach { (cvr, fuzzedCvr) ->
                    val orgChoice = cvr.votes[0]!!.firstOrNull() ?: ncands
                    val fuzzChoice = fuzzedCvr.votes[0]!!.firstOrNull() ?: ncands
                    val change = (orgChoice).toString() + fuzzChoice.toString()
                    val count = choiceChange[change] ?: 0
                    choiceChange[change] = count + 1
                }
                if (show) {
                    println(" choiceChange")
                    choiceChange.toSortedMap().forEach { println("  $it") }
                }
                choiceChanges.add(choiceChange)

                val unchanged = sumDiagonal(ncands, choiceChange)
                if (show) println(" unchanged $unchanged = ${unchanged / N.toDouble()}")
                assertEquals(fuzzPct, 1.0 - unchanged / N.toDouble(), .01)

                // approx even distribution
                val choicePct = choiceChange.map { (key, value) -> Pair(key, value / N.toDouble()) }.toMap()
                repeat(ncands) { checkOffDiagonals(it, ncands, choicePct) }
            }
        }

        val totalChange = mutableMapOf<String, Int>()
        totalChange.mergeReduceS(choiceChanges)

        println(" totalChange")
        totalChange.toSortedMap().forEach { println("  $it") }
    }

    @Test
    fun testMakeFuzzedCvrsFromContestOA() {
        val N = 10000
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        val choiceChanges = mutableListOf<MutableMap<String, Int>>()
        fuzzPcts.forEach { fuzzPct ->
            margins.forEach { margin ->
                // fun makeContestOA(margin: Double, Nc: Int, cvrPercent: Double, skewVotesPercent: Double, undervotePercent: Double, phantomPercent: Double): OneAuditContest {
                val contest =
                    makeContestOA(margin, N, cvrPercent = .70, 0.0, undervotePercent = .01, phantomPercent = .01)
                val cvrs = contest.makeTestCvrs()
                val ncands = contest.ncandidates

                if (show) println("ncands = $ncands fuzzPct = $fuzzPct, margin = $margin ${contest.votes}")
                val fuzzed = makeFuzzedCvrsFrom(listOf(contest), cvrs, fuzzPct)
                val choiceChange = mutableMapOf<String, Int>()
                cvrs.zip(fuzzed).forEach { (cvr, fuzzedCvr) ->
                    val orgChoice = cvr.votes[0]!!.firstOrNull() ?: ncands
                    val fuzzChoice = fuzzedCvr.votes[0]!!.firstOrNull() ?: ncands
                    val change = (orgChoice).toString() + fuzzChoice.toString()
                    val count = choiceChange[change] ?: 0
                    choiceChange[change] = count + 1
                }
                if (show) {
                    println(" choiceChange")
                    choiceChange.toSortedMap().forEach { println("  $it") }
                }
                choiceChanges.add(choiceChange)

                val unchanged = sumDiagonal(ncands, choiceChange)
                if (show) println(" unchanged $unchanged = ${unchanged / N.toDouble()}")
                assertEquals(fuzzPct, 1.0 - unchanged / N.toDouble(), .01)

                // approx even distribution
                val choicePct = choiceChange.map { (key, value) -> Pair(key, value / N.toDouble()) }.toMap()
                repeat(ncands) { checkOffDiagonals(it, ncands, choicePct) }
            }
        }

        val totalChange = mutableMapOf<String, Int>()
        totalChange.mergeReduceS(choiceChanges)

        println(" totalChange")
        totalChange.toSortedMap().forEach { println("  $it") }
    }
}

fun sumDiagonal(ncands: Int, choices: Map<String, Int>): Int {
    var sum = 0
    for (cand in 0..ncands) {
        val key = cand.toString() + cand.toString()
        sum += choices[key]!!
    }
    return sum
}

fun checkOffDiagonals(cand: Int, ncands: Int, choicePct: Map<String, Double>) {
    var first: Double = -1.0
    for (other in 0..ncands) {
        if (other != cand) {
            val key = cand.toString() + other.toString()
            val value = choicePct[key]
            if (value != null) {
                if (first < 0) first = value
                else assertEquals(first, value, .01)
            }
        }
    }
}