package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doublePrecision
import kotlin.test.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestProbRatios {

    @Test
    fun testBinary() {
        testBinary(.5, .505)
        testBinary(.5, .51)
        testBinary(.5, .55)
        testBinary(.5, .6)
    }

    fun testBinary(mu: Double, nu: Double) {
        val margin = nu * 2 - 1.0
        println("\nmu= $mu; nu = $nu; margin = $margin")

        // yk*(nu/mu) + (1-yk)*(1-nu)/(1-mu)
        val r1 = nu/mu
        val r0 = (1-nu)/(1-mu)
        println("        r1=nu/mu = ${r1}")
        println("r0=(1-nu)/(1-mu) = ${r0}")
        assertEquals(2.0, r1+r0)
        assertEquals(1+margin, r1)
        assertEquals(1-margin, r0)
    }

    //Step 1: Replace discrete yk with continuous X_j:
    @Test
    fun testNonBinary() {
        testNonBinary(.5, .51, 0.0)
        testNonBinary(.5, .51, 1.0)
        testNonBinary(.5, .51, .5)
        testNonBinary(.5, .51, .25)
        testNonBinary(.5, .51, .75)
    }

    fun testNonBinary(mu: Double, nu: Double, Xj:Double) {
        val margin = nu * 2 - 1.0
        println("\nmu= $mu; nu = $nu; margin = ${"%4.2f".format(margin)}; Xj=$Xj")

        //    X_j*(nu/mu) + (1-X_j)*(1-nu)/(1-mu)
        val r1 = Xj*nu/mu
        val r0 = (1-Xj)*(1-nu)/(1-mu)
        println("             r1=Xj*nu/mu = ${r1}")
        println(" r0=(1-Xj)*(1-nu)/(1-mu) = ${r0}")
        println(" r1+r0 = ${r1+r0}")
        when {
            Xj == 0.5 -> assertEquals(1.0, r1+r0)
            Xj < 0.5 -> assertTrue(1.0 > r1+r0)
            Xj > 0.5 -> assertTrue(1.0 < r1+r0)
            else -> throw RuntimeException("bugger off")
        }
    }

    @Test
    fun testNonBinaryAlt() {
        testNonBinaryAlt(.5, .51, 0.0)
        testNonBinaryAlt(.5, .51, 1.0)
        testNonBinaryAlt(.5, .51, .5)
        testNonBinaryAlt(.5, .51, .25)
        testNonBinaryAlt(.5, .51, .75)
    }

    // @Test intermittent failure
    fun testAlternateEquals() {
        repeat(10) {
            val margin = Random.nextInt(100)
            val xj = Random.nextDouble(1.0)
            val nu = .5 + margin * .001
            testNonBinaryAlt(.5, nu, xj)
        }
    }

    fun testNonBinaryAlt(mu: Double, nu: Double, Xj:Double) {
        val margin = nu * 2 - 1.0
        println("\nAlt mu= $mu; nu = $nu; margin = ${"%4.2f".format(margin)}; Xj=$Xj")

        //    X_j*(nu/mu) + (1-X_j)*(1-nu)/(1-mu)
        val r1 = Xj*nu/mu
        val r0 = (1-Xj)*(1-nu)/(1-mu)
        println("             r1=Xj*nu/mu = ${r1}")
        println(" r0=(1-Xj)*(1-nu)/(1-mu) = ${r0}")
        println(" r1+r0 = ${r1+r0}")
        r1 + r0
        when {
            Xj == 0.5 -> assertEquals(1.0, r1+r0)
            Xj < 0.5 -> assertTrue(1.0 > r1+r0) // TODO intermittent failures
            Xj > 0.5 -> assertTrue(1.0 < r1+r0)
            else -> throw RuntimeException("bugger off")
        }

        // alternate
        //     ( X_j*nu + (1-X_j)*(1-nu) ) / ( X_j*mu + (1-X_j)*(1-mu) )
        val pnu = Xj*nu + (1-Xj)*(1-nu)
        val pmu = Xj*mu + (1-Xj)*(1-mu)
        val ratio = pnu/pmu
        println(" pnu/pmu = ${ratio}")
        // these agree but i cant get the algebra right to prove equality
        assertEquals(ratio, r1+r0)
    }

    // Step 2: Generalize range [0,1] to [0,upper]
    @Test
    fun testUpper() {
        val upper = 1.1
        testUpper(.5, .51, upper, 0.0)
        testUpper(.5, .51, upper, upper)
        testUpper(.5, .51, upper, .5 * upper)
        testUpper(.5, .51, upper, .25 * upper)
        testUpper(.5, .51, upper, .75 * upper)
    }

    fun testUpper(mu: Double, nu: Double, upper:Double, Xj:Double) {
        val margin = nu * 2 - 1.0
        println("\nmu= $mu; nu = $nu; margin = ${"%4.2f".format(margin)}; upper=$upper, Xj=$Xj")

        // (X_j*(nu/mu) + (upper-X_j)*(upper-nu)/(upper-mu))/upper
        val r1 = Xj*nu/mu/upper
        val r0 = (upper-Xj)*(upper-nu)/(upper-mu)/upper
        println("             r1=Xj*nu/mu/u = ${r1}")
        println(" r0=(u-Xj)*(u-nu)/(u-mu)/u = ${r0}")
        println(" r1+r0 = ${r1+r0}")
        when {
            Xj == 0.5 -> assertEquals(1.0, r1+r0)
            Xj < 0.5 -> assertTrue(1.0 > r1+r0)
            Xj > 0.5 -> assertTrue(1.0 < r1+r0)
            else -> throw RuntimeException("bugger off")
        }
    }

    fun testUpperOld(mu: Double, nu: Double, upper:Double) {
        val margin = nu * 2 - 1.0
        println("\nmu= $mu; nu = $nu; u=$upper; margin = ${"%6.2f".format(margin)}")

        // (X_j*(nu/mu) + (upper-X_j)*(upper-nu)/(upper-mu))/upper
        val r1 = nu/mu
        val r1u = r1/upper
        val r0 = (1-nu)/(1-mu)
        val r0u = (upper-nu)/(upper-mu)/upper
        println("          r1=nu/mu = ${r1}")
        println("       r1u=nu/mu/u = ${r1u}")
        println(" r0=(1-nu)/(1-mu) = ${r0}")
        println("r0u=(u-nu)/(u-mu) = ${r0u}")
        assertEquals(2.0/upper, r1u+r0u)
        assertEquals((1+margin)/upper, r1u)
        assertEquals((1-margin)/upper, r0u)
    }

    @Test
    fun testNumerics() {
        testNumerics(upperBound=1.0101010101010102, etaj=.7575757575756341, mj = .5, xj = .5050505050505051)
        // testNumerics(upperBound=1.0526315789473684, etaj=.5263157894736, mj = .5, xj = .5263157894736)
        // testNumerics(upperBound=1.0, etaj=.5050505050505051, mj = .4999994948989849, xj = .5050505050505051)
    }

    fun testNumerics(upperBound: Double, etaj: Double, mj: Double, xj: Double) {
        val p1 = etaj / mj
        val x1n = xj / upperBound
        val term1 = p1 * x1n
        val p0 = (upperBound - etaj) / (upperBound - mj)
        val x0n = (upperBound - xj) / upperBound
        val term2 = p0 * x0n
        val term = (term1 + term2)
        // ALPHA eq 4
        val ttj = (xj * etaj / mj + (upperBound - xj) * (upperBound - etaj) / (upperBound - mj)) / upperBound
        assertEquals(term, ttj, doublePrecision)
        println("ttj=$ttj term1 = $term1, term2 = $term2 upperBound = $upperBound")

        // alternative formulation (3b) (xj*nj + (u-xj)*(u-nj)) / (x*mj + (u-xj)*(u-mj))
        val (termN1, termN2, termN3) = calcTerms(upperBound, etaj, xj)
        val (termD1, termD2, termD3) = calcTerms(upperBound, mj, xj)

        termN1 + termN2 * termN3
        termD1 + termD2 * termD3
        // println("num=$num den = $den, diff = ${num-den}")

        // u * (u - x) + mean * (2x - u), mean = nj or mj
        val d1 = upperBound * (upperBound - xj)
        val d2 = 2*xj - upperBound // this term is zero when xj = upper/2, whuch is always is for cvrs that match the mvr
        d1 + etaj * d2
        d1 + mj * d2
       // println("tn=$tn tm = $tm")

    }

    fun calcTerms(upperBound: Double, mean: Double, xj: Double) : Triple<Double, Double, Double> {
        return Triple(
            xj * mean,
            (upperBound - xj),
            (upperBound - mean),
        )

    }
}