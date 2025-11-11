package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.ClcaErrorRatesAvg
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.PrevSamplesWithRates
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.oneaudit.makeOneContestUA
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.math.max

class TestMakeFuzzedCvrsFrom {
    val show = false
    val showOA = false

    @Test
    fun testFuzzTwoPersonContest() {
        val avgCvrAssortValue = .505
        val mvrsFuzzPct = .10
        val ncvrs = 10000
        val testCvrs = makeCvrsByExactMean(ncvrs, avgCvrAssortValue)
        val contest = makeContestsFromCvrs(testCvrs).first()
        val contestUA = ContestUnderAudit(contest).addStandardAssertions()
        val assort = contestUA.clcaAssertions.first().cassorter

        // fuzz
        val testMvrs = makeFuzzedCvrsFrom(listOf(contest), testCvrs, mvrsFuzzPct)
        val sampler = ClcaWithoutReplacement(
            contestUA.id,
            testMvrs.zip(testCvrs),
            assort,
            allowReset = true
        )
        val samples = PrevSamplesWithRates(assort.noerror())
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
                val test = MultiContestTestData(1, 1, N, hasStyle=true, margin..margin, underVotePctRange = 0.1..0.1)
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
                assertEquals(fuzzPct, 1.0 - unchanged / N.toDouble(), .015)

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
        val Nc = 10000
        val fuzzPcts = listOf(0.001, .005, .01, .02, .05)
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        val contestId = 0

        val choiceChanges = mutableListOf<MutableMap<String, Int>>()
        fuzzPcts.forEach { fuzzPct ->
            println("===================================")
            val welfordFromCvrs = Welford()
            val welfordFromFuzz = Welford()
            margins.forEach { margin ->
                // fun makeContestOA(margin: Double, Nc: Int, cvrPercent: Double, skewVotesPercent: Double, undervotePercent: Double, phantomPercent: Double): OneAuditContest {
                val (contestOA, _, cvrs) = makeOneContestUA(margin, Nc, cvrFraction = .70, undervoteFraction = .01, phantomFraction = .01) // TODO no skew
                val ncands = contestOA.ncandidates
                val contest = contestOA.contest as Contest
                if (showOA) println("ncands = $ncands fuzzPct = $fuzzPct, margin = $margin ${contest.votes}")

                val vunder = tabulateVotesWithUndervotes(cvrs.iterator(), 0, ncands)
                if (showOA) println("cvrVotes = ${vunder}  contestVotes = ${contest.votesAndUndervotes()}")
                assertEquals(vunder, contest.votesAndUndervotes())
                assertEquals(Nc, cvrs.size)

                val fuzzed = makeFuzzedCvrsFrom(listOf(contestOA.contest), cvrs, fuzzPct, welfordFromFuzz)
                val mvrVotes = tabulateVotesWithUndervotes(fuzzed.iterator(), 0, ncands)
                if (showOA) println("mvrVotes = ${mvrVotes}")

                val choiceChange = mutableMapOf<String, Int>() // org-fuzz -> count
                cvrs.zip(fuzzed).forEach { (cvr, fuzzedCvr) ->
                    if (!cvr.phantom) {
                        val orgChoice = cvr.votes[contestId]!!.firstOrNull() ?: ncands
                        val fuzzChoice = fuzzedCvr.votes[contestId]!!.firstOrNull() ?: ncands
                        val changeKey = (orgChoice).toString() + fuzzChoice.toString()
                        val count = choiceChange[changeKey] ?: 0
                        choiceChange[changeKey] = count + 1
                    }
                }
                if (showOA) {
                    println(" choiceChange")
                    print(showChangeMatrix(ncands, choiceChange))
                    // choiceChange.toSortedMap().forEach { println("  $it") }
                }
                val ncast = contestOA.contest.Ncast()
                choiceChanges.add(choiceChange)
                val allSum = choiceChange.values.sum()
                assertEquals(ncast, allSum)

                val changed = sumOffDiagonal(ncands, choiceChange)
                val unchanged = sumDiagonal(ncands, choiceChange)
                assertEquals(ncast, changed + unchanged)

                val changedPct = 1.0 - unchanged / ncast.toDouble()
                if (showOA) println(" unchanged=$unchanged = changedPct=$changedPct should be ${1.0 - fuzzPct} diff = ${df(fuzzPct - changedPct)}")
                welfordFromCvrs.update(fuzzPct - changedPct)

//                assertEquals(1.0 - fuzzPct, unchangedPct, .015)
                if (showOA) println()
                // approx even distribution TODO seems bogus
                //val choicePct = choiceChange.map { (key, value) -> Pair(key, value / N.toDouble()) }.toMap()
                //repeat(ncands) { checkOffDiagonals(it, ncands, choicePct) }
            }
            println(" fuzzPct =$fuzzPct welfordFromCvrs: ${welfordFromCvrs.show()}")
            println(" welfordFromFuzz: ${welfordFromFuzz.show()}")
        }

        val totalChange = mutableMapOf<String, Int>()
        totalChange.mergeReduceS(choiceChanges)

        // println(showChangeMatrix(2, totalChange))
        // totalChange.toSortedMap().forEach { println("  $it") }
    }

