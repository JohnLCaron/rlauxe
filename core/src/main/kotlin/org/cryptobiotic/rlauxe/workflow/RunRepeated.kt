package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.RiskTestingFn
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.sampling.SampleGenerator
import org.cryptobiotic.rlauxe.util.*
import kotlin.math.sqrt

data class RunTestRepeatedResult(
    val testParameters: Map<String, Double>, // various parameters, depends on the test
    val N: Int,                  // population size (eg number of ballots)
    val totalSamplesNeeded: Int, // total number of samples needed in nsuccess trials
    val nsuccess: Int,           // number of successful trials
    val ntrials: Int,            // total number of trials
    val variance: Double,        // variance over ntrials of samples needed
    val percentHist: Deciles? = null, // histogram of successful sample size as percentage of N, count trials in 10% bins
    val status: Map<TestH0Status, Int>? = null, // count of the trial status
    val sampleCount: List<Int> = emptyList(),
    val errorRate: List<Double> = emptyList(), // error rates percent
    val margin: Double?,
) {

    fun successPct(): Double = 100.0 * nsuccess / (if (ntrials == 0) 1 else ntrials)
    fun failPct(): Double  = 100.0 * (ntrials - nsuccess) / (if (ntrials == 0) 1 else ntrials)
    fun avgSamplesNeeded(): Int  = totalSamplesNeeded / (if (nsuccess == 0) 1 else nsuccess)
    fun pctSamplesNeeded(): Double  = 100.0 * avgSamplesNeeded().toDouble() / (if (N == 0) 1 else N)
    fun errorRates() = buildString { errorRate.forEach{ append("${df(it)},") } }

    override fun toString() = buildString {
        appendLine("RunTesRepeatedResult: testParameters=$testParameters N=$N successPct=${successPct()} in ntrials=$ntrials")
        append("  $nsuccess successful trials: avgSamplesNeeded=${avgSamplesNeeded()} stddev=${sqrt(variance)}")
        if (percentHist != null) appendLine("  cumulPct:${percentHist.cumulPct()}") else appendLine()
        if (status != null) appendLine("  status:${status}")
    }

    fun findQuantile(quantile: Double): Int {
        return quantile(sampleCount, quantile)
    }
}

fun runTestRepeated(
    drawSample: SampleGenerator,
    maxSamples: Int,
    ntrials: Int,
    testFn: RiskTestingFn,
    testParameters: Map<String, Double>,
    terminateOnNullReject: Boolean = true,
    showDetails: Boolean = false,
    startingTestStatistic: Double = 1.0,
    margin: Double?,
    ): RunTestRepeatedResult {

    val showH0Result = false
    val N = drawSample.N()

    var totalSamplesNeeded = 0
    var fail = 0
    var nsuccess = 0
    val percentHist = Deciles(ntrials) // bins of 10%
    val statusMap = mutableMapOf<TestH0Status, Int>()
    val welford = Welford()
    val sampleCounts = mutableListOf<Int>()
    val errorCounts = mutableListOf(0.0,0.0,0.0,0.0,0.0)

    repeat(ntrials) {
        drawSample.reset()
        val testH0Result = testFn.testH0(maxSamples,
            terminateOnNullReject=terminateOnNullReject,
            showDetails = showDetails,
            startingTestStatistic = startingTestStatistic) { drawSample.sample() }

        val currCount = statusMap.getOrPut(testH0Result.status) { 0 }
        statusMap[testH0Result.status] = currCount + 1
        if (testH0Result.status.fail) {
            fail++
        } else {
            nsuccess++

            totalSamplesNeeded += testH0Result.sampleCount
            welford.update(testH0Result.sampleCount.toDouble()) // just to keep the stddev

            // sampleCount was what percent of N? keep 10% histogram bins.
            val percent = ceilDiv(100 * testH0Result.sampleCount, N) // percent, rounded up
            percentHist.add(percent)
            sampleCounts.add(testH0Result.sampleCount)
        }
        if (testH0Result.samplingErrors.isNotEmpty()) {
            testH0Result.samplingErrors.forEachIndexed { idx, err -> errorCounts[idx] = errorCounts[idx] + err.toDouble()/testH0Result.sampleCount }
        }
        if (showH0Result) println(" $it $testH0Result")
    }

    val (_, variance, _) = welford.result()
    return RunTestRepeatedResult(testParameters=testParameters, N=N, totalSamplesNeeded=totalSamplesNeeded, nsuccess=nsuccess,
        ntrials=ntrials, variance, percentHist, statusMap, sampleCounts, errorCounts.map { 100.0 * it / ntrials}, margin = margin)
}