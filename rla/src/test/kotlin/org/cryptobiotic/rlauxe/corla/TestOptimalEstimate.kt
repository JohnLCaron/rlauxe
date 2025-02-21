package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.core.ClcaErrorRates
import kotlin.test.Test

class TestOptimalEstimate {
    @Test
    fun testOptimalEstimate() {
        compareCorlaAndOptimal(0.05)
        compareCorlaAndOptimal(0.01)
        compareCorlaAndOptimal(0.005)
    }
}

fun compareCorlaAndOptimal(dilutedMargin: Double) {
    println("margin=$dilutedMargin")

    repeat(100) {
        compareCorlaAndOptimal(dilutedMargin, it, it, it, it)
    }
/*    compareCorlaAndOptimal(dilutedMargin,1, 0, 0, 0)
    compareCorlaAndOptimal(dilutedMargin,10, 0, 0, 0)
    compareCorlaAndOptimal(dilutedMargin,100, 0, 0, 0)

    compareCorlaAndOptimal(dilutedMargin,0, 1, 0, 0)
    compareCorlaAndOptimal(dilutedMargin,0, 10, 0, 0)
    compareCorlaAndOptimal(dilutedMargin,0, 100, 0, 0)

    compareCorlaAndOptimal(dilutedMargin,0, 0, 1, 0, )
    compareCorlaAndOptimal(dilutedMargin,0, 0,10, 0, )
    compareCorlaAndOptimal(dilutedMargin,0, 0,100, 0, )

    compareCorlaAndOptimal(dilutedMargin,0, 0, 0, 1, )
    compareCorlaAndOptimal(dilutedMargin,0, 0, 0, 10, )
    compareCorlaAndOptimal(dilutedMargin,0, 0, 0, 100,)

    compareCorlaAndOptimal(dilutedMargin,1, 1, 1, 1, )
    compareCorlaAndOptimal(dilutedMargin,5, 5, 5, 5, )
    compareCorlaAndOptimal(dilutedMargin,10, 10, 10, 10, )
    compareCorlaAndOptimal(dilutedMargin,50, 50, 50, 50, )
    compareCorlaAndOptimal(dilutedMargin,100, 100, 100, 100,)

    compareCorlaAndOptimal(dilutedMargin,1, 10, 11, 12, )
    compareCorlaAndOptimal(dilutedMargin,10, 11, 12, 13, )
    compareCorlaAndOptimal(dilutedMargin,100, 110, 111, 99,) */
    println()
}

fun compareCorlaAndOptimal(dilutedMargin: Double, twoOver: Int, oneOver: Int, oneUnder: Int, twoUnder: Int,) {
    val riskLimit = 0.05
    val gamma = 1.2

    val corla = estimateSampleSizeSimple(
        riskLimit,
        dilutedMargin,
        gamma,
        twoOver = twoOver,
        oneOver = oneOver,
        oneUnder = oneUnder,
        twoUnder = twoUnder,
    )

    // fun estimateSampleSizeOptimalLambda(
    //    alpha: Double, // risk
    //    dilutedMargin: Double, // the difference in votes for the reported winner and reported loser, divided by the total number of ballots cast.
    //    upperBound: Double, // assort upper value, = 1 for plurality, 1/(2*minFraction) for supermajority
    //    p1: Double, p2: Double, p3: Double = 0.0, p4: Double = 0.0
    // TODO why does this need a rate and other number ?

    val N = 1000.0
    val optimal = estimateSampleSizeOptimalLambda(
        riskLimit,
        dilutedMargin,
        1.0,
        ClcaErrorRates(twoOver/N, oneOver/N, oneUnder/N, twoUnder/N)
    )

    println("   [$twoOver, $oneOver, $oneUnder, $twoUnder] -> $optimal $corla")
}
