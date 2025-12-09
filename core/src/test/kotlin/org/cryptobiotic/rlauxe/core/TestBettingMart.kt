package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.test.Test

class TestBettingMart {
    val allShow = false

    // what does populationMeanIfH0 look like as j -> N, when all are noerror
    @Test
    fun showPopulationMeanIfH0() {
        for (N in listOf(100, 1000, 10000)) {
            for (margin in listOf(.05, .01, .001)) {
                for (upper in listOf(10.0, 1.0, .67)) {
                    showPopulationMeanIfH0(N, margin, upper)
                }
                println()
            }
        }
    }

    @Test
    fun problem() {
        showPopulationMeanIfH0(1000, .05, 1.0, show = true)
    }

    fun showPopulationMeanIfH0(N: Int, margin: Double, upper: Double, show: Boolean = allShow) {
        val noerror: Double = 1.0 / (2.0 - margin / upper) // clca assort value when no error
        val tracker = ClcaErrorTracker(noerror, upper)
        var diff1: Int = 0
        var hitZero: Int = 0
        var hitUpper: Int = 0
        repeat(N) {
            tracker.addSample(noerror)
            val mj = populationMeanIfH0(N = N, withoutReplacement = true, sampleTracker = tracker)
            if (show) println(
                "${nfn(tracker.numberOfSamples(), 3)}: m=${dfn(mj, 6)} diff from 1/2 = ${
                    dfn(
                        0.5 - mj,
                        6
                    )
                }"
            )
            if ((diff1 == 0) && (0.5 - mj > .01)) diff1 = it
            if ((hitZero == 0) && (mj <= 0.0)) hitZero = it
            if ((hitUpper == 0) && (mj >= upper)) hitUpper = it
        }
        println("N=$N, margin=$margin, upper=$upper: hitZero=$hitZero hitUpper=$hitUpper diff1% = $diff1")
    }


    // val ttj = 1.0 + lamj * (xj - mj)
    // 0 = 1 + lamj * (xj - mj)
    // lamj * (mj - xj) = 1
    // lamj = 1 / (mj - xj)

    // lamj = 1 / (1/2 - xj) // when mj ~= 1/2
    // lamj = 1 / (1 - 2*xj)/2
    // lamj = 2 / (1 - 2*xj)


    // lamj is in (0..2)
    // mj in (0, upper)
    // xj in (0, upper)
    // (mj-xj) in (-upper, upper)

    // ttj = 1 + lamj * (xj - mj)
    // ttj = 1 - lamj * mj when x == 0
    // ttj ~= 0            when x == 0, m ~ 1/2, lam ~= 2

    // suppose you want 1 - lamj * mj > minp; so 1 - minp > lamj * mj; lamj < (1-minp) / mj

    // 54, 0.0000, 1.9990, 0.0005, 0.0010, 947.0993, regularCvr-2637,        [1], votes=[0] possible=true pool=null,
    // tj = 1 - 1.9990 / 2 = .0005    // just lost bet/2 = .9995 of winnings.
    // suppose you want to only lose .95, then max bet = .95*2 = lamda = 1.9
    // i see why you focus on p2 errors

    @Test
    fun testMaxRisk() {
        for (maxRisk in listOf(.90, .95, .99, .999, .9999)) {
            for (N in listOf(100, 1000, 10000)) {
                for (margin in listOf(.05, .01, .001)) {
                    for (upper in listOf(10.0, 1.0, .67)) {
                        findSamplesNeeded(N, margin, upper, maxRisk)
                    }
                }
                println()
            }
        }
    }

    @Test
    fun showMaxRisk() {
        findSamplesNeeded(N = 10000, margin = .05, upper = 1.0, maxRisk = .90, show = false)
        findSamplesNeeded(N = 10000, margin = .05, upper = 1.0, maxRisk = .99, show = false)
        findSamplesNeeded(N = 10000, margin = .05, upper = 1.0, maxRisk = .999, show = false)
        findSamplesNeeded(N = 10000, margin = .05, upper = 1.0, maxRisk = .9999, show = false)
    }

    @Test
    fun showUpper() {
        findSamplesNeeded(N = 10000, margin = .05, upper = 0.67, maxRisk = .90, show = false)
        findSamplesNeeded(N = 10000, margin = .05, upper = 0.87, maxRisk = .90, show = false)
        findSamplesNeeded(N = 10000, margin = .05, upper = 1.0, maxRisk = .90, show = false)
        findSamplesNeeded(N = 10000, margin = .05, upper = 2.0, maxRisk = .90, show = false)
    }

    @Test
    fun showMargins() {
        findSamplesNeeded(N = 10000, margin = .05, upper = 1.0, maxRisk = .95, show = false)
        findSamplesNeeded(N = 10000, margin = .03, upper = 1.0, maxRisk = .95, show = false)
        findSamplesNeeded(N = 10000, margin = .01, upper = 1.0, maxRisk = .95, show = false)
        findSamplesNeeded(N = 10000, margin = .005, upper = 1.0, maxRisk = .95, show = false)
    }

    @Test
    fun showMarginsAtRisk() {
        findSamplesNeeded(N = 10000, margin = .005, upper = 1.0, maxRisk = .9, show = false)
        findSamplesNeeded(N = 10000, margin = .005, upper = 1.0, maxRisk = .99, show = false)
        findSamplesNeeded(N = 10000, margin = .005, upper = 1.0, maxRisk = .999, show = false)
        findSamplesNeeded(N = 10000, margin = .005, upper = 1.0, maxRisk = .9999, show = false)
    }
}

fun findSamplesNeeded(N:Int, margin: Double, upper: Double, maxRisk: Double, show: Boolean = false) {
    val noerror: Double = 1.0 / (2.0 - margin / upper) // clca assort value when no error
    val lamda = 2 * maxRisk
    val tj: Double = 1.0 + lamda * (noerror - 0.5)// clca assort value when no error
    val tracker = ClcaErrorTracker(noerror, upper)
    var T: Double = 1.0
    var sample = 0

    while(T < 20.0) {
        tracker.addSample(noerror)
        val mj = populationMeanIfH0(N = N, withoutReplacement = true, sampleTracker = tracker)
        val ttj = 1.0 + lamda * (noerror - mj)
        T *= ttj
        sample++
        if (show) println("${nfn(tracker.numberOfSamples(), 3)}: ttj=${dfn(ttj, 6)} Tj=${dfn(T, 6)}")
    }
    println("maxRisk: ${df(maxRisk)} N=$N, margin=$margin, upper=$upper noerror:${df(noerror)} tj: ${df(tj)}: needed $sample samples" )
}