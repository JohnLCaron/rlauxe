package org.cryptobiotic.rlauxe.core

import kotlin.math.max
import kotlin.math.sqrt

data class AlphaMartRepeatedResult(val eta0: Double,      // initial estimate of the population mean
                                   val reportedRatio: Double,   // reported vote ratio
                                   val sampleMean: Double, // actual sample mean
                                   val N: Int, // population size (eg number of ballots)
                                   val ntrials: Int,       // repeat this many times
                                   val nsamplesNeeded: Welford, // avg, variance over ntrials of samples needed
                                   val failPct : Double,     // percent failures over ntrials
                                   val hist: Histogram? = null, // histogram of sample size as percentage of N, count trials in 10% bins
                                   val status: Map<TestH0Status, Int>? = null, // count of the trial status
) {

    fun sampleCountAvg(): Int {
        val (avg, _, _) = nsamplesNeeded.result()
        return avg.toInt()
    }

    override fun toString() = buildString {
        appendLine("AlphaMartRepeatedResult: reportedRatio=$reportedRatio eta0=$eta0 sampleMean=$sampleMean N=$N failPct=$failPct")
        val (avg, v, _) = nsamplesNeeded.result()
        appendLine("  nsample avg=${avg.toInt()} stddev = ${sqrt(v)} over ${ntrials} trials")
        if (hist != null) appendLine("  samplePct:${hist.toStringBinned()}")
        if (status != null) appendLine("  status:${status}")
    }
}

fun runAlphaMartRepeated(
    drawSample: SampleFn,
    maxSamples: Int,
    d: Int = 500,
    f: Double = 0.0,
    reportedRatio: Double,
    eta0: Double,
    withoutReplacement: Boolean = true,
    nrepeat: Int = 1,
    showDetail: Boolean = false

): AlphaMartRepeatedResult {
    val N = drawSample.N()
    val t = 0.5
    val upperBound = 1.0
    val minsd = 1.0e-6
    val c = max(eps, ((eta0 - t) / 2))

    // class TruncShrinkage(val N: Int, val u: Double, val t: Double, val minsd : Double, val d: Int, val eta0: Double,
    //                     val f: Double, val c: Double, val eps: Double): EstimFn {
    val estimFn = TruncShrinkage(N, true, upperBound = upperBound, minsd = minsd, d = d, eta0 = eta0, f = f, c = c)

    val alpha = AlphaMart(
        estimFn = estimFn,
        N = N,
        upperBound = upperBound,
        withoutReplacement = withoutReplacement
    )

    var sampleMeanSum = 0.0
    var fail = 0
    var nsuccess = 0
    val hist = Histogram(10) // bins of 10%
    val status = mutableMapOf<TestH0Status, Int>()
    val welford = Welford()

    repeat(nrepeat) {
        drawSample.reset()
        val testH0Result = alpha.testH0(maxSamples, terminateOnNullReject=true) { drawSample.sample() }
        val currCount = status.getOrPut(testH0Result.status) { 0 }
        status[testH0Result.status] = currCount + 1
        sampleMeanSum += testH0Result.sampleMean
        if (testH0Result.status.fail) {
            fail++
        } else {
            nsuccess++
        }
        welford.update(testH0Result.sampleCount.toDouble())
        if (!testH0Result.status.fail) {
            val percent = ceilDiv(100 * testH0Result.sampleCount, N) // percent, rounded up
            hist.add(percent)
        }
        if (showDetail) println(" $it $testH0Result")
    }

    val failAvg = fail.toDouble() / nrepeat
    val sampleMeanAvg = sampleMeanSum / nrepeat
    return AlphaMartRepeatedResult(eta0=eta0, reportedRatio, sampleMean=sampleMeanAvg, N, nrepeat, welford, failAvg, hist, status)
}