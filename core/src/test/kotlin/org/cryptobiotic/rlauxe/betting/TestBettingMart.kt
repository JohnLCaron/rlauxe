package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.test.Test

// all tests here are with assort values = noerror
class TestBettingMart {
    val allShow = false

    //// what does populationMeanIfH0 look like as j -> N, when all are noerror?
    @Test
    fun showPopulationMeanIfH0() {
        println()
        println("hitZero = first time (mj <= 0.0) \"true mean certainly greater than hypothesized\"")
        println("hitUpper = first time mj >= upper")
        println("diff1% = how many samples before mj is 1% different from 1/2")
        println()
        for (N in listOf(100, 1000, 10000)) {
            for (margin in listOf(.05, .01, .001)) {
                for (upper in listOf(10.0, 1.0, .67)) { // for upper > 1, 1, < 1
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
            val mj = populationMeanIfH0(N = N, true, tracker)
            if (show) println(
                "${nfn(tracker.numberOfSamples(), 3)}: m=${dfn(mj, 6)} diff from 1/2 = ${
                    dfn(0.5 - mj, 6)}"
            )
            if ((diff1 == 0) && (0.5 - mj > .01)) diff1 = it     // how many samples before mj is 1% different from 1/2
            if ((hitZero == 0) && (mj <= 0.0)) hitZero = it       // first time (mj <= 0.0) "true mean certainly greater than hypothesized"
            if ((hitUpper == 0) && (mj >= upper)) hitUpper = it   // first time mj >= upper
        }
        println("N=$N, margin=$margin, upper=$upper: hitZero=$hitZero hitUpper=$hitUpper diff1% = $diff1")
    }

    //    tj is how much you win or lose
    //    tj = 1 + lamj * (xj - mj)
    //    tj = 1 - lamj * mj when x == 0 (smallest value x can be)
    //
    //    how much above 0 should it be?
    //    limit your bets so at most you lose maxRisk for any one bet:
    //
    //    tj > (1 - maxRisk)
    //    1 - lamj * mj > 1 - maxRisk   when x = 0
    //    lamj * mj < maxRisk
    //    lamj <  maxRisk / mj
    //    maxBet = maxRisk / mj

    // (" i, ${sfn("xs", 6)}, ${sfn("bet", 6)}, ${sfn("tj", 6)}, ${sfn("Tj", 6)}, ${sfn("pvalue", 8)}, ")
    //     x       lam=bet, tj   , Tj    ,p
    // 54, 0.0000, 1.9990, 0.0005, 0.0010, 947.0993, regularCvr-2637,        [1], votes=[0] possible=true pool=null,
    // tj = 1 - 1.9990 * mj = .0005 for mj ~ 1/2   // just lost bet * mui ~ bet/2 = .9995 of winnings.

    // suppose you want to only lose .95 = maxRisk, then max bet = maxRisk*mui ~ .95*2 = lamda = 1.9
    // i see why you focus on p2 errors

    // just bet the maxRisk
    @Test
    fun testMaxRisk() {
        for (N in listOf(1000, 10000, 100000)) {
            println("N=$N =============================================================================================================")
            for (margin in listOf(.05, .01, .001)) {
                for (upper in listOf(10.0, 1.0, .67)) {
                    val minSample = findSamplesNeededUsingMaxRisk(N, margin, upper, 1.0, show=false)
                    for (maxLoss in listOf(.90, .95, .99, .999, .9999, 1.0)) {
                        findSamplesNeededUsingMaxRisk(N, margin, upper, maxLoss, minSamples = minSample)
                    }
                    println()
                }
                println("------------------")
            }
        }
    }

    @Test
    fun showMaxRisk() {
        findSamplesNeededUsingMaxRisk(N = 10000, margin = .05, upper = 1.0, maxLoss = .90)
        findSamplesNeededUsingMaxRisk(N = 10000, margin = .05, upper = 1.0, maxLoss = .99)
        findSamplesNeededUsingMaxRisk(N = 10000, margin = .05, upper = 1.0, maxLoss = .999)
        findSamplesNeededUsingMaxRisk(N = 10000, margin = .05, upper = 1.0, maxLoss = .9999)
    }

    @Test
    fun showUpper() {
        findSamplesNeededUsingMaxRisk(N = 10000, margin = .05, upper = 0.67, maxLoss = .90)
        findSamplesNeededUsingMaxRisk(N = 10000, margin = .05, upper = 0.87, maxLoss = .90)
        findSamplesNeededUsingMaxRisk(N = 10000, margin = .05, upper = 1.0, maxLoss = .90)
        findSamplesNeededUsingMaxRisk(N = 10000, margin = .05, upper = 2.0, maxLoss = .90)
    }

    @Test
    fun showMargins() {
        findSamplesNeededUsingMaxRisk(N = 10000, margin = .05, upper = 1.0, maxLoss = .95)
        findSamplesNeededUsingMaxRisk(N = 10000, margin = .03, upper = 1.0, maxLoss = .95)
        findSamplesNeededUsingMaxRisk(N = 10000, margin = .01, upper = 1.0, maxLoss = .95)
        findSamplesNeededUsingMaxRisk(N = 10000, margin = .005, upper = 1.0, maxLoss = .95)
    }

    @Test
    fun showMarginsAtRisk() {
        findSamplesNeededUsingMaxRisk(N = 10000, margin = .005, upper = 1.0, maxLoss = .9)
        findSamplesNeededUsingMaxRisk(N = 10000, margin = .005, upper = 1.0, maxLoss = .95)
        findSamplesNeededUsingMaxRisk(N = 10000, margin = .005, upper = 1.0, maxLoss = .99)
        findSamplesNeededUsingMaxRisk(N = 10000, margin = .005, upper = 1.0, maxLoss = .999)
        findSamplesNeededUsingMaxRisk(N = 10000, margin = .005, upper = 1.0, maxLoss = .9999)
    }
}

fun findSamplesNeededUsingMaxRisk(N:Int, margin: Double, upper: Double, maxLoss: Double, mui: Double = 0.5,
                                  minSamples: Int? = null, detail: Boolean = false, show: Boolean = true): Int {
    val noerror: Double = 1.0 / (2.0 - margin / upper) // clca assort value when no error
    val maxLambda = maxLoss / mui
    val maxtj: Double = 1.0 + maxLambda * (noerror - mui)
    val tracker = ClcaErrorTracker(noerror, upper)
    var T: Double = 1.0
    var sample = 0

    while (T < 20.0) {
        tracker.addSample(noerror)
        val mj = populationMeanIfH0(N = N, withoutReplacement = true, tracker = tracker)
        val lamda = maxLoss / mj
        val ttj = 1.0 + lamda * (noerror - mj)
        T *= ttj
        sample++
        if (detail) println("${nfn(tracker.numberOfSamples(), 3)}: ttj=${dfn(ttj, 6)} Tj=${dfn(T, 6)}")
    }
    val pct = if (minSamples != null) "; pct = ${df(minSamples / sample.toDouble())}" else ""
    if (show) println("maxLoss: ${df(maxLoss)} N=$N, margin=$margin, upper=$upper noerror:${df(noerror)} maxtj: ${df(maxtj)}: needed $sample samples$pct" )
    return sample
}
