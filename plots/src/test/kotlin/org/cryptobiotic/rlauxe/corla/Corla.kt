package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.core.*
import java.lang.Math.pow
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

// The algorithm that colorado-rla uses, from SuperSimple paper
// Corla is a RiskTestingFn
// TODO does Corla use phantoms ?
class Corla(val N: Int, val riskLimit: Double, val reportedMargin: Double, val noerror: Double,
    val p1: Double, val p2: Double, val p3: Double, val p4: Double): RiskTestingFn {
    val gamma = 1.03

    override fun testH0(maxSample: Int,
                        terminateOnNullReject: Boolean,
                        startingTestStatistic: Double, // TODO ignore?
                        drawSample : () -> Double) : TestH0Result {
        require(maxSample <= N)

        var sampleNumber = 0        // – j ← 0: sample number
        val prevSamples = PrevSamplesWithRates(noerror) // – S ← 0: sample sum
        var pvalue = 0.0
        var pvalueMin = 1.0
        var sampleFirstUnderLimit = 0

        while (sampleNumber < maxSample) {
            val xj: Double = drawSample()
            sampleNumber++ // j <- j + 1
            //xs.add(xj)
            require(xj >= 0.0)
            // require(xj <= upperBound)

            // calculate the risk; could do this cumulatively, to see better what each sample value does
            pvalue = pValueApprox(
                sampleNumber,
                reportedMargin,
                gamma,
                p2o = prevSamples.countP2o(),
                p1o = prevSamples.countP1o(),
                p1u = prevSamples.countP1u(),
                p2u = prevSamples.countP2u(),
            )

            // not clear we should wait until now to update prevSamples
            prevSamples.addSample(xj)
            if (sampleFirstUnderLimit == 0 && pvalue < riskLimit) sampleFirstUnderLimit = sampleNumber + 1
            if (pvalue < pvalueMin) pvalueMin = pvalue

            if (terminateOnNullReject && (pvalue < riskLimit)) {
                break
            }
        }
        val status = if (pvalue <= riskLimit) TestH0Status.StatRejectNull else TestH0Status.LimitReached

        return TestH0Result(status, sampleNumber, sampleFirstUnderLimit, pvalueMin, pvalue, prevSamples)
    }

    /**
     * From colorado-rla Audit class, pValueApproximation method.
     *
     * Conservative approximation of the Kaplan-Markov P-value.
     *
     * The audit can stop when the P-value drops to or below the defined risk
     * limit. The output of this method will never estimate a P-value that is too
     * low, it will always be at or above the (more complicated to calculate)
     * Kaplan-Markov P-value, but usually not by much. Therefore this method is
     * safe to use as the stopping condition for the audit, even though it may be
     * possible to stop the audit "a ballot or two" earlier if calculated using
     * the Kaplan-Markov method.
     *
     * Implements equation (10) of Philip B. Stark's paper, Super-Simple
     * Simultaneous Single-Ballot Risk-Limiting Audits.
     *
     * Translated from Stark's implementation under the heading "A simple
     * approximation" at the following URL:
     *
     * https://github.com/pbstark/S157F17/blob/master/audit.ipynb
     *
     * NOTE: The ordering of the under and overstatement parameters is different
     * from its cousin method `optimistic`.
     *
     * @param auditedBallots the number of ballots audited so far
     * @param dilutedMargin the diluted margin of the contest
     * @param gamma the "error inflator" parameter from the literature
     * @param twoUnder the number of two-vote understatements
     * @param oneUnder the number of one-vote understatements
     * @param oneOver the number of one-vote overstatements
     * @param twoOver the number of two-vote overstatements
     *
     * @return approximation of the Kaplan-Markov P-value
     */
    fun pValueApprox(
        n: Int, // n = auditedBallots the number of ballots audited so far
        dilutedMargin: Double, // V is the smallest reported margin = min_c { min w∈Wc ∈Lc (V_wl) } over contests c
        gamma: Double,  // use 1.01 or 1.10 ??
        p2o: Int, // twoOver
        p1o: Int, // oneOver
        p1u: Int = 0, // oneUnder
        p2u: Int = 0, // twoUnder
    ): Double {

        // Contest c appears on Nc of the N cast ballots
        // “inflator” γ > 1
        // µ is the diluted margin V /N
        // U ≡ 2γN/V = 2γ/µ    the total error bound across all N ballots
        val U = 2 * gamma / dilutedMargin

        // Pkm <= P (n, n1 , n2 ; U, γ)
        //        = (1 - 1/U) ^ n *  (1 - 1/(2γ)) ^ -n1 *  (1 - 1/γ) ^ -n2 (eq 10)

        // min(1, (1-1/U)^n * (1-1/(2*gamma))^(-n1) * (1-1/gamma)^(-n2) * (1+1/(2*gamma))^(-n3) * (1+1/(gamma))^(-n4)) (eq 10 extended)
        val term = (1.0 - 1.0/U)
        val termn = pow(term, n.toDouble())

        val term1 = (1.0 - 1.0/(2 * gamma))
        val term1n = pow(term1, -p1o.toDouble())

        val term2 = (1.0 - 1.0/gamma)
        val term2n = pow(term2, -p2o.toDouble())

        val term3 = (1.0 + 1.0/(2 * gamma))
        val term3n = pow(term3, -p1u.toDouble())

        val term4 = (1.0 + 1.0/gamma)
        val term4n = pow(term4, -p2u.toDouble())

        val result = termn * term1n * term2n * term3n * term4n
        return min(1.0, result)
    }

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
}