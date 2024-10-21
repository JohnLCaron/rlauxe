package org.cryptobiotic.rlauxe.sim

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.ceilDiv
import org.cryptobiotic.rlauxe.util.Deciles
import org.cryptobiotic.rlauxe.rlaplots.SRT
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
) {
    fun successPct(): Double = 100.0 * nsuccess / (if (ntrials == 0) 1 else ntrials)
    fun failPct(): Double  = 100.0 * (ntrials - nsuccess) / (if (ntrials == 0) 1 else ntrials)
    fun avgSamplesNeeded(): Int  = totalSamplesNeeded / (if (nsuccess == 0) 1 else nsuccess)
    fun pctSamplesNeeded(): Double  = 100.0 * avgSamplesNeeded().toDouble() / (if (N == 0) 1 else N)

    override fun toString() = buildString {
        appendLine("RunTesRepeatedResult: testParameters=$testParameters N=$N successPct=${successPct()} in ntrials=$ntrials")
        append("  $nsuccess successful trials: avgSamplesNeeded=${avgSamplesNeeded()} stddev=${sqrt(variance)}")
        if (percentHist != null) appendLine("  cumulPct:${percentHist.cumulPct()}") else appendLine()
        if (status != null) appendLine("  status:${status}")
    }

    fun makeSRT(N: Int, reportedMean: Double, reportedMeanDiff: Double): SRT {
        return SRT(N, reportedMean, reportedMeanDiff,
            this.testParameters,
            this.nsuccess, this.ntrials, this.totalSamplesNeeded,
            sqrt(this.variance), this.percentHist)
    }

}

fun runTestRepeated(
    drawSample: SampleFn,
    maxSamples: Int,
    ntrials: Int,
    testFn: RiskTestingFn,
    testParameters: Map<String, Double>,
    terminateOnNullReject: Boolean = true,
    showDetails: Boolean = false,
): RunTestRepeatedResult {
    val showH0Result = false
    val N = drawSample.N()

    var totalSamplesNeeded = 0
    var fail = 0
    var nsuccess = 0
    val percentHist = Deciles(ntrials) // bins of 10%
    val status = mutableMapOf<TestH0Status, Int>()
    val welford = Welford()

    repeat(ntrials) {
        drawSample.reset()
        val testH0Result = testFn.testH0(maxSamples, terminateOnNullReject = terminateOnNullReject, showDetails = showDetails) { drawSample.sample() }
        val currCount = status.getOrPut(testH0Result.status) { 0 }
        status[testH0Result.status] = currCount + 1
        if (testH0Result.status.fail) {
            fail++
        } else {
            nsuccess++

            totalSamplesNeeded += testH0Result.sampleCount
            welford.update(testH0Result.sampleCount.toDouble()) // just to keep the stddev

            // sampleCount was what percent of N? keep 10% histogram bins.
            val percent = ceilDiv(100 * testH0Result.sampleCount, N) // percent, rounded up
            percentHist.add(percent)
        }
        if (showH0Result) println(" $it $testH0Result")
    }

    val (_, variance, _) = welford.result()
    return RunTestRepeatedResult(testParameters=testParameters, N=N, totalSamplesNeeded=totalSamplesNeeded, nsuccess=nsuccess,
        ntrials=ntrials, variance, percentHist, status)
}