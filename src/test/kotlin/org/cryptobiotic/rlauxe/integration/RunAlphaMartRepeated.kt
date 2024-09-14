package org.cryptobiotic.rlauxe.integration

import org.cryptobiotic.rlauxe.core.AlphaMart
import org.cryptobiotic.rlauxe.core.SampleFn
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.core.TruncShrinkage
import org.cryptobiotic.rlauxe.core.Welford
import org.cryptobiotic.rlauxe.core.ceilDiv
import kotlin.math.max
import kotlin.math.sqrt

// run AlphaMart with TrunkShrinkage in repeated trials
fun runAlphaMartRepeated(
    drawSample: SampleFn,
    maxSamples: Int,
    eta0: Double,
    d: Int = 500,
    f: Double = 0.0,
    withoutReplacement: Boolean = true,
    ntrials: Int = 1,
    upperBound: Double = 1.0,
    showDetail: Boolean = false

): AlphaMartRepeatedResult {
    val N = drawSample.N()
    val t = 0.5
    val minsd = 1.0e-6
    val c = max(eps, ((eta0 - t) / 2))

    // class TruncShrinkage(val N: Int, val u: Double, val t: Double, val minsd : Double, val d: Int, val eta0: Double,
    //                     val f: Double, val c: Double, val eps: Double): EstimFn {
    val estimFn = TruncShrinkage(N, true, upperBound = upperBound, minsd = minsd, d = d, eta0 = eta0, f = f, c = c)

    val alpha = AlphaMart(
        estimFn = estimFn,
        N = N,
        upperBound = upperBound,
        withoutReplacement = withoutReplacement,
    )

    var totalSamplesNeeded = 0
    var fail = 0
    var nsuccess = 0
    val percentHist = Histogram(10) // bins of 10%
    val status = mutableMapOf<TestH0Status, Int>()
    val welford = Welford()

    repeat(ntrials) {
        drawSample.reset()
        val testH0Result = alpha.testH0(maxSamples, terminateOnNullReject=true) { drawSample.sample() }
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
        if (showDetail) println(" $it $testH0Result")
    }

    return AlphaMartRepeatedResult(eta0=eta0, N=N, totalSamplesNeeded=totalSamplesNeeded, nsuccess=nsuccess,
        ntrials=ntrials, welford, percentHist, status)
}

data class AlphaMartRepeatedResult(val eta0: Double,            // initial estimate of the population mean, eg reported vote ratio
                                   val N: Int,                  // population size (eg number of ballots)
                                   val totalSamplesNeeded: Int, // total number of samples needed in nsuccess trials
                                   val nsuccess: Int,           // number of successful trials
                                   val ntrials: Int,            // total number of trials
                                   val nsamplesNeeded: Welford, // avg, variance over ntrials of samples needed
                                   val percentHist: Histogram? = null, // histogram of successful sample size as percentage of N, count trials in 10% bins
                                   val status: Map<TestH0Status, Int>? = null, // count of the trial status
) {
    fun failPct() = 100.0 * (ntrials - nsuccess) / (if (ntrials == 0) 1 else ntrials)

    fun sampleCountAvg(): Int {
        val (avg, _, _) = nsamplesNeeded.result()
        return avg.toInt()
    }

    override fun toString() = buildString {
        appendLine("AlphaMartRepeatedResult: eta0=$eta0 N=$N successPct=${nsuccess.toDouble()/ntrials} in ntrials=$ntrials")
        val (avg, v, _) = nsamplesNeeded.result()
        val ns = if (nsuccess == 0) 1 else nsuccess
        append("  $nsuccess successful trials: avgSamplesNeeded=${totalSamplesNeeded/ns} avg=${avg.toInt()} stddev=${sqrt(v)}")
        if (percentHist != null) appendLine("  cumulPct:${percentHist.cumulPct(ntrials)}") else appendLine()
        if (status != null) appendLine("  status:${status}")
    }
}