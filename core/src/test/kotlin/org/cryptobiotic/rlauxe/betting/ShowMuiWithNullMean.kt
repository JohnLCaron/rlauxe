package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.math.abs
import kotlin.test.Test

// all tests here are with assort values = noerror
class ShowMuiWithNullMean {
    val allShow = true

    //// how does mui change with different null means?
    @Test
    fun showPopulationMeanIfH0() {
        println()
        println("hitZero = first time (mj <= 0.0) \"true mean certainly greater than hypothesized\"")
        println("hitUpper = first time mj >= upper \"true mean is certainly less than 1/2\"")
        println("diff1% = how many samples before mj is 1% different from eta")
        for (N in listOf(200)) {
            for (margin in listOf(.01)) {
                for (eta in listOf(.45, .50, .55)) { // for upper > 1, 1, < 1
                    showPopulationMeanIfH0(N, margin, eta)
                }
                println()
            }
        }
    }

    @Test
    fun problem() {
        showPopulationMeanIfH0(1000, .05, 1.0, show = true)
    }

    fun showPopulationMeanIfH0(N: Int, margin: Double, eta: Double, show: Boolean = allShow) {
        val noerror: Double = 1.0 / (2.0 - margin) // clca assort value when no error
        val tracker = ClcaErrorTracker(noerror, 1.0)
        var diff1: Int = 0
        var hitZero: Int = 0
        var hitUpper: Int = 0
        println("eta=$eta noerror=$noerror")
        repeat(N) {
            tracker.addSample(noerror)
            val mj = populationMeanIfH0eta(N = N, eta, tracker)
            if (show) println(
                " ${nfn(tracker.numberOfSamples(), 3)}: m=${dfn(mj, 6)} diff from 1/2 = ${dfn(0.5 - mj, 6)}"
            )
            if ((diff1 == 0) && (abs(eta - mj) > .01)) diff1 = it     // how many samples before mj is 1% different from 1/2
            if ((hitZero == 0) && (mj <= 0.0)) hitZero = it               // first time (mj <= 0.0) "true mean certainly greater than hypothesized"
            if ((hitUpper == 0) && (mj >= 1.0)) hitUpper = it             // first time mj >= upper
        }
        println("eta=$eta: N=$N, margin=$margin, hitZero=$hitZero hitUpper=$hitUpper diff1% = $diff1")
        println()
    }
}