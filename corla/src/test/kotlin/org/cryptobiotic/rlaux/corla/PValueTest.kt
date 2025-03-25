package org.cryptobiotic.rlaux.corla

import java.lang.Math.pow
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

class PValueTest {

    @Test
    fun checkTable3() {
        var count = 0
        table3s.forEach {
            assertEquals(it.expect, pValue(it), .001, "$count: $it")
            count++
        }
    }

    @Test
    fun problem() {
        val it = table3s[11]
        assertEquals(it.expect, pValue(it), .001)
    }
}

fun pValue(row: T3row) = pValueApproxOld(row.n, row.mu, row.gamma, row.n1, row.n2)

// Kaplan-Markov bound: eq 10 of SuperSimple
// these are for simultaneous auditing of multiple contests, giving a more conservative estimate of the risk
// we dont actually know if colorado-rla is combining contests
fun pValueApproxOld(
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
    val term = (1.0 - 1.0 / U)
    val termn = pow(term, n.toDouble())

    val term1 = (1.0 - 1.0 / (2 * gamma))
    val term1n = pow(term1, -n1.toDouble())

    val term2 = (1.0 - 1.0 / gamma)
    val term2n = pow(term2, -n2.toDouble())

    val term3 = (1.0 + 1.0 / (2 * gamma))
    val term3n = pow(term3, -n3.toDouble())

    val term4 = (1.0 + 1.0 / gamma)
    val term4n = pow(term4, -n4.toDouble())

    val result = termn * term1n * term2n * term3n * term4n
    return min(1.0, result)
}

// Kaplan-Markov MACRO P-value (maximum across-contest relative overstatement)
//
// The MACRO for ballot p is the largest percentage by which the
//  difference between the CVR and MVR (hand interpretation of that ballot)
//  resulted in overstating any margin in any of the c contests:
// e_r = max_c { max w∈Wc ∈Lc (v_pw − a_pw − v_pl + a_pl )/V_wl }
// V_wl = The reported margin of reported winner w ∈ Wc over reported loser L ∈ Lc in contest c
//      = Sum(v_pw − v_pl) > 0, p=1..N,
// where
//   v_pi is the reported vote (0 or 1) for candidate i on ballot p (from the cvr)
//   a_pi is the actual vote (0 or 1) for candidate i on ballot p (from the audit)

// Kaplan-Markov MACRO P-value = P_KM = Prod( (1-1/U) / (1 - e_r/(2*gamma * V)) ) for r=1..n (eq 9)
// where
//   V is the smallest reported margin = min_c { min w∈Wc ∈Lc (V_wl) } over contests c
//   U = 2 * gamma / V
//   e_r = max_c { max w∈Wc ∈Lc (v_pw − a_pw − v_pl + a_pl )/V_wl } (eq 5)

// TODO probably no point in this unless we simulate real ballots, and use seperate estimates of the ni for each contest (?)
//   now that we know the algo, we can simulate how it compares in rlauxe
fun macro(
    n: Int, // n
    dilutedMargin: Double, // V
    gamma: Double,
    n1: Int, // oneOver
    n2: Int, // twoOver
    n3: Int = 0, // oneUnder
    n4: Int = 0, // twoUnder
): Double {
    val n0 = n - n1 - n2 - n3 - n4 // ntimes CVR == MVR
    val U = 2 * gamma / dilutedMargin

    // Prod( (1-1/U) / (1 - e_r/(2*gamma * V)) ) for r=1..n (eq 9)
    // since its a product, we can do it in any order

    val term0 = (1.0 - 1.0 / U)

    // Pkm <= P (n, n1 , n2 ; U, γ)
    //        = (1 - 1/U) ^ n *  (1 - 1/(2γ)) ^ -n1 *  (1 - 1/γ) ^ -n2

    // min(1, (1-1/U)^n * (1-1/(2*gamma))^(-n1) * (1-1/gamma)^(-n2) * (1+1/(2*gamma))^(-n3) * (1+1/(gamma))^(-n4))
    val term = (1.0 - 1.0 / U)
    val termn = pow(term, n.toDouble())

    val term1 = (1.0 - 1.0 / (2 * gamma))
    val term1n = pow(term1, -n1.toDouble())

    val term2 = (1.0 - 1.0 / gamma)
    val term2n = pow(term2, -n2.toDouble())

    val term3 = (1.0 + 1.0 / (2 * gamma))
    val term3n = pow(term3, -n3.toDouble())

    val term4 = (1.0 + 1.0 / gamma)
    val term4n = pow(term4, -n4.toDouble())

    val result = termn * term1n * term2n * term3n * term4n
    return min(1.0, result)
}

