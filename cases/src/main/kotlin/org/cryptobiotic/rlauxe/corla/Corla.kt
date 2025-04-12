package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.core.*
import java.lang.Math.pow
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

// main Corla RiskTestingFn is in plots module
    /**
     *  From colorado-rla Audit class, optimistic method.
     *  Not using this at the moment, testing Corla with SingleRound task.
     *
     * Computes the expected number of ballots to audit overall given the
     * specified numbers of over- and understatements.
     *
     * @param the_two_under The two-vote understatements.
     * @param the_one_under The one-vote understatements.
     * @param the_one_over The one-vote overstatements.
     * @param the_two_over The two-vote overstatements.
     *
     * @return the expected number of ballots remaining to audit.
     * This is the stopping sample size as defined in the literature:
     * https://www.stat.berkeley.edu/~stark/Preprints/gentle12.pdf
     *
     *
     * @param dilutedMargin the diluted margin of the contest
     * @param gamma the "error inflator" parameter from the literature
     * @param twoUnder the number of two-vote understatements
     * @param oneUnder the number of one-vote understatements
     * @param oneOver the number of one-vote overstatements
     * @param twoOver the number of two-vote overstatements
     */
    fun optimistic(
        riskLimit: Double,
        dilutedMargin: Double,
        gamma: Double,
        oneOver: Int = 0,   // p1
        twoOver: Int = 0,   // p2
        oneUnder: Int = 0,  // p3
        twoUnder: Int = 0,  // p4
    ): Double {
        val two_under_term = twoUnder * ln( 1 + 1 / gamma) // log or ln ?
        val one_under_term = oneUnder * ln( 1 + 1 / (2 * gamma)) // log or ln ?
        val one_over_term = oneOver * ln( 1 - 1 / (2 * gamma)) // log or ln ?
        val two_over_term = twoOver * ln( 1 - 1 / gamma) // log or ln ?

        //             twogamma.negate()
        //                .multiply(
        //                    log(riskLimit, MathContext.DECIMAL128)
        //                        .add(two_under.add(one_under).add(one_over).add(two_over))
        //                )
        val numerator: Double = -(2.0 * gamma) * (ln(riskLimit) + two_under_term + one_under_term + one_over_term + two_over_term)

        // val ceil = numerator.divide(dilutedMargin, MathContext.DECIMAL128).setScale(0, RoundingMode.CEILING)
        val ceil = ceil(numerator / dilutedMargin)  // org does a rounding up
        val over_under_sum = (twoUnder + oneUnder +  oneOver + twoOver).toDouble()
        val result = max(ceil, over_under_sum)

        return result
    }