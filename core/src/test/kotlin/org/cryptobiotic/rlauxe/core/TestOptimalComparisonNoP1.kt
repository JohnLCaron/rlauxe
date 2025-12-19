package org.cryptobiotic.rlauxe.core

import kotlin.test.Test
import kotlin.test.assertEquals

// CANDIDATE for removal

class TestOptimalComparisonNoP1 {

    @Test
    fun compareOptimalLambdaToOptimalComparisonNoP1() {
        val N = 100
        val margins = listOf(.025, .05, .1)
        val p2s = listOf(.0001, .001, .01)

        for (margin in margins) {
            val a = 1 / (2 - margin) // aka noerror
            for (p2 in p2s) {
                println("margin=$margin p2=$p2")

                val kelly = OptimalLambda(a, PluralityErrorRates(p2, 0.0, 0.0, 0.0))
                val lam = kelly.solve()

                val optimal = OptimalComparisonNoP1(N = N, withoutReplacement = true, upperBound = 2 * a, p2 = p2)
                val bet = optimal.bet(PluralityErrorTracker(a))

                val message = if (lam < 1.0) "**** betting against" else ""
                println("   OptimalLambda=$lam OptimalComparisonNoP1=$bet $message")
                assertEquals(bet, lam, 1e-5)
            }
        }
    }

    @Test
    fun compareOptimalFailures() {
        val N = 100
        val margins = listOf(.05, .02, .01)
        val p2s = listOf(.005, .01, .02)

        for (margin in margins) {
            val a = 1 / (2 - margin) // aka noerror
            for (p2 in p2s) {
                println("margin=$margin p2=$p2")

                val kelly = OptimalLambda(a, PluralityErrorRates(p2, 0.0, 0.0, 0.0))
                val lam = kelly.solve()

                // these fail when upperBound * (1.0 - p2) <= 1.0
                val optimal = OptimalComparisonNoP1(N = N, withoutReplacement = true, upperBound = 2 * a, p2 = p2)
                val bet = optimal.bet(PluralityErrorTracker(a))

                println("   OptimalLambda=$lam OptimalComparisonNoP1=$bet")
                if (bet > 0.0) {
                    assertEquals(bet, lam, 1e-5)
                }
            }
        }
    }

    @Test
    fun testOptimalComparison() {
        val mean = .51                              // .5 < mean <= 1
        val dilutedMargin = 2 * mean - 1            // aka v;  0 < v <= 1
        val noerror = 1.0 / (2.0 - dilutedMargin)   // aka a;  1/2 < a <= 1
        val upperBound = 2 * noerror                // aka u; 1 < u <= 2
        val p2 = .001
        val mu = .5

        val eta =  (1.0 - upperBound * (1.0 - p2)) / (2.0 - 2.0 * upperBound) + upperBound * (1.0 - p2) - 0.5
        val lam =  etaToLam(eta, mu, upperBound) // (eta / mu - 1) / (upper - mu)

        // so this equation only works if mu = .5
        val p0 = 1.0 - p2
        val expectedLam = (2 - 4 * noerror * p0) / (1 - 2 * noerror) // Cobra eq 3

        assertEquals(expectedLam, lam)
    }

    @Test
    fun testOptimalComparisonUpper() {
        val N = 100
        val mean = .51
        val dilutedMargin = 2 * mean - 1
        val noerror = 1.0 / (2.0 - dilutedMargin)
        val upperBound = 2 * noerror
        val p2 = .001
        println("upperBound = $upperBound")

        val betFn = OptimalComparisonNoP1(
            N = N,
            withoutReplacement = true,
            upperBound = upperBound,
            p2 = p2
        )

        val prevSamples = PluralityErrorTracker(noerror)
        val x = listOf(1, 1, 1, 0, 1, 1, 0, 0, 1, 1)
        val bets = mutableListOf<Double>()
        x.forEach {
            bets.add(betFn.bet(prevSamples))
            prevSamples.addSample(it * noerror)
        }
        println("bets = $bets")

        // tweak upperBound
        val upperBound2 = 1.0 + eps
        println("upperBound2 = $upperBound2")

        val betFn2 = OptimalComparisonNoP1(
            N = N,
            withoutReplacement = true,
            upperBound = 1.0 + eps,
            p2 = p2
        )

        val x2 = listOf(1, 1, 1, 0, 1, 1, 0, 0, 1, 1)
        val prevSamples2 = PluralityErrorTracker(noerror)
        val bets2 = mutableListOf<Double>()
        x2.forEach {
            bets2.add( betFn2.bet(prevSamples2) )
            prevSamples2.addSample(it.toDouble())
        }
        println("bets2 = $bets2")
    }

}