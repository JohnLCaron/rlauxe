package org.cryptobiotic.rlauxe.shangrla

import org.cryptobiotic.rlauxe.core.PrevSamples
import org.cryptobiotic.rlauxe.core.TruncShrinkage
import org.cryptobiotic.rlauxe.core.populationMeanIfH0
import org.cryptobiotic.rlauxe.doublesAreClose
import org.cryptobiotic.rlauxe.util.doubleIsClose
import kotlin.test.Test
import kotlin.test.assertTrue


// Direct compare TruncShrinkage with output from SHANGRLA TestNonnegMean test_leading_zeros output
class TestShrinkTrunc {

    // shrink: m=array([0.5       , 0.55555556, 0.625     , 0.71428571, 0.83333333,
    //       1.        , 1.        , 1.        , 1.        , 1.        ])
    //
    // test_leading_zeros=alpha_mart=array([0.51      , 0.55706311, 0.62644338, 0.71567246, 0.83466964,
    //       1.        , 1.        , 1.        , 1.        , 1.        ])

    // shrink: m=array([0.5       , 0.55555556, 0.625     , 0.71428571, 0.83333333, 1.        , 1.        , 1.        , 1.        , 1.        ])
    // est=array([0.51      , 0.46363636, 0.425     , 0.39230769, 0.36428571, 0.34      , 0.38125   , 0.41764706, 0.45      , 0.47894737])
    // capBelow=array([0.50158114, 0.55706311, 0.62644338, 0.71567246, 0.83466964, 1.00129099, 1.00125   , 1.00121268, 1.00117851, 1.00114708])
    //
    //test_leading_zeros=etas=array([0.51      , 0.55706311, 0.62644338, 0.71567246, 0.83466964,  1.        , 1.        , 1.        , 1.        , 1.        ])
    @Test
    fun testTruncShrinkageLeadingZeroes() {
        val x = listOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 1.0, )

        // values from SHANGRLA test_NonnegMean.test_alpha_mart1
        val expectedmeans = listOf(0.5, 0.55555556, 0.625, 0.71428571, 0.83333333, 1.0, 1.0, 1.0, 1.0, 1.0, )

        val meanValues = PrevSamples()
        val means = mutableListOf<Double>()
        x.forEach {
            means.add(populationMeanIfH0(x.size, true, meanValues))
            meanValues.addSample(it)
        }
        println("populationMeanIfH0=$means")
        println()
        doublesAreClose(expectedmeans, means)

        val expected = listOf(0.51, 0.55706311, 0.62644338, 0.71567246, 0.83466964,  1.0, 1.0 , 1.0 , 1.0, 1.0)
        val t = .5
        val u = 1.0
        val d = 10
        val minsd = 1.0e-6
        val eta0 = .51
        val c = (eta0 - t) / 2

        val N = x.size
        val estimFn = TruncShrinkage(N = N, upperBound = u, minsd = minsd, d = d, eta0 = eta0, c = c)
        val assortValues = PrevSamples()
        val etaValues = mutableListOf<Double>()
        x.forEach {
            val eta = estimFn.eta(assortValues)
            etaValues.add(eta)
            assortValues.addSample(it)
        }
        println("testTruncShrinkageLeadingZeroes= $etaValues")
        //est = 0.51 sampleSum=0.0 d=10 eta0=0.51 dj1=10.0 lastj = 0, capBelow=0.45612659337553874(false)
        //est = 0.4636363636363636 sampleSum=0.0 d=10 eta0=0.51 dj1=11.0 lastj = 1, capBelow=0.5015075567228888(true)
        //est = 0.425 sampleSum=0.0 d=10 eta0=0.51 dj1=12.0 lastj = 2, capBelow=0.5569989312285296(true)
        //est = 0.3923076923076923 sampleSum=0.0 d=10 eta0=0.51 dj1=13.0 lastj = 3, capBelow=0.626386750490563(true)
        //est = 0.36428571428571427 sampleSum=0.0 d=10 eta0=0.51 dj1=14.0 lastj = 4, capBelow=0.7156220204952765(true)
        //est = 0.33999999999999997 sampleSum=0.0 d=10 eta0=0.51 dj1=15.0 lastj = 5, capBelow=0.8346243277820692(true)
        //est = 0.38125 sampleSum=1.0 d=10 eta0=0.51 dj1=16.0 lastj = 6, capBelow=1.00125(true)
        //est = 0.41764705882352937 sampleSum=2.0 d=10 eta0=0.51 dj1=17.0 lastj = 7, capBelow=1.0012126781251818(true)
        //est = 0.44999999999999996 sampleSum=3.0 d=10 eta0=0.51 dj1=18.0 lastj = 8, capBelow=1.0011785113019775(true)
        //est = 0.4789473684210526 sampleSum=4.0 d=10 eta0=0.51 dj1=19.0 lastj = 9, capBelow=1.0011470786693528(true)
        // testTruncShrinkageLeadingZeroes= [0.51, 0.5015075567228888, 0.5569989312285296, 0.626386750490563, 0.7156220204952765, 0.8346243277820692, 0.9999999999999998, 0.9999999999999998, 0.9999999999999998, 0.9999999999999998]
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
        val minsd = 1.0e-6
        val c = (eta0 - t) / 2

        val N = x.size
        val estimFn = TruncShrinkage(N = N, upperBound = u, minsd = minsd, d = d, eta0 = eta0, c = c)
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
        val minsd = 1.0e-6
        val t= 0.5
        val c = (eta0 - t) / 2
        val N = x.size

        val estimFn = TruncShrinkage(N = N, upperBound = u, minsd = minsd, d = d, eta0 = eta0, c = c)

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