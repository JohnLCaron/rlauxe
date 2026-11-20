package org.cryptobiotic.rlauxe.co.corla

import org.cryptobiotic.rlauxe.corla.PluralityErrorRates
import org.cryptobiotic.rlauxe.shangrla.OptimalLambda
import java.lang.Math.pow
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.test.Test

class CompareCorlaAndOptimal {

    @Test
    fun compareCorlaAndOptimal() {
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

    val corla = estimateCorla(
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
    val optimal = estimateSampleSizeCobraOptimalLamda(
        riskLimit,
        dilutedMargin,
        1.0,
        PluralityErrorRates(twoOver / N, oneOver / N, oneUnder / N, twoUnder / N)
    )

    println("   [$twoOver, $oneOver, $oneUnder, $twoUnder] -> $optimal $corla")
}


// From COBRA ??
//
// COBRA equation 1 is a deterministic lower bound on sample size, dependent on margin and risk limit.
// COBRA equation 2 has the maximum expected value for given over/understatement rates.

fun estimateSampleSizeCobraOptimalLamda(
    alpha: Double, // risk
    dilutedMargin: Double, // the difference in votes for the reported winner and reported loser, divided by the total number of ballots cast.
    upperBound: Double, // assort upper value, = 1 for plurality, 1/(2*minFraction) for supermajority
    errorRates: PluralityErrorRates,
): Int {

    //  a := 1 / (2 − v/au)
    //  v := 2Āc − 1 is the diluted margin
    //  au := assort upper value, = 1 for plurality, 1/(2*minFraction) for supermajority

    val noerror = 1 / (2 - dilutedMargin / upperBound)
    val kelly = OptimalLambda(noerror, errorRates)
    val lam = kelly.solve()

    // TODO not sure what this is; seems wrong. its not the bet its the payoff = (1 + lam * (x - mui))
    // 1 / alpha = bet ^ size
    val term1 = -ln(alpha)
    val term2 = ln(lam)
    val r = term1 / term2 // round up

    pow(lam, r)
    val size = ceil(r)
    // println("   lam=$lam r=$r T=$T size=$size")

    return size.toInt()
}
