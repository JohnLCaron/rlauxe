package org.cryptobiotic.rlauxe.integration

import org.cryptobiotic.rlauxe.core.AlphaMart
import org.cryptobiotic.rlauxe.core.FixedEstimFn
import org.cryptobiotic.rlauxe.core.PollWithoutReplacement
import org.cryptobiotic.rlauxe.core.SampleFn
import org.cryptobiotic.rlauxe.core.TestH0Result
import org.cryptobiotic.rlauxe.core.TruncShrinkage
import org.cryptobiotic.rlauxe.core.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.core.eps
import org.cryptobiotic.rlauxe.makeStandardPluralityAssorter
import org.cryptobiotic.rlauxe.sim.AlphaMartRepeatedResult
import org.cryptobiotic.rlauxe.sim.runAlphaEstimRepeated
import kotlin.math.max
import kotlin.test.Test

class CompareShrinkTrunkWithFixed {

    @Test
    fun compareShrinkTruncWithFixedExact() {
        val etas = listOf(.505, .51, .52, .55, .6, .7) // alternative means
        val N = 10000
        etas.forEach { eta ->
            val cvrs = makeCvrsByExactMean(N, eta)
            val sampleFn = PollWithoutReplacement(cvrs, makeStandardPluralityAssorter())

            println("\neta0 = $eta")
            val fixResult = testAlphaMartFixed(eta, sampleFn)
            println("fixResult=$fixResult")

            sampleFn.reset()
            val alphaResult = testAlphaMartTrunc(eta, sampleFn)
            println("alphaResult=$alphaResult")
        }
    }

    fun testAlphaMartTrunc(eta0: Double, sampleFn: SampleFn): TestH0Result {
        val u = 1.0
        val d = 10000
        val f = 0.0
        val minsd = 1.0e-6
        val t = 0.5
        val c = max(eps, (eta0 - t) / 2)
        val N = sampleFn.N()

        val trunc = TruncShrinkage(N = N, upperBound = u, minsd = minsd, d = d, eta0 = eta0, f = f, c = c)
        val alpha = AlphaMart(estimFn = trunc, N = sampleFn.N())
        return alpha.testH0(sampleFn.N(), true) { sampleFn.sample() }
    }

    fun testAlphaMartFixed(eta0: Double, sampleFn: SampleFn): TestH0Result {
        val fixed = FixedEstimFn(eta0 = eta0)
        val alpha = AlphaMart(estimFn = fixed, N = sampleFn.N())
        return alpha.testH0(sampleFn.N(), true) { sampleFn.sample() }
    }

    @Test
    fun compareShrinkTruncWithFixedRepeated() {
        val etas = listOf(.505, .51, .52, .53, .54, .55, .6, .7) // alternative means
        val N = 10000
        val ntrials = 1000
        val fixResults = mutableListOf<AlphaMartRepeatedResult>()
        val truncResults = mutableListOf<AlphaMartRepeatedResult>()

        etas.forEach { eta ->
            val cvrs = makeCvrsByExactMean(N, eta)
            val sampleFn = PollWithoutReplacement(cvrs, makeStandardPluralityAssorter())

            println("\neta0 = $eta")
            val fixResult = runAlphaMartFixedRepeated(eta, sampleFn, ntrials)
            println("fixResult=$fixResult")
            fixResults.add(fixResult)

            sampleFn.reset()
            val truncResult = runAlphaMartTruncRepeated(eta, sampleFn, ntrials)
            println("truncResult=$truncResult")
            truncResults.add(truncResult)
        }

        calcMean(" pctSamplesNeeded", fixResults, truncResults) { it.pctSamplesNeeded() }
        calcMean(" success10percent", fixResults, truncResults) { it.percentHist!!.cumul(10) }
        calcMean(" success20percent", fixResults, truncResults) { it.percentHist!!.cumul(20) }
        calcMean(" success30percent", fixResults, truncResults) { it.percentHist!!.cumul(30) }
        calcMean(" success40percent", fixResults, truncResults) { it.percentHist!!.cumul(40) }
        calcMean(" success50percent", fixResults, truncResults) { it.percentHist!!.cumul(50) }
    }

    fun calcMean(title: String, fixResults : List<AlphaMartRepeatedResult>, truncResults : List<AlphaMartRepeatedResult>,
                 fld: (AlphaMartRepeatedResult) -> Double) {
        val fixFld = mutableListOf<Double>()
        val truncFld = mutableListOf<Double>()
        fixResults.forEachIndexed{ idx, fixResult ->
            fixFld.add(fld(fixResult))
            truncFld.add(fld(truncResults[idx]))
        }
        println("ArithmeticMean for $title: fix=${fixFld.average().toInt()}, trunc=${truncFld.average().toInt()}")
        // println("GeometricMean for $title: fix=${geometricMean(fixFld)}, trunc=${geometricMean(truncFld)}")
    }

    fun runAlphaMartTruncRepeated(eta0: Double, sampleFn: SampleFn, ntrials: Int): AlphaMartRepeatedResult {
        val u = 1.0
        val d = 10000
        val f = 0.0
        val minsd = 1.0e-6
        val t = 0.5
        val c = max(eps, (eta0 - t) / 2)
        val N = sampleFn.N()

        val trunc = TruncShrinkage(N = N, upperBound = u, minsd = minsd, d = d, eta0 = eta0, f = f, c = c)
        val alpha = AlphaMart(estimFn = trunc, N = sampleFn.N())

        return runAlphaEstimRepeated(
            drawSample = sampleFn,
            maxSamples = N,
            terminateOnNullReject = true,
            ntrials = ntrials,
            showDetail = false,
            alphaMart = alpha,
            eta0 = eta0,
        )
    }

    fun runAlphaMartFixedRepeated(eta0: Double, sampleFn: SampleFn, ntrials: Int): AlphaMartRepeatedResult {
        val N = sampleFn.N()
        val fixed = FixedEstimFn(eta0 = eta0)
        val alpha = AlphaMart(estimFn = fixed, N = N)

        return runAlphaEstimRepeated(
            drawSample = sampleFn,
            maxSamples = N,
            terminateOnNullReject = true,
            ntrials = ntrials,
            showDetail = false,
            alphaMart = alpha,
            eta0 = eta0,
        )
    }
}