package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.RiskTestingFn
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.util.*
import kotlin.math.sqrt

private const val showH0Result = false

// single threaded, used for estimating sample size
// runs RiskTestingFn repeatedly, drawSample.reset() gives different permutation for each trial.
fun runTestRepeated(
    drawSample: Sampler,
    ntrials: Int,
    testFn: RiskTestingFn,
    testParameters: Map<String, Double>,
    terminateOnNullReject: Boolean = true,
    startingTestStatistic: Double = 1.0,
    Nc:Int, // maximum cards in the contest
): RunTestRepeatedResult {
    var totalSamplesNeeded = 0
    var fail = 0
    var nsuccess = 0
    val statusMap = mutableMapOf<TestH0Status, Int>()
    val welford = Welford()
    val sampleCounts = mutableListOf<Int>()

    repeat(ntrials) {
        if (it != 0) drawSample.reset() // this creates all the variation for the estimation
        val testH0Result = testFn.testH0(
            maxSamples=drawSample.maxSamples(),
            terminateOnNullReject=terminateOnNullReject,
            startingTestStatistic = startingTestStatistic) { drawSample.sample() }

        val currCount = statusMap.getOrPut(testH0Result.status) { 0 }
        statusMap[testH0Result.status] = currCount + 1

        // samples cant fail (I think), since testH0 can use the entire population, so always gets an answer
        if (testH0Result.status == TestH0Status.LimitReached) {
            println("unexpected failure in sampling, status= ${testH0Result.status}")
            fail++
        } else {
            nsuccess++

            totalSamplesNeeded += testH0Result.sampleCount
            welford.update(testH0Result.sampleCount.toDouble()) // just to keep the stddev

            sampleCounts.add(testH0Result.sampleCount)
        }
        if (showH0Result) println(" $it $testH0Result")
    }

    val (_, variance, _) = welford.result()
    return RunTestRepeatedResult(testParameters=testParameters, Nc=Nc, totalSamplesNeeded=totalSamplesNeeded, nsuccess=nsuccess,
        ntrials=ntrials, variance=variance, statusMap, sampleCounts) // , margin = margin)
}

data class RunTestRepeatedResult(
    val testParameters: Map<String, Double>, // various parameters, depends on the test
    val Nc: Int,                  // population size (eg number of ballots)
    val totalSamplesNeeded: Int, // total number of samples needed in nsuccess trials
    val nsuccess: Int,           // number of successful trials
    val ntrials: Int,            // total number of trials
    val variance: Double,        // variance over ntrials of samples needed
    val status: Map<TestH0Status, Int>? = null, // count of the trial status
    val sampleCount: List<Int> = emptyList(),
) {

    fun successPct(): Double = 100.0 * nsuccess / (if (ntrials == 0) 1 else ntrials)
    fun failPct(): Double  = if (nsuccess == 0) 100.0 else 100.0 * (ntrials - nsuccess) / (if (ntrials == 0) 1 else ntrials)
    fun avgSamplesNeeded(): Int  = totalSamplesNeeded / (if (nsuccess == 0) 1 else nsuccess)
    fun pctSamplesNeeded(): Double  = 100.0 * avgSamplesNeeded().toDouble() / (if (Nc == 0) 1 else Nc)

    override fun toString() = buildString {
        appendLine("RunTestRepeatedResult: testParameters=$testParameters Nc=$Nc successPct=${successPct()} in ntrials=$ntrials")
        append("  $nsuccess successful trials: avgSamplesNeeded=${avgSamplesNeeded()} stddev=${sqrt(variance)}")
        append(showDeciles(sampleCount))
        if (status != null) appendLine("  status:${status}")
    }

    fun findQuantile(quantile: Double): Int {
        return quantile(sampleCount, quantile)
    }

    fun showSampleDist(contestId: Int) = buildString {
        append("  Contest $contestId had $nsuccess successful trials: avgSamplesNeeded=${avgSamplesNeeded()} stddev=${sqrt(variance)}")
        append(showDeciles(sampleCount))
    }
}