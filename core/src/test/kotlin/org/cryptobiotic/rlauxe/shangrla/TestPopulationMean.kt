package org.cryptobiotic.rlauxe.shangrla

import org.cryptobiotic.rlauxe.SampleFromArray
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.util.doubleIsClose
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
        val u = 1.0
        val d = 10

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
        assertEquals(0.5714285714285714, assortValues.mean(), doublePrecision)
        assertEquals(0.2448979591836735, assortValues.variance(), doublePrecision)
        // assertEquals(ClcaErrorRates(0.0, 0.0, 0.0, 0.0, ), assortValues.errorRates())

        // compare directly to ALPHA
        val estimFn = TruncShrinkage(N = N, upperBound = u, d = d, eta0 = eta)
        val alpha = AlphaMart(estimFn = estimFn, N = N, upperBound = u)

        val sampler = SampleFromArray(x.toDoubleArray())
        println("alphaTestH0")
        alpha.testH0(x.size, false) { sampler.sample() }

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

    @Test
    fun testMeanAlwaysHalf() {
        val N = 100
        val halfSamples = HalfSamples()
        repeat(100) {
            (N * 0.5 - halfSamples.sum())
            (N - halfSamples.numberOfSamples())
            val mj = populationMeanIfH0(N = N, withoutReplacement = true, sampleTracker = halfSamples)
            // println("mj=${mj} num=$num den=$den")
            halfSamples.count++
            assertEquals(.5, mj) // its the deviations of the sample from 1/2 that cause mj to change
        }
    }

    class HalfSamples(): SampleTracker {
        var count = 0
        override fun last() = .5
        override fun numberOfSamples() = count
        override fun sum() = count * .5
        override fun mean() = 0.5
        override fun variance() = 0.0
        override fun addSample(sample: Double) {
        }
    }

    @Test
    fun testMeanComparison() {
        val N = 100
        val awinnerAvg = .55
        val samples = ComparisonSamples(awinnerAvg)
        println("awinnerAvg=${awinnerAvg} noerror=${samples.noerror}")

        repeat(100) {
            populationMeanIfH0(N = N, withoutReplacement = true, sampleTracker = samples)
            // println("mj=${mj}")
            samples.count++
        }
    }

    class ComparisonSamples(awinnerAvg: Double): SampleTracker {
        val noerror = 1.0 / (3 - 2 * awinnerAvg)
        var count = 0
        override fun last() = noerror
        override fun numberOfSamples() = count
        override fun sum() = count * noerror
        override fun mean() = noerror
        override fun variance() = 0.0
        override fun addSample(sample: Double) {
        }
    }
}

// old way
fun meanUnderNull(N: Int, withoutReplacement: Boolean, x: SampleTracker): Double {
    val t = 0.5
    if (!withoutReplacement) return t  // with replacement
    if (x.numberOfSamples() == 0) return t

    val sum = x.sum()
    val m1 = (N * t - sum)
    val m2 = (N - x.numberOfSamples())
    val m3 = m1 / m2
    return m3
}