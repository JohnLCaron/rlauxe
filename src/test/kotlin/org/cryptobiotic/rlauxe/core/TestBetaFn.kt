package org.cryptobiotic.rlauxe.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TestBetaFn {


    @Test
    fun testOptimalComparison() {
        val N = 100
        val mean = .51                              // .5 < mean <= 1
        val dilutedMargin = 2 * mean - 1            // aka v;  0 < v <= 1
        val noerror = 1.0 / (2.0 - dilutedMargin)   // aka a;  1/2 < a <= 1
        val upperBound = 2 * noerror                // aka u; 1 < u <= 2
        val p2 = .001
        val mu = .5

        val eta =  (1.0 - upperBound * (1.0 - p2)) / (2.0 - 2.0 * upperBound) + upperBound * (1.0 - p2) - 0.5
        val lam =  eta_to_lam(eta, mu, upperBound) // (eta / mu - 1) / (upper - mu)

        // so this equation only works if mu = .5
        val p0 = 1.0 - p2
        val expectedLam = (2 - 4 * noerror * p0) / (1 - 2 * noerror) // Cobra eq 3

        assertEquals(expectedLam, lam)
    }

    @Test
    fun testOptimalComparisonBet() {
        val N = 100
        val mean = .51
        val dilutedMargin = 2 * mean - 1
        val noerror = 1.0 / (2.0 - dilutedMargin)
        val upperBound = 2 * noerror
        val p2 = .001

        val betFn = OptimalComparisonNoP1(
            N = N,
            withoutReplacement = true,
            upperBound = upperBound,
            p2 = p2
        )

        val prevSamples = PrevSamples()
        val x = listOf(1, 1, 1, 0, 1, 1, 0, 0, 1, 1)
        val bets = mutableListOf<Double>()
        x.forEach {
            bets.add(betFn.bet(prevSamples))
            prevSamples.addSample(it * noerror)
        }
        println("bets = $bets")
    }

    @Test
    fun testOptimalComparisonBet2() {
        val N = 100
        val mean = .51
        val dilutedMargin = 2 * mean - 1
        val noerror = 1.0 / (2.0 - dilutedMargin)
        val upperBound = 2 * noerror
        val p2 = .001
        println("upperBound = $upperBound")
        val betFn2 = OptimalComparisonNoP1(
            N = N,
            withoutReplacement = true,
            upperBound = 1.0 + eps,
            p2 = p2
        )

        val x = listOf(1, 1, 1, 0, 1, 1, 0, 0, 1, 1)
        val prevSamples2 = PrevSamples()
        val bets2 = mutableListOf<Double>()
        x.forEach {
            bets2.add( betFn2.bet(prevSamples2) )
            prevSamples2.addSample(it.toDouble())
        }
        println("bets2 = $bets2")
    }

}