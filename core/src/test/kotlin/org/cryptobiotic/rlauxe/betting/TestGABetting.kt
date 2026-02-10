package org.cryptobiotic.rlauxe.betting

import kotlin.test.Test
import kotlin.test.assertEquals

// exoplore this function
// log T_i = ln(1.0 + lamda * (noerror - mui)) * p0  + Sum { ln(1.0 + lamda * (assortValue_k - mui)) * p_k; over error type k }
//          + Sum { ln(1.0 + lamda * (assortValue_pk - mui)) * p_pk; over pools and pool types }              (eq 2)

class TestGABetting {
    val N = 10000
    val margin = .01
    val upper = 1.0
    val maxLoss = .9

    @Test
    fun testPhantoms() {
        val r1 = makeBet(0)
        println()
        val r2 = makeBet(10)
    }

    fun makeBet(
        nphantoms: Int
    ): Double {
        println("nphantoms = $nphantoms")
        val noerror: Double = 1.0 / (2.0 - margin / upper)

        val gaBetting = GeneralAdaptiveBetting(
            N,
            startingErrors = ClcaErrorCounts.empty(noerror, upper),
            nphantoms = nphantoms,
            oaAssortRates = null, d = 0, maxLoss = maxLoss, debug = false
        )
        println("bassort values = ${gaBetting.startingErrors.bassortValues()}")

        val tracker = ClcaErrorTracker(noerror, upper)
        repeat(1000) { tracker.addSample(noerror) }

        repeat(1) { tracker.addSample(0.0 * noerror) }
        repeat(10) { tracker.addSample(0.5 * noerror) }
        repeat(10) { tracker.addSample(1.5 * noerror) }
        repeat(1) { tracker.addSample(2.0 * noerror) }
        println("errorCounts = ${tracker.measuredClcaErrorCounts().show()}")
        assertEquals(listOf(1, 10, 10, 1), tracker.measuredClcaErrorCounts().errorCounts().map { it.value })

        val rates = gaBetting.estimatedErrorRates(tracker.measuredClcaErrorCounts())
        println("rates = $rates")

        // get optimal bet
        val bet = gaBetting.bet(tracker)
        println("optimal bet = $bet")
        return bet
    }
}