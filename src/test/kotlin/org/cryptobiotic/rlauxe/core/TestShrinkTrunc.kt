package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doubleIsClose
import org.cryptobiotic.rlauxe.doublesAreClose
import kotlin.test.Test
import kotlin.test.assertTrue


// Direct compare TruncShrinkage with output from SHANGRLA TestNonnegMean output
class TestShrinkTrunc {

    @Test
    fun testTruncShrinkageLeadingZeroes() {
        val eta0 = .51
        val x = listOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 1.0, )
        val expected = listOf(0.51, 0.55706311, 0.62644338, 0.71567246, 0.83466964,  1.0, 1.0 , 1.0 , 1.0, 1.0)

        val t = .5
        val u = 1.0
        val d = 10
        val f = 0.0
        val minsd = 1.0e-6
        val c = (eta0 - t) / 2

        val N = x.size
        val estimFn = TruncShrinkage(N = N, upperBound = u, minsd = minsd, d = d, eta0 = eta0, f = f, c = c)
        val assortValues = PrevSamples()
        val etaValues = mutableListOf<Double>()
        x.forEach {
            val eta = estimFn.eta(assortValues)
            etaValues.add(eta)
            assortValues.addSample(it)
        }
        println("testTruncShrinkageLeadingZeroes= $etaValues")
        doublesAreClose(expected, etaValues)
    }

    @Test
    fun testTruncShrinkageLeadingOnes() {
        val eta0 = .51
        val x = listOf(1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val expected = listOf(0.51, 0.55454545, 0.59166667, 0.62307692, 0.65, 0.67333333, 0.63125, 0.59411765, 0.56111111, 0.53157895)
        val t = .5
        val u = 1.0
        val d = 10
        val f = 0.0
        val minsd = 1.0e-6
        val c = (eta0 - t) / 2

        val N = x.size
        val estimFn = TruncShrinkage(N = N, upperBound = u, minsd = minsd, d = d, eta0 = eta0, f = f, c = c)
        val assortValues = PrevSamples()
        val etaValues = mutableListOf<Double>()
        x.forEach {
            val eta = estimFn.eta(assortValues)
            etaValues.add(eta)
            assortValues.addSample(it)
        }
        println("testTruncShrinkageLeadingOnes= $etaValues")
        doublesAreClose(expected, etaValues)
    }

    @Test
    fun test_shrink_trunc_problem() {
        val x = listOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0)
        val eta0 = .51

        println("test_shrink_trunc_problem $eta0 x=$x")
        val u = 1.0
        val d = 10
        val f = 0.0
        val minsd = 1.0e-6
        val t= 0.5
        val c = (eta0 - t) / 2
        val N = x.size

        val estimFn = TruncShrinkage(N = N, upperBound = u, minsd = minsd, d = d, eta0 = eta0, f = f, c = c)

        val assortValues = PrevSamples()
        val etaValues = mutableListOf<Double>()
        x.forEach {
            val eta = estimFn.eta(assortValues)
            println(" eta = $eta")
            etaValues.add(eta)
            assortValues.addSample(it)
        }

        val expected = listOf(0.51, 0.55454545, 0.59166667, 0.62307692, 0.65, 0.67333333, 0.69375, 0.65294118, 0.61666667, 0.58421053)
        println("expected = $expected")
        println("actual = $etaValues")

        expected.forEachIndexed { idx, it ->
            assertTrue(doubleIsClose(it, etaValues[idx]))
        }
    }

    @Test
    fun test_leading_zeros_wreplace() {
        val x = listOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 1.0, )
        val eta0 = .51

        println("test_leading_zeros_wreplace $eta0 x=$x")
        val u = 1.0
        val d = 10
        val f = 0.0
        val minsd = 1.0e-6
        val t= 0.5
        val c = (eta0 - t) / 2
        val N = x.size

        val estimFn = TruncShrinkage(
            N = N,
            withoutReplacement = false,
            upperBound = u,
            minsd = minsd,
            d = d,
            eta0 = eta0,
            f = f,
            c = c
        )

        val assortValues = PrevSamples()
        val etaValues = mutableListOf<Double>()
        x.forEach {
            val eta = estimFn.eta(assortValues)
            println(" eta = $eta")
            etaValues.add(eta)
            assortValues.addSample(it)
        }

        // test_leading_zeros_wreplace=alpha_mart=array([0.51      , 0.50150756, 0.50144338, 0.50138675, 0.50133631,
        //       0.50129099, 0.50125   , 0.50121268, 0.50117851, 0.50114708])
        val expected = listOf(0.51      , 0.50150756, 0.50144338, 0.50138675, 0.50133631, 0.50129099, 0.50125, 0.50121268, 0.50117851, 0.50114708)
        println("expected = $expected")
        println("actual = $etaValues")

        expected.forEachIndexed { idx, it ->
            assertTrue(doubleIsClose(it, etaValues[idx]))
        }
    }
}