    @Test
    fun testFuzzedCvrsMultipleContests() {
        val ncontests = 11
        val test = MultiContestTestData(ncontests, 1, 50000, hasStyle=true)
        println("contest = ${test.contests.first()}\n")
        val cvrs = test.makeCvrsFromContests()
        val ntrials = 2
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)

        fuzzPcts.forEach { fuzzPct ->
            val fcvrs = makeFuzzedCvrsFrom(test.contests, cvrs, fuzzPct)
            println("fuzzPct = $fuzzPct")
            val totalErrorCounts = mutableListOf(0.0, 0.0, 0.0, 0.0, 0.0)
            test.contests.forEach { contest ->
                val contestUA = makeContestUAfromCvrs(contest.info, cvrs)
                val minAssert = contestUA.minClcaAssertion().first
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
                    println("  trial $trial ${contest.name} changed = $count out of ${ccount} = ${df(fuzz)}")
                    println("    errorCounts = ${samples.errorCounts()}")
                    println("    errorRates =  ${samples.errorRates()}")

                    // assertEquals(cvrs.size, samples.errorCounts().sum()) // TODO why would this be true ??

                    val changes = samples.errorCounts().subList(1, samples.errorCounts().size).sum()
                    val changePct = changes / samples.numberOfSamples().toDouble()
                    assertEquals(fuzzPct, changePct, .015) // 1.5% isnt great

                    samples.errorCounts()
                        .forEachIndexed { idx, it -> totalErrorCounts[idx] = totalErrorCounts[idx] + it }
                }
            }

            val total = ntrials * ncontests * cvrs.size.toDouble()
            val avgErrorRates = totalErrorCounts.map { it / total }
            println("  avgErrorRates = ${avgErrorRates}")

