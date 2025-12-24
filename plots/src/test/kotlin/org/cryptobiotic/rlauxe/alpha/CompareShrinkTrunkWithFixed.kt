package org.cryptobiotic.rlauxe.alpha

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.workflow.PollingSampling
import org.cryptobiotic.rlauxe.workflow.Sampling
import org.cryptobiotic.rlauxe.estimate.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.makeStandardPluralityAssorter
import org.cryptobiotic.rlauxe.estimate.RunTestRepeatedResult
import org.cryptobiotic.rlauxe.estimate.runTestRepeated
import org.cryptobiotic.rlauxe.util.makeContestsFromCvrs
import org.cryptobiotic.rlauxe.util.mean2margin

import kotlin.test.Test

// CANDIDATE FOR REMOVAL

class CompareShrinkTrunkWithFixed {

    @Test
    fun compareShrinkTruncWithFixedExact() {
        val etas = listOf(.505, .51, .52, .55, .6, .7) // alternative means
        val N = 10000
        etas.forEach { eta ->
            val cvrs = makeCvrsByExactMean(N, eta)
            val pairs = cvrs.zip(cvrs)

            val contestUA = ContestWithAssertions(makeContestsFromCvrs(cvrs).first()).addStandardAssertions()

            val sampleFn = PollingSampling(contestUA.id, pairs, makeStandardPluralityAssorter(N))

            println("\neta0 = $eta")
            val fixResult = testAlphaMartFixed(eta, sampleFn)
            println("fixResult=$fixResult")

            sampleFn.reset()
            val alphaResult = testAlphaMartTrunc(eta, sampleFn)
            println("alphaResult=$alphaResult")
        }
    }

    fun testAlphaMartTrunc(eta0: Double, sampling: Sampling): TestH0Result {
        val u = 1.0
        val d = 10000
        val N = sampling.maxSamples()

        val trunc = TruncShrinkage(N = N, upperBound = u, d = d, eta0 = eta0)
        val alpha = AlphaMart(estimFn = trunc, N = N)
        val tracker = ClcaErrorTracker(0.0, 1.0)

        return alpha.testH0(N, true, tracker=tracker) { sampling.sample() }
    }

    fun testAlphaMartFixed(eta0: Double, sampling: Sampling): TestH0Result {
        val fixed = FixedEstimFn(eta0 = eta0)
        val alpha = AlphaMart(estimFn = fixed, N = sampling.maxSamples())
        val tracker = ClcaErrorTracker(0.0, 1.0)

        return alpha.testH0(sampling.maxSamples(), true, tracker=tracker) { sampling.sample() }
    }

    @Test
    fun compareShrinkTruncWithFixedRepeated() {
        val etas = listOf(.505, .51, .52, .53, .54, .55, .6, .7) // alternative means
        val N = 10000
        val ntrials = 1000
        val fixResults = mutableListOf<RunTestRepeatedResult>()
        val truncResults = mutableListOf<RunTestRepeatedResult>()

        etas.forEach { eta ->
            val cvrs = makeCvrsByExactMean(N, eta)
            val pairs = cvrs.zip(cvrs)

            val contestUA = ContestWithAssertions(makeContestsFromCvrs(cvrs).first()).addStandardAssertions()
            val sampleFn = PollingSampling(contestUA.id, pairs, makeStandardPluralityAssorter(N))

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
        // calcMean(" success10percent", fixResults, truncResults) { it.percentHist!!.cumul(10) }
        // calcMean(" success20percent", fixResults, truncResults) { it.percentHist!!.cumul(20) }
        // calcMean(" success30percent", fixResults, truncResults) { it.percentHist!!.cumul(30) }
        // calcMean(" success40percent", fixResults, truncResults) { it.percentHist!!.cumul(40) }
        // calcMean(" success50percent", fixResults, truncResults) { it.percentHist!!.cumul(50) }
    }

    fun calcMean(title: String, fixResults : List<RunTestRepeatedResult>, truncResults : List<RunTestRepeatedResult>,
                 fld: (RunTestRepeatedResult) -> Double) {
        val fixFld = mutableListOf<Double>()
        val truncFld = mutableListOf<Double>()
        fixResults.forEachIndexed{ idx, fixResult ->
            fixFld.add(fld(fixResult))
            truncFld.add(fld(truncResults[idx]))
        }
        println("ArithmeticMean for $title: fix=${fixFld.average().toInt()}, trunc=${truncFld.average().toInt()}")
        // println("GeometricMean for $title: fix=${geometricMean(fixFld)}, trunc=${geometricMean(truncFld)}")
    }

    fun runAlphaMartTruncRepeated(eta0: Double, sampling: Sampling, ntrials: Int): RunTestRepeatedResult {
        val u = 1.0
        val d = 10000
        val N = sampling.maxSamples()

        val trunc = TruncShrinkage(N = N, upperBound = u, d = d, eta0 = eta0)
        val alpha = AlphaMart(estimFn = trunc, N = sampling.maxSamples())

        return runTestRepeated(
            name = "runAlphaMartTruncRepeated",
            drawSample = sampling,
            terminateOnNullReject = true,
            ntrials = ntrials,
            testFn = alpha,
            testParameters = mapOf("eta0" to eta0, "d" to d.toDouble(), "margin" to mean2margin(eta0)),
            N=N,
            tracker = ClcaErrorTracker(0.0, 1.0),
            )
    }

    fun runAlphaMartFixedRepeated(eta0: Double, sampling: Sampling, ntrials: Int): RunTestRepeatedResult {
        val N = sampling.maxSamples()
        val fixed = FixedEstimFn(eta0 = eta0)
        val alpha = AlphaMart(estimFn = fixed, N = N)

        return runTestRepeated(
            name = "runAlphaMartFixedRepeated",
            drawSample = sampling,
            terminateOnNullReject = true,
            ntrials = ntrials,
            testFn = alpha,
            testParameters = mapOf("eta0" to eta0, "margin" to mean2margin(eta0)),
            N=N,
            tracker = ClcaErrorTracker(0.0, 1.0),
            )
    }
}