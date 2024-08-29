package org.cryptobiotic.rlauxe

import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals


// Direct compare TruncShrinkage with output from SHANGRLA TestNonnegMean output
class TestShrinkTrunc {

    @Test
    fun testTruncShrinkageLeadingZeroes() {
        val eta0 = .51
        val x = listOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 1.0, )
        val expected = listOf(0.51, 0.5570631122784444, 0.626443375672974, 0.7156724647762773, 0.8346696395428955, 0.9999999999999998, 0.9999999999999998, 0.9999999999999998, 0.9999999999999998, 0.9999999999999998)

        val t = .5
        val u = 1.0
        val d = 10
        val f = 0.0
        val minsd = 1.0e-6
        val c = (eta0 - t) / 2
        val N = x.size

        val estimFn = TruncShrinkage(N = N, u=u, minsd=minsd, d=d, eta0=eta0, f=f, c=c)
        val result = mutableListOf<Double>()
        repeat(x.size) {
            val sublist = x.subList(0, it+1)
            val estim = estimFn.eta(sublist)
            println("estim = $estim")
            result.add(estim)
        }
        doublesAreEqual(expected, result)
    }

    @Test
    fun testTruncShrinkageLeadingOnes() {
        val eta0 = .51
        val x = listOf(1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val expected = listOf(0.51, 0.5545454545454546, 0.5916666666666667, 0.6230769230769231, 0.65, 0.6733333333333333, 0.63125, 0.5941176470588235, 0.5611111111111111, 0.531578947368421)
        val t = .5
        val u = 1.0
        val d = 10
        val f = 0.0
        val minsd = 1.0e-6
        val c = (eta0 - t) / 2

        val N = x.size

        val estimFn = TruncShrinkage(N = N, u=u, minsd=minsd, d=d, eta0=eta0, f=f, c=c)
        val result = mutableListOf<Double>()
        repeat(x.size) {
            val sublist = x.subList(0, it+1)
            val estim = estimFn.eta(sublist)
            // println(" $it sublist = ${sublist} estim = $estim")
            result.add(estim)
        }
        doublesAreEqual(expected, result)
    }

    // direct test with SHANGRLA test_shrink_trunc_problem
    // def test_shrink_trunc_problem(self):
    //        epsj = lambda c, d, j: c / math.sqrt(d + j - 1)
    //        Sj = lambda x, j: 0 if j == 1 else np.sum(x[0:j - 1])
    //        tj = lambda N, t, x, j: (N * t - Sj(x, j)) / (N - j + 1) if np.isfinite(N) else t
    //        eta = .51
    //        t = 1 / 2
    //        u = 1
    //        d = 10
    //        f = 0
    //        x = np.array([1, 1, 1, 1, 1, 1, 0, 0, 0, 0])
    //
    //        test_fin = NonnegMean(t=t, u=u, d=d, f=f)
    //        c = (eta - t) / 2
    //        test_fin.c = c
    //        test_fin.eta = eta
    //
    //        N = len(x)
    //        test_fin.N = N
    //        # xinf = test_inf.shrink_trunc(x)
    //        xfin = test_fin.shrink_trunc(x)
    //        yinf = np.zeros(N)
    //        yfin = np.zeros(N)
    //        for j in range(1, N + 1):
    //            est = (d * eta + Sj(x, j)) / (d + j - 1)
    //            most = u * (1 - np.finfo(float).eps)
    //            yinf[j - 1] = np.minimum(np.maximum(t + epsj(c, d, j), est), most)
    //            yfin[j - 1] = np.minimum(np.maximum(tj(N, t, x, j) + epsj(c, d, j), est), most)
    //        # np.testing.assert_allclose(xinf, yinf)
    //        np.testing.assert_allclose(xfin, yfin)
    //        print(f'{xfin=}')
    //
    // xfin=array([0.51      , 0.55454545, 0.59166667, 0.62307692, 0.65      ,
    //       0.67333333, 0.69375   , 0.65294118, 0.61666667, 0.58421053])

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

        val estimFn = TruncShrinkage(N = N, u=u, minsd=minsd, d=d, eta0=eta0, f=f, c=c)

        val assortValues = mutableListOf<Double>()
        val etaValues = mutableListOf<Double>()
        x.forEach {
            assortValues.add(it)
            val eta = estimFn.eta(assortValues)
            println(" eta = $eta")
            etaValues.add(eta)
        }

        val expected = listOf(0.51, 0.55454545, 0.59166667, 0.62307692, 0.65, 0.67333333, 0.69375, 0.65294118, 0.61666667, 0.58421053)
        println("expected = $expected")
        println("actual = $etaValues")

        expected.forEachIndexed { idx, it ->
            assertTrue(numpy_isclose(it, etaValues[idx]))
        }
    }
}