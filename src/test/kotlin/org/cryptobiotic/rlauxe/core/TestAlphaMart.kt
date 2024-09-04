package org.cryptobiotic.rlauxe.core

import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test

class TestAlphaMart {

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
                m,
                reportedRatio = ratio,
                eta0 = sampler.truePopulationMean(),
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
                        "cumulhist=${result.hist!!.cumul()}" +
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
                val result = runAlphaMartRepeated(sampler, m, reportedRatio = ratio, eta0 = ratio, nrepeat = nrepeat)
                if (showRR) {
                    println(result)
                    println(
                        "testFailure actualMean=${actualMean} reportedMean=${"%5.4f".format(ratio)} " +
                                "eta0=${"%5.4f".format(result.eta0)} " +
                                "truePopulationCount=${ff.format(sampler.truePopulationCount())} " +
                                "sampleCount=${df.format(result.sampleCountAvg())} " +
                                // "sampleMean=${"%5.4f".format(result.sampleMean)} " +
                                "cumulhist=${result.hist!!.cumul()}" +
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
        val N = 20_000 // nballots
        val m = N// cutoff
        val nrepeat = 100
        val ratio = .575 // "reported mean"
        val showRR = true

        repeat(20) {
            val sampler = SampleFromArrayWithoutReplacement(generateSample(N, ratio))
            val actualMean = sampler.truePopulationMean()
            val result = runAlphaMartRepeated(sampler, m, reportedRatio = ratio, eta0 = ratio, nrepeat = nrepeat)
            if (showRR) {
                println(result)
                println(
                    "testOne actualMean=${actualMean} reportedMean=${"%5.4f".format(ratio)} " +
                            "eta0=${"%5.4f".format(result.eta0)} " +
                            "truePopulationCount=${ff.format(sampler.truePopulationCount())} " +
                            "truePopulationMean=${"%5.4f".format(sampler.truePopulationMean())} " +
                            "sampleCount=${df.format(result.sampleCountAvg())} " +
                            // "sampleMean=${"%5.4f".format(result.sampleMean)} " +
                            "cumulhist=${result.hist!!.cumul()}" +
                            // "fail=${(result.failPct * nrepeat).toInt()} " +
                            "status=${result.status} "
                )
            }
        }
    }

}