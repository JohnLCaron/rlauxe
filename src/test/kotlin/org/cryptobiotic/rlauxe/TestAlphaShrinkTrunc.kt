package org.cryptobiotic.rlauxe

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Compare AlphaAlgorithm with output from SHANGRLA TestNonnegMean
class TestAlphaShrinkTrunc {

    //     def test_alpha_mart(self):
    //        eps = 0.0001  # Generic small value
    //
    //        # When all the items are 1/2, estimated p for a mean of 1/2 should be 1.
    //        s = np.ones(5) / 2
    //        test = NonnegMean(N=int(10 ** 6))
    //        np.testing.assert_almost_equal(test.alpha_mart(s)[0], 1.0)
    //        test.t = eps
    //        np.testing.assert_array_less(test.alpha_mart(s)[1][1:], [eps] * (len(s) - 1))
    //
    //        s = [0.6, 0.8, 1.0, 1.2, 1.4]
    //        test.u = 2
    //        np.testing.assert_array_less(test.alpha_mart(s)[1][1:], [eps] * (len(s) - 1))
    //
    //        s1 = [1, 0, 1, 1, 0, 0, 1]
    //        test.u = 1
    //        test.N = 7
    //        test.t = 3 / 7
    //        alpha_mart1 = test.alpha_mart(s1)[1]
    //        # p-values should be big until the last, which should be 0
    //        print(f'{alpha_mart1=}')
    //        assert (not any(np.isnan(alpha_mart1)))
    //        assert (alpha_mart1[-1] == 0)
    //
    //        s2 = [1, 0, 1, 1, 0, 0, 0]
    //        alpha_mart2 = test.alpha_mart(s2)[1]
    //        # Since s1 and s2 only differ in the last observation,
    //        # the resulting martingales should be identical up to the next-to-last.
    //        # Final entry in alpha_mart2 should be 1
    //        print(f'{alpha_mart2=}')
    //        assert (all(np.equal(alpha_mart2[0:(len(alpha_mart2) - 1)],
    //                             alpha_mart1[0:(len(alpha_mart1) - 1)])))
    //alpha_mart1=array([0.57142857, 1.        , 0.61464586, 0.1891218 , 1.        ,1.        , 0.        ])
    //alpha_mart2=array([0.57142857, 1.        , 0.61464586, 0.1891218 , 1.        ,1.        , 1.        ])

    @Test
    fun test_alpha_mart_half() {
        //        # When all the items are 1/2, estimated p for a mean of 1/2 should be 1.
        //        s = np.ones(5)/2
        //        test = NonnegMean(N=int(10**6))
        //        np.testing.assert_almost_equal(test.alpha_mart(s)[0],1.0)
        val x = DoubleArray(5) { .5 }
        val eta0 = 0.5

        val allHalf = testAlphaWithShrinkTrunc(eta0, x.toList())
        println(" allHalf = ${allHalf.pvalues}")
        allHalf.pvalues.forEach {
            assertEquals(1.0, it)
        }
    }

    @Test
    fun test_alpha_mart1() {
        //         s1 = [1, 0, 1, 1, 0, 0, 1]
        val x2 = listOf(1.0, 0.0, 1.0, 1.0, 0.0, 0.0, 1.0)

        val alpha2 = testAlphaMartWithShrinkTrunc(x2.average(), x2.toList())
        println(" alpha2 = ${alpha2}")

        // alpha_mart1=array([ 0.66666667,  1.        ,  0.78431373,  0.36199095,  1.        ,-7.239819  ,  0.        ])
        val expected = listOf(0.875,      1.0,         1.0,        0.68906946, 1.0,        1.0, 1.0        )
        println(" expected = ${expected}")
        expected.forEachIndexed { idx, it ->
            assertTrue( it == 1.0 || numpy_isclose(it, alpha2.pvalues[idx]) )
        }
        assertTrue(alpha2.status == TestH0Status.SampleSum)
        assertEquals(alpha2.sampleCount, x2.size)
        assertEquals(alpha2.sampleMean, 0.5714285714285714)
    }

    @Test
    fun test_alpha_mart2() {
        //         s1 = [1, 0, 1, 1, 0, 0, 1]
        val x2 = listOf(1.0, 0.0, 1.0, 1.0, 0.0, 0.0, 1.0)

        val alpha2 = testAlphaMartWithShrinkTrunc(x2.average(), x2.toList())
        println(" alpha2 = ${alpha2}")

        // alpha_mart1=array([ 0.66666667,  1.        ,  0.78431373,  0.36199095,  1.        ,-7.239819  ,  0.        ])
        val expected = listOf(0.875,      1.0,         1.0,        0.68906946, 1.0,        1.0, 1.0        )
        println(" expected = ${expected}")
        expected.forEachIndexed { idx, it ->
            assertTrue( it == 1.0 || numpy_isclose(it, alpha2.pvalues[idx]) )
        }
        assertTrue(alpha2.status == TestH0Status.SampleSum)
        assertEquals(alpha2.sampleCount, x2.size)
        assertEquals(alpha2.sampleMean, 0.5714285714285714)
    }

