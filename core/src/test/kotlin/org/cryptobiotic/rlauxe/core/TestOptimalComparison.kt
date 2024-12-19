package org.cryptobiotic.rlauxe.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TestOptimalComparison {

    @Test
    fun compareToOptimalComparisonNoP1() {
        val N = 100
        val margins = listOf(.025, .05, .1)
        val p2s = listOf(.0001, .001, .01)

        for (margin in margins) {
            val a = 1 / (2 - margin) // aka noerror
            for (p2 in p2s) {
                println("margin=$margin p2=$p2")

                val kelly = OptimalLambda(a, p1 = 0.0, p2 = p2)
                val lam = kelly.solve()

                val optimal = OptimalComparisonNoP1(N = N, withoutReplacement = true, upperBound = 2 * a, p2 = p2)
                val bet = optimal.bet(PrevSamplesWithRates(a))

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

                val kelly = OptimalLambda(a, p1 = 0.0, p2 = p2)
                val lam = kelly.solve()

                // these fail when upperBound * (1.0 - p2) <= 1.0
                val optimal = OptimalComparisonNoP1(N = N, withoutReplacement = true, upperBound = 2 * a, p2 = p2)
                val bet = optimal.bet(PrevSamplesWithRates(a))

                println("   OptimalLambda=$lam OptimalComparisonNoP1=$bet")
                if (bet > 0.0) {
                    assertEquals(bet, lam, 1e-5)
                }
            }
        }
    }
}