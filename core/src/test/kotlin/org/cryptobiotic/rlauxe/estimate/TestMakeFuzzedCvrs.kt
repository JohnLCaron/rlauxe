package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.PluralityErrorTracker
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.oneaudit.makeOneContestUA
import org.cryptobiotic.rlauxe.workflow.ClcaWithoutReplacement
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.math.max

class TestMakeFuzzedCvrs {
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
        val samples = PluralityErrorTracker(assort.noerror())
        repeat(ncvrs) {
            samples.addSample(sampler.sample())
        }
        println("  errorCounts = ${samples.pluralityErrorCounts()}")
        println("  errorRates =  ${samples.errorRates()}")
        assertEquals(ncvrs, samples.pluralityErrorCounts().sum())

        val changes = samples.pluralityErrorCounts().subList(1, samples.pluralityErrorCounts().size).sum()
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
                val (contestOA, _, cvrs) = makeOneContestUA(
                    margin,
                    Nc,
                    cvrFraction = .70,
                    undervoteFraction = .01,
                    phantomFraction = .01
                ) // TODO no skew
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
                if (showOA) println(
                    " unchanged=$unchanged = changedPct=$changedPct should be ${1.0 - fuzzPct} diff = ${
                        df(
                            fuzzPct - changedPct
                        )
                    }"
                )
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
                val contestUA = makeContestUAfromCvrs(contest.info, cvrs)
                val minAssert = contestUA.minClcaAssertion()
                if (minAssert != null) repeat(ntrials) { trial ->
                    val minAssort = minAssert.cassorter
                    val samples = PluralityErrorTracker(minAssort.noerror())
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
                    println("    errorCounts = ${samples.pluralityErrorCounts()}")
                    println("    errorRates =  ${samples.errorRates()}")

                    // assertEquals(cvrs.size, samples.errorCounts().sum()) // TODO why would this be true ??

                    val changes = samples.pluralityErrorCounts().subList(1, samples.pluralityErrorCounts().size).sum()
                    val changePct = changes / samples.numberOfSamples().toDouble()
                    assertEquals(fuzzPct, changePct, .015) // 1.5% isnt great

                    samples.pluralityErrorCounts()
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
}

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