// n is the number of samples. interesting that N isnt needed
// mu is the dilutedMargin
data class T3row(val n: Int, val mu: Double, val gamma: Double, val n1: Int, val n2: Int, val expect: Double)

val table3s = listOf(
    T3row(500, .02, 1.01, 0, 0, .007),
    T3row(500, .02, 1.01, 1, 0, .014),
    T3row(500, .02, 1.01, 2, 0, .027),
    T3row(500, .02, 1.01, 3, 0, .054),

    T3row(500, .02, 1.01, 0, 0, .007),
    T3row(500, .02, 1.01, 0, 1, .698),
    T3row(500, .02, 1.01, 0, 2, 1.0),
    T3row(500, .02, 1.01, 0, 3, 1.0),

    T3row(500, .02, 1.10, 0, 0, .01),
    T3row(500, .02, 1.10, 1, 0, .019),
    T3row(500, .02, 1.10, 2, 0, .035),
    T3row(500, .02, 1.10, 3, 0, .064),

    T3row(500, .02, 1.10, 0, 0, .01),
    T3row(500, .02, 1.10, 0, 1, .114),
    T3row(500, .02, 1.10, 0, 2, 1.0),
    T3row(500, .02, 1.10, 0, 3, 1.0),


    T3row(750, .02, 1.01, 0, 0, .001),
    T3row(750, .02, 1.01, 1, 0, .001),
    T3row(750, .02, 1.01, 2, 0, .002),
    T3row(750, .02, 1.01, 3, 0, .004),
    T3row(750, .02, 1.01, 4, 0, .009),
    T3row(750, .02, 1.01, 5, 0, .017),

    T3row(750, .02, 1.01, 0, 0, .001),
    T3row(750, .02, 1.01, 0, 1, .058),
    T3row(750, .02, 1.01, 0, 2, 1.0),
    T3row(750, .02, 1.01, 0, 3, 1.0),
    T3row(750, .02, 1.01, 0, 4, 1.0),
    T3row(750, .02, 1.01, 0, 5, 1.0),

    T3row(750, .02, 1.10, 0, 0, .001),
    T3row(750, .02, 1.10, 1, 0, .002),
    T3row(750, .02, 1.10, 2, 0, .004),
    T3row(750, .02, 1.10, 3, 0, .007),
    T3row(750, .02, 1.10, 4, 0, .012),
    T3row(750, .02, 1.10, 5, 0, .022),

    T3row(750, .02, 1.10, 0, 0, .001),
    T3row(750, .02, 1.10, 0, 1, .012),
    T3row(750, .02, 1.10, 0, 2, .128),
    T3row(750, .02, 1.10, 0, 3, 1.0),
    T3row(750, .02, 1.10, 0, 4, 1.0),
    T3row(750, .02, 1.10, 0, 5, 1.0),


    T3row(750, .01, 1.01, 0, 0, .024),
    T3row(750, .01, 1.01, 1, 0, .048),
    T3row(750, .01, 1.01, 2, 0, .095),

    T3row(750, .01, 1.01, 0, 0, .024),
    T3row(750, .01, 1.01, 0, 1, 1.0),
    T3row(750, .01, 1.01, 0, 2, 1.0),

    T3row(750, .01, 1.10, 0, 0, .033),
    T3row(750, .01, 1.10, 1, 0, .060),
    T3row(750, .01, 1.10, 2, 0, .11),

    T3row(750, .01, 1.10, 0, 0, .033),
    T3row(750, .01, 1.10, 0, 1, .361),
    T3row(750, .01, 1.10, 0, 2, 1.0),


    T3row(1000, .01, 1.01, 0, 0, .007),
    T3row(1000, .01, 1.01, 1, 0, .014),
    T3row(1000, .01, 1.01, 2, 0, .027),
    T3row(1000, .01, 1.01, 3, 0, .054),

    T3row(1000, .01, 1.01, 0, 0, .007),
    T3row(1000, .01, 1.01, 0, 1, .706),
    T3row(1000, .01, 1.01, 0, 2, 1.0),
    T3row(1000, .01, 1.01, 0, 3, 1.0),

    T3row(1000, .01, 1.10, 0, 0, .011),
    T3row(1000, .01, 1.10, 1, 0, .019),
    T3row(1000, .01, 1.10, 2, 0, .035),
    T3row(1000, .01, 1.10, 3, 0, .065),

    T3row(1000, .01, 1.10, 0, 0, .011),
    T3row(1000, .01, 1.10, 0, 1, .116),
    T3row(1000, .01, 1.10, 0, 2, 1.0),
    T3row(1000, .01, 1.10, 0, 3, 1.0),


    T3row(1000, .005, 1.01, 0, 0, .084),
    T3row(1000, .005, 1.10, 0, 0, .103),


    T3row(1250, .005, 1.01, 0, 0, .045),
    T3row(1250, .005, 1.01, 1, 0, .089),

    T3row(1250, .005, 1.01, 0, 0, .045),
    T3row(1250, .005, 1.01, 0, 1, 1.0),

    T3row(1250, .005, 1.10, 0, 0, .058),
    T3row(1250, .005, 1.10, 1, 0, .107),

    T3row(1250, .005, 1.10, 0, 0, .058),
    T3row(1250, .005, 1.10, 0, 1, .64),


    T3row(1500, .005, 1.01, 0, 0, .024),
    T3row(1500, .005, 1.01, 1, 0, .048),
    T3row(1500, .005, 1.01, 2, 0, .095),

    T3row(1500, .005, 1.01, 0, 0, .024),
    T3row(1500, .005, 1.01, 0, 1, 1.0),
    T3row(1500, .005, 1.01, 0, 2, 1.0),

    T3row(1500, .005, 1.10, 0, 0, .033),
    T3row(1500, .005, 1.10, 1, 0, .060),
    T3row(1500, .005, 1.10, 2, 0, .111),

    T3row(1500, .005, 1.10, 0, 0, .033),
    T3row(1500, .005, 1.10, 0, 1, .362),
    T3row(1500, .005, 1.10, 0, 2, 1.0),


    T3row(2000, .005, 1.01, 0, 0, .007),
    T3row(2000, .005, 1.01, 1, 0, .014),
    T3row(2000, .005, 1.01, 2, 0, .028),
    T3row(2000, .005, 1.01, 3, 0, .055),

    T3row(2000, .005, 1.01, 0, 0, .007),
    T3row(2000, .005, 1.01, 0, 1, .711),
    T3row(2000, .005, 1.01, 0, 2, 1.0),
    T3row(2000, .005, 1.01, 0, 3, 1.0),

    T3row(2000, .005, 1.10, 0, 0, .011),
    T3row(2000, .005, 1.10, 1, 0, .019),
    T3row(2000, .005, 1.10, 2, 0, .035),
    T3row(2000, .005, 1.10, 3, 0, .065),

    T3row(2000, .005, 1.10, 0, 0, .011),
    T3row(2000, .005, 1.10, 0, 1, .116),
    T3row(2000, .005, 1.10, 0, 2, 1.0),
    T3row(2000, .005, 1.10, 0, 3, 1.0),
)