            val changes = totalErrorCounts.subList(1, 5).sum()
            val changePct = changes / total
            assertEquals(fuzzPct, changePct, max(fuzzPct / 4, .01))
        }
    }

    @Test
    fun testFuzzedCvrsErrors() {
        val show = false
        val ncontests = 11
        val phantomPct = .02
        val test = MultiContestTestData(ncontests, 1, 50000, hasStyle=true, phantomPctRange=phantomPct..phantomPct)
        val contestsUA = test.contests.map { ContestUnderAudit(it).addStandardAssertions() }
        val cvrs = test.makeCvrsFromContests()
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)

        println("phantomPct = $phantomPct")
        println("              ${ClcaErrorRatesAvg.header()}")

        fuzzPcts.forEach { fuzzPct ->
            val fcvrs = makeFuzzedCvrsFrom(test.contests, cvrs, fuzzPct)
            val testPairs = cvrs.zip(fcvrs)

            val avgErrorRates = ClcaErrorRatesAvg()
            contestsUA.forEach { contestUA ->
                contestUA.clcaAssertions.forEach { cassertion ->
                    val cassorter = cassertion.cassorter
                    val samples = PrevSamplesWithRates(cassorter.noerror())
                    if (show) println("  contest = ${contestUA.id} assertion = ${cassorter.shortName()}")

                    testPairs.forEach { (mvr, cvr) ->
                        if (cvr.hasContest(contestUA.id)) {
                            samples.addSample(cassorter.bassort(mvr, cvr))
                        }
                    }
                    if (show) println("    errorCounts = ${samples.errorCounts()}")
                    if (show) println("    errorRates =  ${samples.errorRates()}")

                    avgErrorRates.add(samples.errorRates())
                }
            }
            println("fuzzPct ${dfn(fuzzPct,3)}: ${avgErrorRates}")
        }
        println()
    }

}
/*
phantomPct = 0.0
                 p2o,    p1o,    p1u,    p2u,    sum,
fuzzPct 0.000: 0.0000, 0.0000, 0.0000, 0.0000, 0.00000
fuzzPct 0.001: 0.0001, 0.0003, 0.0003, 0.0001, 0.00082
fuzzPct 0.005: 0.0006, 0.0012, 0.0016, 0.0007, 0.00412
fuzzPct 0.010: 0.0011, 0.0024, 0.0033, 0.0015, 0.00823
fuzzPct 0.020: 0.0022, 0.0049, 0.0067, 0.0029, 0.01669
fuzzPct 0.050: 0.0056, 0.0121, 0.0166, 0.0071, 0.04133

phantomPct = 0.01
                 p2o,    p1o,    p1u,    p2u,    sum,
fuzzPct 0.000: 0.0000, 0.0099, 0.0000, 0.0000, 0.00990
fuzzPct 0.001: 0.0001, 0.0102, 0.0004, 0.0001, 0.01081
fuzzPct 0.005: 0.0003, 0.0112, 0.0019, 0.0006, 0.01395
fuzzPct 0.010: 0.0007, 0.0124, 0.0037, 0.0011, 0.01786
fuzzPct 0.020: 0.0014, 0.0149, 0.0075, 0.0023, 0.02596
fuzzPct 0.050: 0.0035, 0.0223, 0.0187, 0.0057, 0.05019

phantomPct = 0.02
                 p2o,    p1o,    p1u,    p2u,    sum,
fuzzPct 0.000: 0.0000, 0.0196, 0.0000, 0.0000, 0.01961
fuzzPct 0.001: 0.0001, 0.0199, 0.0004, 0.0001, 0.02044
fuzzPct 0.005: 0.0005, 0.0209, 0.0017, 0.0007, 0.02383
fuzzPct 0.010: 0.0010, 0.0221, 0.0034, 0.0014, 0.02786
fuzzPct 0.020: 0.0020, 0.0246, 0.0069, 0.0027, 0.03629
fuzzPct 0.050: 0.0050, 0.0315, 0.0167, 0.0067, 0.05992
 */

// the diagonals are the unchanged cvrs
fun sumDiagonal(ncands: Int, choices: Map<String, Int>): Int {
    var sum = 0
    for (cand in 0..ncands) {
        val key = cand.toString() + cand.toString()
        if (choices[key] != null) {
            sum += choices[key]!!
        }
    }
    return sum
}

// the diagonals are the changed cvrs
fun sumOffDiagonal(ncands: Int, choices: Map<String, Int>): Int {
    var sum = 0
    for (cand2 in 0..ncands) {
        for (cand1 in 0..ncands) {
            if (cand1 != cand2) {
                val key = cand2.toString() + cand1.toString()
                if (choices[key] != null) {
                    sum += choices[key]!!
                }
            }
        }
    }
    return sum
}

private val elemWidth = 7
fun showChangeMatrix(ncands: Int, choices: Map<String, Int>) = buildString {
    append(sfn(" ", elemWidth))
    repeat(ncands+1) { append( sfn(it.toString(), elemWidth)) }
    appendLine()
    for (cand2 in 0..ncands) {
        append( sfn(cand2.toString(), elemWidth))
        for (cand1 in 0..ncands) {
            val key = cand1.toString() + cand2.toString()
            val change = choices[key]
            append(sfn(change?.toString() ?: "X", elemWidth))
        }
        appendLine()
    }
}

// the diagonals are the unchanged cvrs
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