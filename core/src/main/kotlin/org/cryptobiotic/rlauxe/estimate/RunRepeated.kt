package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.betting.ClcaSamplerErrorTracker
import org.cryptobiotic.rlauxe.betting.RiskMeasuringFn
import org.cryptobiotic.rlauxe.betting.SamplerTracker
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.util.*
import kotlin.math.sqrt

private const val showH0Result = false
private val logger = KotlinLogging.logger("runTestRepeated")

// single threaded, used for estimating sample size
// runs RiskTestingFn repeatedly, drawSample.reset() gives different permutation for each trial.
fun runRepeated(
    name: String,
    ntrials: Int,
    testFn: RiskMeasuringFn,
    testParameters: Map<String, Double>,
    terminateOnNullReject: Boolean = true,
    startingTestStatistic: Double = 1.0, // T, must grow to 1/riskLimit
    samplerTracker: SamplerTracker,
    N:Int, // maximum cards in the contest (diluted)
): RunRepeatedResult {
    var totalSamplesNeeded = 0
    var fail = 0
    var nsuccess = 0
    val statusMap = mutableMapOf<TestH0Status, Int>()
    val welford = Welford()
    val sampleCounts = mutableListOf<Int>()

    repeat(ntrials) { trial ->
        if (trial != 0) {
            samplerTracker.reset() // this creates all the variation for the estimation
        } /* else {
            val t = samplerTracker as ClcaSamplerErrorTracker
            if (t.contestId == 52 && t.cassorter.shortName() == "154/155") {
                val round = if (startingTestStatistic == 1.0) 1 else 2
                samplerTracker.dump("/home/stormy/rla/tests/scratch/est52-154-155-$round.txt")
            }
        } */

        val testH0Result = testFn.testH0(
            maxSamples=samplerTracker.maxSamples(),
            terminateOnNullReject=terminateOnNullReject,
            startingTestStatistic = startingTestStatistic,
       ) { samplerTracker.sample() }

        val currCount = statusMap.getOrPut(testH0Result.status) { 0 }
        statusMap[testH0Result.status] = currCount + 1

        // this can fail when you have limited the number of samples
        if (testH0Result.status == TestH0Status.LimitReached) {
            fail++
            logger.warn { "$name:  $trial failed in sampling max= ${samplerTracker.maxSamples()} samples" }
        } else {
            nsuccess++

            totalSamplesNeeded += testH0Result.sampleCount
            welford.update(testH0Result.sampleCount.toDouble()) // just to keep the stddev

            sampleCounts.add(testH0Result.sampleCount)
        }
        if (showH0Result) logger.debug{ Result.toString() }
    }

    if (fail > 0) {
        logger.warn { "$name:  $fail/$ntrials failures in sampling max= ${samplerTracker.maxSamples()} samples" }
    }

    val (_, variance, _) = welford.result()
    return RunRepeatedResult(testParameters=testParameters, N=N, totalSamplesNeeded=totalSamplesNeeded, nsuccess=nsuccess,
        ntrials=ntrials, variance=variance, statusMap, sampleCounts) // , margin = margin)
}

data class RunRepeatedResult(
    val testParameters: Map<String, Double>, // various parameters, depends on the test
    val N: Int,                  // population size (eg number of ballots)
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
    fun pctSamplesNeeded(): Double  = 100.0 * avgSamplesNeeded().toDouble() / (if (N == 0) 1 else N)

    override fun toString() = buildString {
        appendLine("RunTestRepeatedResult: testParameters=$testParameters N=$N successPct=${successPct()} in ntrials=$ntrials")
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