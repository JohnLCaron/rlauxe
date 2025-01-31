package org.cryptobiotic.rlauxe.shangrla

import org.cryptobiotic.rlauxe.SampleFromArray
import org.cryptobiotic.rlauxe.core.AlphaMart
import org.cryptobiotic.rlauxe.util.Bernoulli
import org.cryptobiotic.rlauxe.core.TestH0Result
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.core.TruncShrinkage
import org.cryptobiotic.rlauxe.core.eps
import org.cryptobiotic.rlauxe.doublePrecision
import kotlin.test.Test
import kotlin.math.max
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Compare AlphaMart with output from SHANGRLA
class TestAlphaMart {

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

        val allHalf = testAlphaMartBatch(eta0, x.toList())
        println(" allHalf = ${allHalf.pvalues}")
        allHalf.pvalues.forEach {
            assertEquals(1.0, it)
        }
    }

    @Test
    fun test_alpha_mart1() {
        //         s1 = [1, 0, 1, 1, 0, 0, 1]
        val x2 = listOf(1.0, 0.0, 1.0, 1.0, 0.0, 0.0, 1.0)

        val eta0 = .5714285714285714
        require(x2.average() == eta0)
        val alpha2 = testAlphaMartBatch(x2.average(), x2.toList())
        println(" alpha2 = ${alpha2}")

        val expected = listOf(0.875, 1.0, 1.0, 0.73981759, 1.0, 1.0, 1.0)
        println(" expected = ${expected}")
        expected.forEachIndexed { idx, it ->
            if (it != 1.0) {
                assertEquals(it, alpha2.pvalues[idx], doublePrecision)
            }
        }
        assertTrue(!alpha2.status.complete)
        assertEquals(alpha2.sampleCount, x2.size)
        assertEquals(alpha2.sampleMean, 0.5714285714285714)
    }

    @Test
    fun test_alpha_mart2() {
        val x2 = listOf(1.0, 0.0, 1.0, 1.0, 0.0, 0.0, 0.0)

        val alpha2 = testAlphaMartBatch(.51, x2.toList())
        println(" alpha2 = ${alpha2}")

        val expected = listOf(0.98039216, 1.0 , 1.0 , 0.86706352, 1.0, 1.0 , 1.0)
        println(" expected = ${expected}")
        expected.forEachIndexed { idx, it ->
            if (it != 1.0) {
                assertEquals(it, alpha2.pvalues[idx], doublePrecision)
            }
        }
        assertTrue(alpha2.status == TestH0Status.LimitReached)
        assertEquals(alpha2.sampleCount, x2.size)
        assertEquals(alpha2.sampleMean, 0.42857142857142855, doublePrecision)
    }

    // @Test not supporting f1
    fun test_shrink_trunk_f1() {
        val x = listOf(1.0, 0.0, 1.0, 1.0, 0.0, 0.0, 1.0 )
        val eta0 = .51

        println("test_shrink_trunk_f1 $eta0 x=$x")
        val u = 1.0
        val d = 10
        val t= 0.5
        val c = (eta0 - t) / 2
        val N = x.size

        val estimFn = TruncShrinkage(N = N, upperBound = u, d = d, eta0 = eta0, c = c)
        val alpha = AlphaMart(estimFn = estimFn, N = N, upperBound = u)
        val sampler = SampleFromArray(x.toDoubleArray())

        val result = alpha.testH0(x.size, false) { sampler.sample() }
        println(" test_shrink_trunk_f1 = ${result}")

        val expected = listOf(0.74257426, 1.0, 0.96704619, 0.46507099, 1.0, 1.0, 1.0)
        println(" expected = ${expected}")
        expected.forEachIndexed { idx, it ->
            if (it != 1.0) {
                assertEquals(it, result.pvalues[idx], doublePrecision)
            }
        }
        assertTrue(result.status.complete)
        assertEquals(result.sampleCount, x.size)
        assertEquals(result.sampleMean, 0.5714285714285714)
    }

    // @Test not supporting f1
    fun test_shrink_trunk_f1_wreplacement() {
        val x = listOf(1.0, 0.0, 1.0, 1.0, 0.0, 0.0, 1.0 )
        val eta0 = .51

        println("test_shrink_trunk_f1 $eta0 x=$x")
        val u = 1.0
        val d = 10
        val t= 0.5
        val c = (eta0 - t) / 2
        val N = x.size

        val estimFn = TruncShrinkage(
            N = N,
            withoutReplacement = false,
            upperBound = u,
            d = d,
            eta0 = eta0,
            c = c
        )
        val alpha = AlphaMart(estimFn = estimFn, N = N, withoutReplacement = false, upperBound = u)
        val sampler = SampleFromArray(x.toDoubleArray())

        val result = alpha.testH0(x.size, false) { sampler.sample() }
        println(" test_shrink_trunk_f1_wreplacement = ${result}")

        val expected = listOf(0.74257426, 1.0, 0.82889674, 0.53150971, 1.0, 1.0, 1.0)
        println(" expected = ${expected}")
        expected.forEachIndexed { idx, it ->
            if (it != 1.0) {
                assertEquals(it, result.pvalues[idx], doublePrecision)
            }
        }
        assertTrue(result.status.complete)
        assertEquals(result.sampleCount, x.size)
        assertEquals(result.sampleMean, 0.5714285714285714)
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
                compareAlphaWithShrinkTrunc(eta, x)
            }
            println()
        }
    }

    fun compareAlphaWithShrinkTrunc(eta0: Double, x: List<Double>) {
        val algoValues: TestH0Result = testAlphaMartWithTermination(eta0, x)

        val martValues: TestH0Result = testAlphaMartBatch(eta0, x)

        // both failed or both succeeded
        val limit1 = algoValues.status == TestH0Status.LimitReached
        val limit2 = martValues.status == TestH0Status.LimitReached
        assertEquals(limit1, limit2)

        algoValues.pvalues.forEachIndexed { idx, it ->
            assertEquals(it, martValues.pvalues[idx])
        }
    }

    fun testAlphaMartWithTermination(eta0: Double, x: List<Double>): TestH0Result {
        // println("testAlphaMartWithTermination $eta0 x=$x")
        val u = 1.0
        val d = 10
        val t = 0.5
        val c = (eta0 - t) / 2
        val N = x.size

        val estimFn = TruncShrinkage(N = N, upperBound = u, d = d, eta0 = eta0, c = c)
        val alpha = AlphaMart(estimFn = estimFn, N = N, upperBound = u)

        val sampler = SampleFromArray(x.toDoubleArray())
        return alpha.testH0(x.size, true) { sampler.sample() }
    }

    fun testAlphaMartBatch(eta0: Double, x: List<Double>): TestH0Result {
        println("testAlphaMartBatch $eta0 x=$x")
        val u = 1.0
        val d = 10
        val t = 0.5
        val c = max(eps, (eta0 - t) / 2)
        val N = x.size

        val estimFn = TruncShrinkage(N = N, upperBound = u, d = d, eta0 = eta0, c = c)
        val alpha = AlphaMart(estimFn = estimFn, N = N, upperBound = u)

        val sampler = SampleFromArray(x.toDoubleArray())
        return alpha.testH0(x.size, false) { sampler.sample() }
    }

    @Test
    fun testAlphaAlgoWithShrinkTruncProblem() {
        val x = listOf(1.0, 0.0, 1.0, 1.0, 0.0, 0.0, 1.0)
        val eta0 = x.average()
        val algoValues = testAlphaMartWithTermination(eta0, x)
        println("testAlphaAlgoWithShrinkTruncProblem = $algoValues")
    }

    @Test
    fun testAlphaMartWithShrinkTruncProblem() {
        val x = listOf(1.0, 0.0, 1.0, 1.0, 0.0, 0.0, 1.0)
        val eta0 = x.average()

        val martValues = testAlphaMartBatch(eta0, x)
        println("testAlphaMartWithShrinkTruncProblem = ${martValues}")
    }
}