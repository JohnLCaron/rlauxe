package org.cryptobiotic.rlauxe.shangrla

import org.cryptobiotic.rlauxe.SampleFromList
import org.cryptobiotic.rlauxe.core.AlphaMart
import org.cryptobiotic.rlauxe.core.PrevSamples
import org.cryptobiotic.rlauxe.core.TestH0Result
import org.cryptobiotic.rlauxe.core.TruncShrinkage
import org.cryptobiotic.rlauxe.core.doubleIsClose
import org.cryptobiotic.rlauxe.core.meanUnderNull
import org.cryptobiotic.rlauxe.core.populationMeanIfH0
import org.cryptobiotic.rlauxe.doublesAreClose
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestPopulationMeanWithoutReplacement {

    @Test
    fun testMean() {
        val x = listOf(0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 0.0, 1.0)
        println(x)

        var sum = 0.0
        val sumMinus1: List<Double> = x.mapIndexed { idx, it ->
            val summ1 = if (idx == 0) 0.0 else sum
            sum += it
            summ1
        }
        println(sumMinus1)
        assertEquals(listOf(0.0, 0.0, 1.0, 1.0, 2.0, 2.0, 2.0, 3.0, 4.0, 4.0), sumMinus1)

        val N = x.size
        val m = sumMinus1.mapIndexed { idx, s ->
            val sampleNum = idx + 1
            (N * .5 - s) / (N - sampleNum + 1)
        }
        println(m)
        assertEquals(listOf(0.5, 0.5555555555555556, 0.5, 0.5714285714285714, 0.5, 0.6, 0.75, 0.6666666666666666, 0.5, 1.0), m)
    }

    @Test
    fun testMeanNeg() {
        val x = listOf(0.0, 1.0, 0.0, 1.0, 1.0, 1.0, 0.0, 1.0, 1.0, 1.0, 1.0, 0.0)
        println(x)

        var sum = 0.0
        val sumMinus1: List<Double> = x.mapIndexed { idx, it ->
            val summ1 = if (idx == 0) 0.0 else sum
            sum += it
            summ1
        }
        println(sumMinus1)
        assertEquals(listOf(0.0, 0.0, 1.0, 1.0, 2.0, 3.0, 4.0, 4.0, 5.0, 6.0, 7.0, 8.0), sumMinus1)

        val N = x.size
        val m = sumMinus1.mapIndexed { idx, s ->
            val sampleNum = idx + 1
            (N * .5 - s) / (N - sampleNum + 1)
        }
        println(m)
        assertEquals(listOf(0.5, 0.5454545454545454, 0.5, 0.5555555555555556, 0.5, 0.42857142857142855, 0.3333333333333333, 0.4, 0.25, 0.0, -0.5, -2.0), m)
        // if mean goes < 0, not enough samples left to make average < .5, so RejectNull
    }

    @Test
    fun testMeanGtOne() {
        val x = listOf(0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0)
        println(x)

        var sum = 0.0
        val sumMinus1: List<Double> = x.mapIndexed { idx, it ->
            val summ1 = if (idx == 0) 0.0 else sum
            sum += it
            summ1
        }
        println(sumMinus1)
        assertEquals(listOf(0.0, 0.0, 1.0, 1.0, 2.0, 2.0, 2.0, 2.0, 3.0, 3.0, 3.0, 4.0), sumMinus1)

        val N = x.size
        val m = sumMinus1.mapIndexed { idx, s ->
            val sampleNum = idx + 1
            (N * .5 - s) / (N - sampleNum + 1)
        }
        println(m)
        assertEquals(listOf(0.5, 0.5454545454545454, 0.5, 0.5555555555555556, 0.5, 0.5714285714285714, 0.6666666666666666, 0.8, 0.75, 1.0, 1.5, 2.0), m)
        // if mean goes > 1, not enough samples left to make average > .5, so AcceptNull
    }

    // match this from SHANGRLA test_NonnegMean.test_alpha_mart1
    //     def test_alpha_mart1(self):
    //        x = [1, 0, 1, 1, 0, 0, 1]
    //        eta = 0.5714285714285714
    //        t = 1 / 2
    //        c = (eta - t) / 2
    //
    //        test_fin = NonnegMean(t=t, u=1, d=10, f=0, estim=NonnegMean.shrink_trunc)
    //        test_fin.c = c
    //        test_fin.eta = eta
    //        test_fin.N = len(x)
    //
    //        alpha_mart1 = test_fin.alpha_mart(x)
    //        print(f'\ntest_alpha_mart1={alpha_mart1=}')
    //
    // alpha:  m=array([0.5       , 0.41666667, 0.5       , 0.375     , 0.16666667,  0.25      , 0.5       ])
    // shrink: m=array([0.5       , 0.41666667, 0.5       , 0.375     , 0.16666667,  0.25      , 0.5       ])
    // test_alpha_mart1=alpha_mart1=(np.float64(0.0), array([0.875, 1., 1., 0.73981759, 1.        ,1.        , 0.        ]))

    @Test
    fun test_alpha_mart1() {
        val x = listOf(1.0, 0.0, 1.0, 1.0, 0.0, 0.0, 1.0)
        println("x=${x}")

        var sum = 0.0
        val prevSum: List<Double> = x.mapIndexed { idx, it ->
            val summ1 = if (idx == 0) 0.0 else sum
            sum += it
            summ1
        }
        println("prevSum=${prevSum}")
        assertEquals(listOf(0.0, 1.0, 1.0, 2.0, 3.0, 3.0, 3.0), prevSum)

        val N = x.size
        val m = prevSum.mapIndexed { idx, s ->
            val sampleNum = idx + 1
            (N * .5 - s) / (N - sampleNum + 1)
        }
        println("m=${m}")

        // values from SHANGRLA test_NonnegMean.test_alpha_mart1
        val expected = listOf(0.5, 0.41666667, 0.5       , 0.375     , 0.16666667,  0.25      , 0.5)
        m.forEachIndexed { idx, it ->
            assertTrue(doubleIsClose(expected[idx], it))
        }

        val eta = 0.5714285714285714
        val t = .5
        val u = 1.0
        val d = 10
        val f = 0.0
        val minsd = 1.0e-6
        val c = (eta - t) / 2

        // compare directly to TruncShrinkage
        val assortValues = PrevSamples()
        val means = mutableListOf<Double>()
        x.forEach {
            means.add(populationMeanIfH0(N, true, assortValues))
            assortValues.addSample(it)

        }
        println("populationMeanIfH0=$means")
        println()
        doublesAreClose(expected, means)

        // compare directly to ALPHA
        val estimFn = TruncShrinkage(N = N, upperBound = u, minsd = minsd, d = d, eta0 = eta, f = f, c = c)
        val alpha = AlphaMart(estimFn = estimFn, N = N, upperBound = u)

        val sampler = SampleFromList(x.toDoubleArray())
        println("alphaTestH0")
        val result: TestH0Result = alpha.testH0(x.size, false) { sampler.sample() }

        // alphaTestH0
        // 1: howAbout sum=0.0 result = 0.5
        // 1: alpha sum=0.0 result = 0.5
        //
        // 2: howAbout sum=1.0 result = 0.4166666666666667
        // 2: alpha sum=1.0 result = 0.4166666666666667
        //
        // 3: howAbout sum=1.0 result = 0.5
        // 3: alpha sum=1.0 result = 0.5
        //
        // 4: howAbout sum=2.0 result = 0.375
        // 4: alpha sum=2.0 result = 0.375
        //
        // 5: howAbout sum=3.0 result = 0.16666666666666666
        // 5: alpha sum=3.0 result = 0.16666666666666666
        //
        // 6: howAbout sum=3.0 result = 0.25
        // 6: alpha sum=3.0 result = 0.25
        //
        // 7: howAbout sum=3.0 result = 0.5
        // 7: alpha sum=3.0 result = 0.5
    }

    @Test
    fun test_leading_zeros() {
        val x = listOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 1.0,)
        println("x=${x}")

        // values from SHANGRLA test_NonnegMean.test_alpha_mart1
        val expected = listOf(0.5, 0.55555556, 0.625, 0.71428571, 0.83333333, 1.0, 1.0, 1.0, 1.0, 1.0, )

        var assortValues = PrevSamples()
        var means = mutableListOf<Double>()
        x.forEach {
            means.add(populationMeanIfH0(x.size, true, assortValues))
            assortValues.addSample(it)
        }
        println("populationMeanIfH0=$means")
        println()
        doublesAreClose(expected, means)

        assortValues = PrevSamples()
        means = mutableListOf<Double>()
        x.forEach {
            means.add(meanUnderNull(x.size, true, assortValues))
            assortValues.addSample(it) // add after !!
        }
        println("meanUnderNull=$means")
        println()
        doublesAreClose(expected, means)
    }
}