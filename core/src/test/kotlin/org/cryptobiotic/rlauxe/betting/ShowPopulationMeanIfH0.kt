package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.test.Test

// all tests here are with assort values = noerror
class ShowPopulationMeanIfH0 {
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
                    dfn(0.5 - mj, 6)
                }"
            )
            if ((diff1 == 0) && (0.5 - mj > .01)) diff1 = it     // how many samples before mj is 1% different from 1/2
            if ((hitZero == 0) && (mj <= 0.0)) hitZero =
                it       // first time (mj <= 0.0) "true mean certainly greater than hypothesized"
            if ((hitUpper == 0) && (mj >= upper)) hitUpper = it   // first time mj >= upper
        }
        println("N=$N, margin=$margin, upper=$upper: hitZero=$hitZero hitUpper=$hitUpper diff1% = $diff1")
    }
}