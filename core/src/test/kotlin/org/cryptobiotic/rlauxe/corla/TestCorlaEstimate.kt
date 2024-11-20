package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.sampling.estimateSampleSizeSimple
import kotlin.test.Test

class TestCorlaEstimate {
    @Test
    fun showEstimateGentle() {
        showEstimateSimple(0.05)
        showEstimateSimple(0.01)
        showEstimateSimple(0.005)
    }
}

fun showEstimateSimple(dilutedMargin: Double) {
    println("margin=$dilutedMargin")

    runEstimateSimple(dilutedMargin, 0, 0, 0, 0)
    runEstimateSimple(dilutedMargin,1, 0, 0, 0)
    runEstimateSimple(dilutedMargin,10, 0, 0, 0)
    runEstimateSimple(dilutedMargin,100, 0, 0, 0)

    runEstimateSimple(dilutedMargin,0, 1, 0, 0)
    runEstimateSimple(dilutedMargin,0, 10, 0, 0)
    runEstimateSimple(dilutedMargin,0, 100, 0, 0)

    runEstimateSimple(dilutedMargin,0, 0, 1, 0, )
    runEstimateSimple(dilutedMargin,0, 0,10, 0, )
    runEstimateSimple(dilutedMargin,0, 0,100, 0, )

    runEstimateSimple(dilutedMargin,0, 0, 0, 1, )
    runEstimateSimple(dilutedMargin,0, 0, 0, 10, )
    runEstimateSimple(dilutedMargin,0, 0, 0, 100,)

    runEstimateSimple(dilutedMargin,1, 1, 1, 1, )
    runEstimateSimple(dilutedMargin,5, 5, 5, 5, )
    runEstimateSimple(dilutedMargin,10, 10, 10, 10, )
    runEstimateSimple(dilutedMargin,50, 50, 50, 50, )
    runEstimateSimple(dilutedMargin,100, 100, 100, 100,)

    runEstimateSimple(dilutedMargin,1, 10, 11, 12, )
    runEstimateSimple(dilutedMargin,10, 11, 12, 13, )
    runEstimateSimple(dilutedMargin,100, 110, 111, 99,)
    println()
}

// see rla-kotlin compareCorlaToRlauxe testing that our function agrees with Corla's
fun runEstimateSimple(dilutedMargin: Double, twoUnder: Int, oneUnder: Int, oneOver: Int, twoOver: Int,) {
    val riskLimit = 0.05
    val gamma = 1.2

    val rlauxe = estimateSampleSizeSimple(
        riskLimit,
        dilutedMargin,
        gamma,
        twoUnder = twoUnder,
        oneUnder = oneUnder,
        oneOver = oneOver,
        twoOver = twoOver
    )

    println("   [$twoOver, $oneOver, $oneUnder, $twoUnder] -> $rlauxe")
}
