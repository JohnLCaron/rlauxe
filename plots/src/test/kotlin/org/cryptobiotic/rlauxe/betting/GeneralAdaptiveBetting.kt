package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.core.ClcaErrorTracker
import org.cryptobiotic.rlauxe.core.GeneralAdaptiveBetting
import kotlin.test.Test

class GeneralAdaptiveBetting {

    @Test
    fun showGeneralAdaptiveComparisonBet() {
        val N = 10000
        val margins = listOf(.001, .002, .004, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        for (error in listOf(0.0, 0.0001, .001, .01)) {
            println("errors = $error")
            for (margin in margins) {
                val noerror = 1 / (2 - margin)
                // ClcaErrorCounts(val errorCounts: Map<Double, Int>, val totalSamples: Int, val noerror: Double, val upper: Double): ClcaErrorRatesIF {
                //val errorCounts = ClcaErrorCounts(emptyMap(), 0, noerror, 1.0)
                //val optimal = GeneralAdaptiveBettingOld(N = N, errorCounts, d = 100)
                val betFn = GeneralAdaptiveBetting(N, oaErrorRates = null, d = 100, maxRisk = .99)
                val samples = ClcaErrorTracker(noerror, 1.0)
                repeat(100) { samples.addSample(noerror) }
                println(" margin=$margin, noerror=$noerror bet = ${betFn.bet(samples)}")
            }
        }
    }

}