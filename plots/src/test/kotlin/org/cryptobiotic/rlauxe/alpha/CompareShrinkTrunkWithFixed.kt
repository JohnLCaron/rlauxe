package org.cryptobiotic.rlauxe.alpha

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.audit.PollWithoutReplacement
import org.cryptobiotic.rlauxe.audit.Sampler
import org.cryptobiotic.rlauxe.estimate.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.makeStandardPluralityAssorter
import org.cryptobiotic.rlauxe.estimate.RunTestRepeatedResult
import org.cryptobiotic.rlauxe.estimate.runTestRepeated
import org.cryptobiotic.rlauxe.util.makeContestsFromCvrs
import org.cryptobiotic.rlauxe.util.mean2margin

import kotlin.math.max
import kotlin.test.Test

// CANDIDATE FOR REMOVAL

class CompareShrinkTrunkWithFixed {

    @Test
    fun compareShrinkTruncWithFixedExact() {
        val etas = listOf(.505, .51, .52, .55, .6, .7) // alternative means
        val N = 10000
        etas.forEach { eta ->
            val cvrs = makeCvrsByExactMean(N, eta)
            val contestUA = ContestUnderAudit(makeContestsFromCvrs(cvrs).first())

            val sampleFn = PollWithoutReplacement(contestUA.id, true, cvrs, makeStandardPluralityAssorter(N))

            println("\neta0 = $eta")
            val fixResult = testAlphaMartFixed(eta, sampleFn)
            println("fixResult=$fixResult")

            sampleFn.reset()
            val alphaResult = testAlphaMartTrunc(eta, sampleFn)
            println("alphaResult=$alphaResult")
        }
    }

    fun testAlphaMartTrunc(eta0: Double, sampler: Sampler): TestH0Result {
        val u = 1.0
        val d = 10000
        val t = 0.5
        val c = max(eps, (eta0 - t) / 2)
        val N = sampler.maxSamples()

        val trunc = TruncShrinkage(N = N, upperBound = u, d = d, eta0 = eta0, c = c)
        val alpha = AlphaMart(estimFn = trunc, N = N)
        return alpha.testH0(N, true) { sampler.sample() }
    }

    fun testAlphaMartFixed(eta0: Double, sampler: Sampler): TestH0Result {
        val fixed = FixedEstimFn(eta0 = eta0)
        val alpha = AlphaMart(estimFn = fixed, N = sampler.maxSamples())
        return alpha.testH0(sampler.maxSamples(), true) { sampler.sample() }
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
            val contestUA = ContestUnderAudit(makeContestsFromCvrs(cvrs).first())

            val sampleFn = PollWithoutReplacement(contestUA.id, true, cvrs, makeStandardPluralityAssorter(N))

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

    fun runAlphaMartTruncRepeated(eta0: Double, sampler: Sampler, ntrials: Int): RunTestRepeatedResult {
        val u = 1.0
        val d = 10000
        val t = 0.5
        val c = max(eps, (eta0 - t) / 2)
        val N = sampler.maxSamples()

        val trunc = TruncShrinkage(N = N, upperBound = u, d = d, eta0 = eta0, c = c)
        val alpha = AlphaMart(estimFn = trunc, N = sampler.maxSamples())

        return runTestRepeated(
            drawSample = sampler,
            // maxSamples = N,
            terminateOnNullReject = true,
            ntrials = ntrials,
            testFn = alpha,
            testParameters = mapOf("eta0" to eta0, "d" to d.toDouble()),
            margin = mean2margin(eta0),
            Nc=N,
        )
    }

    fun runAlphaMartFixedRepeated(eta0: Double, sampler: Sampler, ntrials: Int): RunTestRepeatedResult {
        val N = sampler.maxSamples()
        val fixed = FixedEstimFn(eta0 = eta0)
        val alpha = AlphaMart(estimFn = fixed, N = N)

        return runTestRepeated(
            drawSample = sampler,
            // maxSamples = N,
            terminateOnNullReject = true,
            ntrials = ntrials,
            testFn = alpha,
            testParameters = mapOf("eta0" to eta0),
            margin = mean2margin(eta0),
            Nc=N,
            )
    }
}