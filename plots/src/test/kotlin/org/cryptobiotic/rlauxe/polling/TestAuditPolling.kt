package org.cryptobiotic.rlauxe.polling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.plots.plotDDsample
import org.cryptobiotic.rlauxe.rlaplots.SRT
import org.cryptobiotic.rlauxe.workflow.RunTestRepeatedResult
import org.cryptobiotic.rlauxe.rlaplots.makeSRT
import org.cryptobiotic.rlauxe.sampling.PollWithReplacement
import org.cryptobiotic.rlauxe.sampling.PollWithoutReplacement
import org.cryptobiotic.rlauxe.comparison.runAlphaMartRepeated
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test

class TestAuditPolling {

    val showContests = false

    @Test
    fun testPollingWorkflow() {
        val dl = listOf(10, 100, 500, 1000, 2000)
        val margins = listOf(.4, .2, .1, .08, .06, .04, .02, .01) // winning percent: 70, 60, 55, 54, 53, 52, 51, 50.5
        val Nlist = listOf(20000) // listOf(1000, 5000, 10000, 20000, 50000)
        val srs = mutableListOf<SRT>()
        val show = false

        if (show) println("d, N, margin, eta0, without, with, speedup, pctVotes, failWith, sampleSumOver")

        dl.forEach { d ->
            Nlist.forEach { N ->
                margins.forEach { margin ->
                    val cvrs = makeCvrsByExactMean(N, margin2mean(margin))
                    val resultWithout = testPollingWorkflow(margin, withoutReplacement = true, cvrs, d, silent = true).first()
                    val resultWith = testPollingWorkflow(margin, withoutReplacement = false, cvrs, d, silent = true).first()
                    if (show) print("$d, ${cvrs.size}, $margin, ")
                    val speedup = resultWith.avgSamplesNeeded().toDouble() / resultWithout.avgSamplesNeeded().toDouble()
                    val pct = (100.0 * resultWithout.avgSamplesNeeded().toDouble() / N).toInt()

                    if (show) print("${resultWithout.avgSamplesNeeded().toDouble()}, ${resultWith.avgSamplesNeeded().toDouble()}, ${"%5.2f".format(speedup)}, ")
                    if (show) println("${pct}, ${resultWith.failPct()}, ${resultWithout.status}")
                    srs.add(resultWithout.makeSRT(margin2mean(margin), 0.0))
                }
                if (show) println()
            }
        }
        // plotNTpct(srs, "PollingWithoutNT")
        plotDDsample(srs, "PollingWithoutDD")
    }

    fun testPollingWorkflow(margin: Double, withoutReplacement: Boolean, cvrs: List<Cvr>, d: Int, silent: Boolean = true): List<RunTestRepeatedResult> {
        val N = cvrs.size
        if (!silent) println(" d= $d, N=${cvrs.size} margin=$margin ${if (withoutReplacement) "withoutReplacement" else "withReplacement"}")

        // count actual votes
        val votes: Map<Int, Map<Int, Int>> = tabulateVotes(cvrs) // contest -> candidate -> count
        if (!silent && showContests) {
            votes.forEach { key, cands ->
                println("contest ${key} ")
                cands.forEach { println("  ${it} ${it.value.toDouble() / cvrs.size}") }
            }
        }

        // make contests from cvrs
        val contests: List<Contest> = makeContestsFromCvrs(votes, cardsPerContest(cvrs))
        if (!silent && showContests) {
            println("Contests")
            contests.forEach { println("  ${it}") }
        }
        val contestsUA = contests.map { ContestUnderAudit(it, isComparison = false).makePollingAssertions() }

        // this has to be run separately for each assorter, but we want to combine them in practice
        val results = mutableListOf<RunTestRepeatedResult>()
        contestsUA.forEach { contestUA ->
            if (!silent && showContests) println("Assertions for Contest ${contestUA.id}")
            contestUA.pollingAssertions.forEach {
                if (!silent && showContests) println("  ${it}")

                val cvrSampler = if (withoutReplacement) PollWithoutReplacement(contestUA, cvrs, it.assorter) else PollWithReplacement(contestUA, cvrs, it.assorter)

                val result = runAlphaMartRepeated(
                    drawSample = cvrSampler,
                    maxSamples = N,
                    eta0 = margin2mean(margin),
                    d = d,
                    ntrials = 10,
                    withoutReplacement = withoutReplacement,
                    upperBound = it.assorter.upperBound()
                )
                if (!silent) println(result)
                results.add(result)
            }
        }
        return results // TODO only one
    }

// 8/31/2024
// compares well with table 3 of ALPHA
// eta0 = theta, no divergence of sample from true. 100 repetitions
//
// nVotes sampled for population size = 20000 and d (row) vs theta (col),
//     d,  0.505,  0.510,  0.520,  0.550,  0.600,  0.700,
//    10,  14278,   9584,   4215,    836,    164,     47,
//   100,  15038,   9337,   4007,    648,    161,     42,
//   500,  14356,   8336,   3359,    557,    154,     37,
//  1000,  13308,   8573,   3126,    553,    154,     37,
//  2000,  14750,   8216,   3251,    600,    138,     45,

// 9/16/2024 with upperBound, N=20000
// nsamples, d (row) vs theta (col): PollingWithoutDD
//       : 0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.600,  0.700,
//    10,  14494,  12252,   3183,   2089,    906,    702,    284,     55,
//   100,  15347,   9312,   3275,   1413,    670,    690,    132,     48,
//   500,  10340,   9219,   4144,   1733,    527,    609,    138,     30,
//  1000,  14077,   7972,   3180,   1840,    657,    380,    124,     35,
//  2000,  11381,   8237,   2959,   1677,    953,    436,    170,     26,

}