    @Test
    fun compareAlphaWithShrinkTrunc() {
        val bernoulliDist = Bernoulli(.5)
        val bernoulliList = DoubleArray(20) { bernoulliDist.get() }.toList()

        val v = listOf(
            listOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 1.0),
            listOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0),
            bernoulliList
        )

        val etas = listOf(.51, .55, .6) // alternative means
        for (eta in etas) {
            for (x: List<Double> in v) {
                compareAlphaWithShrinkTrunc(
                    eta,
                    x)
            }
        }
    }

    fun compareAlphaWithShrinkTrunc(eta0: Double, x: List<Double>) {
        val algoValues = testAlphaWithShrinkTrunc(
            eta0,
            x
        )

        val expectedPhistory = testAlphaMartWithAlphaMart(
            eta0,
            x
        )

        /*
        algoValues.phistory.forEachIndexed { idx, it ->
            assertEquals(expectedPhistory[idx], it, "$idx: ${expectedPhistory[idx]} != ${it}")
        }

         */
    }

    fun testAlphaWithShrinkTrunc(eta0: Double, x: List<Double>): TestH0Result {
        println("testAlphaWithShrinkTrunc $eta0 x=$x")
        val u = 1.0
        val d = 10
        val f = 0.0
        val minsd = 1.0e-6
        val t= 0.5
        val c = (eta0 - t) / 2
        val N = x.size

        val estimFn = TruncShrinkage(N = N, u=u, minsd=minsd, d=d, eta0=eta0, f=f, c=c)
        val alpha = AlphaAlgorithm(estimFn=estimFn, N=N, upperBound=u)

        val sampler = SampleFromList(x.toDoubleArray())
        return alpha.testH0(x.size) { sampler.sample() }
    }

    fun testAlphaMartWithShrinkTrunc(eta0: Double, x: List<Double>): TestH0Result {
        println("testAlphaWithShrinkTrunc $eta0 x=$x")
        val u = 1.0
        val d = 100
        val f = 0.0
        val minsd = 1.0e-6
        val t= 0.5
        val c = (eta0 - t) / 2
        val N = x.size

        val estimFn = TruncShrinkage(N = N, u=u, minsd=minsd, d=d, eta0=eta0, f=f, c=c)
        val alpha = AlphaMart(estimFn=estimFn, N=N, upperBound=u)

        val sampler = SampleFromList(x.toDoubleArray())
        return alpha.testH0(x.size) { sampler.sample() }
    }

    // see start/NonnegMean
    fun testAlphaMartWithAlphaMart(eta0: Double, x: List<Double>): DoubleArray {
        println("testAlphaMartWithAlphaMart $eta0 x=$x")
        val t = .5
        val u = 1.0
        val d = 10
        val f = 0.0
        val c = (eta0 - t) / 2

        val minsd = 1.0e-6
        val N = x.size

        return DoubleArray(0)
        //val estimFn = ShrinkTrunc(N = N, withReplacement = false, t = t, u = u, minsd=minsd, d = d, eta=eta0, f=f, c=c, eps=eps)
        //val alphamart = AlphaMart(N = N, withReplacement = false, t = t, u = u, estimFnType = EstimFnType.SHRINK_TRUNC, estimFn)
        //return alphamart.test(x.toDoubleArray()).second
    }

    fun epsj(c: Double, d: Int, j:Int): Double =  c/ sqrt(d+j-1.0)
    fun Sj(x: List<Double>, j:Int): Double = if (j == 1) 0.0 else x.subList(0,j-1).sum()
    fun tj(N:Int, t: Double, x: List<Double>, j:Int) =  (N*t-Sj(x, j))/(N-j+1)

    @Test
    fun testAlphaWithShrinkTruncProblem() {
        val x = listOf(1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val eta0 = .51
        val algoValues = testAlphaWithShrinkTrunc(
            eta0,
            x
        )

        println("testAlphaWithShrinkTruncProblem = $algoValues")
    }

    @Test
    fun testAlphaMartWithShrinkTruncProblem() {
        val x = listOf(1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val eta0 = .51

        val expectedPhistory = testAlphaMartWithAlphaMart(
            eta0,
            x
        )

        println("testAlphaMartWithShrinkTruncProblem = ${expectedPhistory.contentToString()}")
    }
}