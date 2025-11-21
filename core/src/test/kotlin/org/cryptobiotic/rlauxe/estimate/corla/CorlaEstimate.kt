package org.cryptobiotic.rlauxe.estimate.corla

import org.cryptobiotic.rlauxe.core.PluralityErrorRates
import org.cryptobiotic.rlauxe.core.OptimalLambda
import java.lang.Math.pow
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max

/**
 * From colorado-rla Audit.optimistic().
 * Based on SuperSimple paper, generalization of equations in section 2.
 *
 * @param gamma the "error inflator" parameter. error inflation factor γ ≥ 100%.
 *   γ controls a tradeoff between initial sample size and the amount of additional counting required when the
 *   sample finds too many overstatements, especially two-vote overstatements.
 *   The larger γ is, the larger the initial sample needs to be, but the less additional counting will be required
 *   if the sample finds a two-vote overstatement or a large number of one-vote maximum overstatements. (paper has 1.1)
 * @param twoOver the number of two-vote overstatements
 * @param oneOver the number of one-vote overstatements
 * @param oneUnder the number of one-vote understatements
 * @param twoUnder the number of two-vote understatements
 */
fun estimateSampleSizeSimple(
    riskLimit: Double,
    dilutedMargin: Double,
    gamma: Double = 1.03,
    twoOver: Int = 0,
    oneOver: Int = 0,
    oneUnder: Int = 0,
    twoUnder: Int = 0,
): Int {
    val two_under_term = twoUnder * ln( 1 + 1 / gamma)
    val one_under_term = oneUnder * ln( 1 + 1 / (2 * gamma))
    val one_over_term = oneOver * ln( 1 - 1 / (2 * gamma))
    val two_over_term = twoOver * ln( 1 - 1 / gamma)

    // "sample-size multiplier" rho is independent of margin
    val rho: Double = -(2.0 * gamma) * (ln(riskLimit) + two_under_term + one_under_term + one_over_term + two_over_term)
    val r = ceil(rho / dilutedMargin)  // round up
    val over_under_sum = (twoUnder + oneUnder + oneOver + twoOver).toDouble()
    // println("   rho=$rho r=$r")
    return max(r, over_under_sum).toInt()
}

// COBRA equation 1 is a deterministic lower bound on sample size, dependent on margin and risk limit.
// COBRA equation 2 has the maximum expected value for given over/understatement rates. See OptimalLambda class for implementation.

fun estimateSampleSizeOptimalLambda(
    alpha: Double, // risk
    dilutedMargin: Double, // the difference in votes for the reported winner and reported loser, divided by the total number of ballots cast.
    upperBound: Double, // assort upper value, = 1 for plurality, 1/(2*minFraction) for supermajority
    errorRates: PluralityErrorRates,
): Int {

    //  a := 1 / (2 − v/au)
    //  v := 2Āc − 1 is the diluted margin
    //  au := assort upper value, = 1 for plurality, 1/(2*minFraction) for supermajority

    val a = 1 / (2 - dilutedMargin / upperBound)
    val kelly = OptimalLambda(a, errorRates)
    val lam = kelly.solve()

    // 1 / alpha = bet ^ size
    val term1 = -ln(alpha)
    val term2 = ln(lam)
    val r = term1 / term2 // round up

    pow(lam, r)
    val size = ceil(r)
    // println("   lam=$lam r=$r T=$T size=$size")

    return size.toInt()
}
