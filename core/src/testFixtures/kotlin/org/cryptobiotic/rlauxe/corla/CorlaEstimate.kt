package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.core.PluralityErrorRates
import org.cryptobiotic.rlauxe.shangrla.OptimalLambda
import org.cryptobiotic.rlauxe.util.roundUp
import java.lang.Math.pow
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max


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
