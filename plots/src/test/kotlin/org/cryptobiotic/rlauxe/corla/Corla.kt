package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.core.*
import java.lang.Math.pow
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

// The algorithm that colorado-rla uses, from SuperSimple paper
class Corla(val N: Int, val riskLimit: Double, val reportedMargin: Double, val noerror: Double,
    val p1: Double, val p2: Double, val p3: Double, val p4: Double): RiskTestingFn {
    val gamma = 1.03

    override fun testH0(maxSample: Int,
                        terminateOnNullReject: Boolean,
                        showDetails: Boolean,
                        startingTestStatistic: Double, // TODO ignore?
                        drawSample : () -> Double) : TestH0Result {
        require(maxSample <= N)

        var sampleNumber = 0        // – j ← 0: sample number
        val prevSamples = PrevSamplesWithRates(noerror) // – S ← 0: sample sum
        var pvalue = 0.0

        // keep series for debugging, remove for production
        val xs = mutableListOf<Double>()
        val pvalues = mutableListOf<Double>()

        while (sampleNumber < maxSample) {
            val xj: Double = drawSample()
            sampleNumber++ // j <- j + 1
            xs.add(xj)
            require(xj >= 0.0)
            // require(xj <= upperBound)

            // calculate the risk; could do this cumulatively, to see better what each sample value does
            pvalue = pValueApprox(
                sampleNumber,
                reportedMargin,
                gamma,
                n1 = prevSamples.sampleP1count(),
                n2 = prevSamples.sampleP2count(),
                n3 = prevSamples.sampleP3count(),
                n4 = prevSamples.sampleP4count(),
            )
            pvalues.add(pvalue)

            // not clear we should wait until now to update prevSamples
            prevSamples.addSample(xj)

            if (terminateOnNullReject && (pvalue < riskLimit)) {
                break
            }
        }
        val status = if (pvalue <= riskLimit) TestH0Status.StatRejectNull else TestH0Status.LimitReached

        return TestH0Result(status, sampleNumber, prevSamples.mean(), pvalues, emptyList(), emptyList())
    }

    fun testH0batch(maxSample: Int, drawSample : () -> Double) : TestH0Result {
        require(maxSample <= N)

        var sampleNumber = 0        // – j ← 0: sample number
        val prevSamples = PrevSamplesWithRates(noerror) // – S ← 0: sample sum

        // first estimate sample size
        val estSampleSize = estimateSampleSize(
            riskLimit,
            reportedMargin,
            gamma,
            oneOver = (N * p1).toInt(),
            twoOver = (N * p2).toInt(),
            oneUnder = (N * p3).toInt(),
            twoUnder = (N * p4).toInt(),
        )

        // draw that number of samples, accumulating errors
        while (sampleNumber < maxSample && sampleNumber < estSampleSize) {
            val xj: Double = drawSample()
            prevSamples.addSample(xj)
            sampleNumber++ // j <- j + 1
        }

        // calculate the risk
        val p = pValueApprox(
            sampleNumber,
            reportedMargin,
            gamma,
            n1 = prevSamples.sampleP1count(),
            n2 = prevSamples.sampleP2count(),
            n3 = prevSamples.sampleP3count(),
            n4 = prevSamples.sampleP4count(),
        )

        val status = if (p <= riskLimit) TestH0Status.StatRejectNull else TestH0Status.LimitReached

        return TestH0Result(status, sampleNumber, prevSamples.mean(), emptyList(), emptyList(), emptyList())
    }

    // Kaplan-Markov bound: eq 10 of SuperSimple
    // these are for simultaneous auditing of multiple contests, giving a more conservative estimate of the risk
    // we dont actually know if colorado-rla is combining contests
    fun pValueApprox(
        n: Int, // n is the sample size, not N total ballots
        dilutedMargin: Double, // V is the smallest reported margin = min_c { min w∈Wc ∈Lc (V_wl) } over contests c
        gamma: Double,  // use 1.01 or 1.10 ??
        n1: Int, // oneOver
        n2: Int, // twoOver
        n3: Int = 0, // oneUnder
        n4: Int = 0, // twoUnder
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
        val term1n = pow(term1, -n1.toDouble())

        val term2 = (1.0 - 1.0/gamma)
        val term2n = pow(term2, -n2.toDouble())

        val term3 = (1.0 + 1.0/(2 * gamma))
        val term3n = pow(term3, -n3.toDouble())

        val term4 = (1.0 + 1.0/gamma)
        val term4n = pow(term4, -n4.toDouble())

        val result = termn * term1n * term2n * term3n * term4n
        return min(1.0, result)
    }

    /**
     * @param dilutedMargin the diluted margin of the contest
     * @param gamma the "error inflator" parameter from the literature
     * @param twoUnder the number of two-vote understatements
     * @param oneUnder the number of one-vote understatements
     * @param oneOver the number of one-vote overstatements
     * @param twoOver the number of two-vote overstatements
     */
    fun estimateSampleSize(
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