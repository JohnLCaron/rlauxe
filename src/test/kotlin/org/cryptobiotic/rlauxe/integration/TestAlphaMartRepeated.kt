package org.cryptobiotic.rlauxe.integration

import org.cryptobiotic.rlauxe.core.AuditContest
import org.cryptobiotic.rlauxe.core.PluralityAssorter
import org.cryptobiotic.rlauxe.core.PollWithoutReplacement
import org.cryptobiotic.rlauxe.core.SampleFromArrayWithoutReplacement
import org.cryptobiotic.rlauxe.core.Welford
import kotlin.test.Test

class TestAlphaMartRepeated {

    @Test
    fun testWithPopulationMean() { // use the true population mean for eta0:
        val randomMeans = listOf(.505, .51, .52, .53, .55, .60)
        val N = 10_000 // nballots
        val m = 10_000 // cutoff
        val nrepeat = 100

        randomMeans.forEach { ratio ->
            val sampler = SampleFromArrayWithoutReplacement(generateSample(N, ratio))
            val result = runAlphaMartRepeated(
                sampler,
                maxSamples=m,
                theta = sampler.truePopulationMean(),
                eta0 = ratio,
                nrepeat = nrepeat
            )
            val voteDiff = N * (result.eta0 - ratio)
            println(result)
            println(
                "testWithSampleMean ratio=${"%5.4f".format(ratio)} " +
                        "eta0=${"%5.4f".format(result.eta0)} " +
                        "voteDiff=${"%4d".format(voteDiff.toInt())} " +
                        "sampleCount=${df.format(result.sampleCountAvg())} " +
                        // "sampleMean=${"%5.4f".format(result.sampleMean)} " +
                        "cumulPct=${result.hist!!.cumulPct(nrepeat)}" +
                        // "fail=${(result.failPct * nrepeat).toInt()} " +
                        "status=${result.status} "
            )
            println("------------------")
        }
    }

    @Test
    fun testFailure() {
        val N = 10_000 // nballots
        val m = 10_000 // cutoff
        val nrepeat = 100
        val ratio = .505 // "reported mean"
        val showRR = true

        val failPct = Welford()
        var countFail = 0
        while (countFail < 100) {
            val sampler = SampleFromArrayWithoutReplacement(generateSample(N, ratio))
            val actualMean = sampler.truePopulationMean()
            if (actualMean <= 0.5) {
                val result = runAlphaMartRepeated(sampler, m, theta = actualMean, eta0 = ratio, nrepeat = nrepeat)
                if (showRR) {
                    println(result)
                    println(
                        "testFailure actualMean=${actualMean} reportedMean=${"%5.4f".format(ratio)} " +
                                "eta0=${"%5.4f".format(result.eta0)} " +
                                "truePopulationCount=${ff.format(sampler.truePopulationCount())} " +
                                "sampleCount=${df.format(result.sampleCountAvg())} " +
                                // "sampleMean=${"%5.4f".format(result.sampleMean)} " +
                                "cumulPct=${result.hist!!.cumulPct(nrepeat)}" +
                                // "fail=${(result.failPct * nrepeat).toInt()} " +
                                "status=${result.status} "
                    )
                    println("------------------ $countFail")
                }

                countFail++
                if (result.failPct < .95) {
                    println("$countFail: truePopulationCount=${ff.format(sampler.truePopulationCount())} failPct=${result.failPct} status=${result.status}")
                }
                //assertTrue(result.failPct >= .95 )
                failPct.update(result.failPct)
            }
        }
        println("\nfailPct = $failPct")
    }

    @Test
    fun testOne() {
        val N = 10000 // nballots
        val m = N // cutoff
        val nrepeat = 10000
        val theta = .55 // "reported mean"
        val showRR = true

            val cvrs = makeCvrsByExactTheta(N, theta)
            val contest = AuditContest("contest0", 0, listOf(0, 1), listOf(0))
            val assort = PluralityAssorter(contest, 0, 1)
            val sampler = PollWithoutReplacement(cvrs, assort)
            val actualMean = sampler.truePopulationMean()
            val result = runAlphaMartRepeated(sampler, m, theta = actualMean, eta0 = theta, nrepeat = nrepeat, withoutReplacement = true)
            if (showRR) {
                println(result)
                /* println(
                    "testOne N=$N " + // actualMean=${actualMean} reportedMean=${"%5.4f".format(theta)} " +
                            "eta0=${"%5.4f".format(result.eta0)} " +
                            "truePopulationCount=${ff.format(sampler.truePopulationCount())} " +
                            "truePopulationMean=${"%5.4f".format(sampler.truePopulationMean())} " +
                            "sampleCount=${df.format(result.sampleCountAvg())} " +
                            // "sampleMean=${"%5.4f".format(result.sampleMean)} " +
                            "cumulhist=${result.hist!!.cumul()}" +
                            // "fail=${(result.failPct * nrepeat).toInt()} " +
                            "status=${result.status} "
                )

                 */
            }
    }

}