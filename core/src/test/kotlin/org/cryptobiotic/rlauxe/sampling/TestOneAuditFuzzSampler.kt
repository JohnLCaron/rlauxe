package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.oneaudit.OneAuditComparisonAssorter
import org.cryptobiotic.rlauxe.oneaudit.OneAuditContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.makeContestOA
import org.cryptobiotic.rlauxe.util.mergeReduceS
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TestOneAuditFuzzSampler {

    @Test
    fun testMakeFuzzedCvrsFromContestOA() {
        val show = false
        val N = 10000
        val fuzzPcts = listOf(.01) // (0.0, 0.001, .005, .01, .02, .05)
        val margins =
            listOf(.05) // .001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        val choiceChanges = mutableListOf<MutableMap<String, Int>>()
        fuzzPcts.forEach { fuzzPct ->
            margins.forEach { margin ->
                // fun makeContestOA(margin: Double, Nc: Int, cvrPercent: Double, skewVotesPercent: Double, undervotePercent: Double, phantomPercent: Double): OneAuditContest {
                val contest = makeContestOA(margin, N, cvrPercent = .70, 0.0, undervotePercent = .01, phantomPercent = .01)
                val cvrs = contest.makeTestCvrs()
                val ncands = contest.ncandidates
                val contestUA: OneAuditContestUnderAudit = contest.makeContestUnderAudit(cvrs)
                val assertion = contestUA.minClcaAssertion()!!
                val cassorter = assertion.cassorter as OneAuditComparisonAssorter // TODO why so complicated?
                val fuzzer = OneAuditFuzzSampler(fuzzPct, cvrs, contestUA, cassorter)
                val cvrPairs = fuzzer.cvrPairs

                if (show) println("ncands = $ncands fuzzPct = $fuzzPct, margin = $margin ${contest.votes}")

                val choiceChange = mutableMapOf<String, Int>()
                cvrPairs.forEach { (cvr, fuzzedCvr) ->
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