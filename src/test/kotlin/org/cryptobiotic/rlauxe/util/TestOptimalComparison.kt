package org.cryptobiotic.rlauxe.util

import org.junit.jupiter.api.Test

class TestOptimalComparison {

    @Test
    fun testBasics() {
        val kelly = OptimalLambda(0.5128205128205129, p1=0.01,  p2=0.001)

        // EF [Ti ] = p0 [1 + λ(a − 1/2)] + p1 [1 − λ(1 − a)/2] + p2 [1 − λ/2]
    }
}