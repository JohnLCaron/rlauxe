package org.cryptobiotic.rlauxe.port

import java.lang.Math.log
import java.lang.Math.pow
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.cryptobiotic.*

class TestNonnegMean {

    @Test
    fun test_alpha_mart() {
        //        # When all the items are 1/2, estimated p for a mean of 1/2 should be 1.
        //        s = np.ones(5)/2
        //        test = NonnegMean(N=int(10**6))
        //        np.testing.assert_almost_equal(test.alpha_mart(s)[0],1.0)
        val s1 = DoubleArray(5) { .5 }
        val test = NonnegMean(N = 1_000_000)
        val (p, _) = test.alpha_mart(s1)
        assertEquals(1.0, p)

        // test.t = eps
        // np.testing.assert_array_less(test.alpha_mart(s)[1][1:],[eps]*(len(s)-1))
        val eps = 0.0001  // Generic small value
        val test1 = NonnegMean(N = 1_000_000, t = eps)
        val (_, p_history) = test1.alpha_mart(s1)
        // p_history.forEach{ it < eps }
        p_history.forEach{ it < eps * (s1.size - 1) }

        //        s = [0.6,0.8,1.0,1.2,1.4]
        //        test.u=2 # TODO eta not recalculated
        //        np.testing.assert_array_less(test.alpha_mart(s)[1][1:],[eps]*(len(s)-1))
        val s2 = doubleArrayOf(0.6, 0.8, 1.0, 1.2, 1.4)
        val test2 = NonnegMean(N = 1_000_000, u = 2.0)
        val (_, p2_history) = test2.alpha_mart(s2)
        p2_history.forEach{ it < eps * (s2.size - 1)}

        //       s1 = [1, 0, 1, 1, 0, 0, 1]
        //        test.u=1
        //        test.N = 7
        //        test.t = 3/7
        //        alpha_mart1 = test.alpha_mart(s1)[1]
        //        # p-values should be big until the last, which should be 0
        //        print(f'{alpha_mart1=}')
        //        assert(not any(np.isnan(alpha_mart1)))
        //        assert(alpha_mart1[-1] == 0)
        val s3 = doubleArrayOf(1.0, 0.0, 1.0, 1.0, 0.0, 0.0, 1.0)
        val test3 = NonnegMean(N = 7, u = 1.0, t = 3.0 / 7)
        val (_, p3_history)  = test3.alpha_mart(s3)
        // p-values should be big until the last, which should be 0
        // assert(not any (np.isnan(alpha_mart1)))
        p3_history.forEach{ !it.isNaN() }
        assert(p3_history.last() == 0.0)

        //         s2 = [1, 0, 1, 1, 0, 0, 0]
        //        alpha_mart2 = test.alpha_mart(s2)[1]
        //        # Since s1 and s2 only differ in the last observation,
        //        # the resulting martingales should be identical up to the next-to-last.
        //        # Final entry in alpha_mart2 should be 1
        //        assert(all(np.equal(alpha_mart2[0:(len(alpha_mart2)-1)],
        //                            alpha_mart1[0:(len(alpha_mart1)-1)])))
        //        print(f'{alpha_mart2=}')
        val s4 = doubleArrayOf(1.0, 0.0, 1.0, 1.0, 0.0, 0.0, 0.0)
        val (_, p4_history) = test3.alpha_mart(s4)
        // Since s3 and s4 only differ in the last observation,
        // the resulting martingales should be identical up to the next-to-last.
        // Final entry in alpha_mart2 should be 1
        assertEquals(p3_history.size, p4_history.size)
        repeat(p3_history.size-1) { assertEquals(p3_history[it], p4_history[it])}
        assertEquals(1.0, p4_history.last())
        println("p3_history=${p3_history.contentToString()}")
        println("p4_history=${p4_history.contentToString()}")
    }

    /*
    fun test_shrink_trunc() {
        epsj = lambda c, d, j: c/math.sqrt(d+j-1)
        Sj = lambda x, j: 0 if j == 1 else np.sum(x[0:j-1])
        tj = lambda N, t, x, j: (N*t-Sj(x, j))/(N-j+1) if np.isfinite(N) else t
        etas = [.51, .55, .6]  // alternative means
        t = 1 / 2
        u = 1
        d = 10
        f = 0
        vrand = sp.stats.bernoulli.rvs(1 / 2, size = 20)
        v = [
            np.array([0, 0, 0, 0, 0, 1, 1, 1, 1, 1]),
            np.array([1, 1, 1, 1, 1, 1, 0, 0, 0, 0]),
            vrand
        ]
        test_inf = NonnegMean(N = np.inf, t = t, u = u, d = d, f = f)
        test_fin = NonnegMean(t = t, u = u, d = d, f = f)
        for eta in etas:
        c = (eta - t) / 2
        test_inf.c = c
        test_inf.eta = eta
        test_fin.c = c
        test_fin.eta = eta
        for x in v:
        N = len(x)
        test_fin.N = N
        xinf = test_inf.shrink_trunc(x)
        xfin = test_fin.shrink_trunc(x)
        yinf = np.zeros(N)
        yfin = np.zeros(N)
        for j in range(1, N + 1):
        est = (d * eta + Sj(x, j)) / (d + j - 1)
        most = u * (1 - np.finfo(float).eps)
        yinf[j - 1] = np.minimum(np.maximum(t + epsj(c, d, j), est), most)
        yfin[j - 1] = np.minimum(np.maximum(tj(N, t, x, j) + epsj(c, d, j), est), most)
        np.testing.assert_allclose(xinf, yinf)
        np.testing.assert_allclose(xfin, yfin)
    }

    fun test_kaplan_markov() {
        s = np.ones(5)
        test = NonnegMean(u = 1, N = np.inf, t = 1 / 2)
        np.testing.assert_almost_equal(test.kaplan_markov(s)[0], 2 * * - 5)
        s = np.array([1, 1, 1, 1, 1, 0])
        test.g = 0.1
        np.testing.assert_almost_equal(test.kaplan_markov(s)[0], (1.1 / .6) * * - 5)
        test.random_order = false
        np.testing.assert_almost_equal(test.kaplan_markov(s)[0], (1.1 / .6) * * - 5 * .6 / .1)
        s = np.array([1, -1])
        try :
            test.kaplan_markov(s)
            except ValueError :
            pass
            else:
            raise AssertionError
        }

    fun test_kaplan_wald() {
        s = np.ones(5)
        test = NonnegMean()
        np.testing.assert_almost_equal(test.kaplan_wald(s)[0], 2 * * - 5)
        s = np.array([1, 1, 1, 1, 1, 0])
        test.g = 0.1
        np.testing.assert_almost_equal(test.kaplan_wald(s)[0], (1.9) * * - 5)
        test.random_order = false
        np.testing.assert_almost_equal(test.kaplan_wald(s)[0], (1.9) * * - 5 * 10)
        s = np.array([1, -1])
        try :
            test.kaplan_wald(s)
            except ValueError :
            pass
            else:
            raise AssertionError
        }

     */

    @Test
    fun test_estimateSampleSize() {
        val eta = 0.75
        val u = 1.0
        val N = 1000
        val t = 0.5
        val alpha = 0.05
        val quantile = 0.5
        val prefix = false

        val test = NonnegMean(u = u, N = N, t = t, etaOverride = eta)

        val x = DoubleArray(N / 200 ) { 1.0 }
        val sam_size = test.sample_size(x = x, alpha = alpha, prefix = prefix, quantile = quantile)
        assertEquals(8, sam_size) // ((.75/.5)*1+(.25/.5)*0)**8 = 25 > 1/alpha, so sam_size=8
        /*
        val reps = 100
        sam_size = test.sample_size(x = x, alpha = alpha, reps = reps, prefix = prefix, quantile = quantile)
        np.testing.assert_equal(sam_size, 8) // all simulations should give the same answer
        //
        x = 0.75 * np.ones(math.floor(N / 200))
        sam_size = test.sample_size(x = x, alpha = alpha, reps = reps, prefix = prefix, quantile = quantile)
        np.testing.assert_equal(sam_size, 14) // ((.75/.5)*.75+(.25/.5)*.25)**14 = 22.7 > 1/alpha, so sam_size=14
        */

        val g = 0.1

        val testWald = NonnegMean(
            testFnType = TestFnType.KAPLAN_WALD,
            u = u, N = N, t = t, etaOverride = eta, gOverride = g
        )
        val sam_size_wald = testWald.sample_size(x = x, alpha = alpha, reps = null, prefix = prefix, quantile = quantile)
        //   p-value is \prod ((1-g)*x/t + g), so
        val kw_size = ceil(log(1 / alpha) / log((1 - g) / t + g))
        println("kw_size = $kw_size")
        assertTrue(numpy_isclose(kw_size, sam_size_wald.toDouble()))
        assertEquals(kw_size.toInt(), 5)

        val x75 = DoubleArray(N / 200 ) { 0.75 }
        val sam_size75 = testWald.sample_size(x = x75, alpha = alpha, reps = null, prefix = prefix, quantile = quantile)
        //   p-value is \prod ((1-g)*x/t + g), so
        val kw_size75 = ceil(log(1 / alpha) / log(0.75 * (1 - g) / t + g))
        println("kw_size75 = $kw_size75")
        assertTrue(numpy_isclose(kw_size75, sam_size75.toDouble()))
        assertEquals(kw_size75.toInt(), 9)
    }

    /*
    fun test_lam_to_eta_to_lam() {
        N = 100
        u = 1
        test = NonnegMean(N = N, u = u)
        for lam in [0.5, 1, 2]:
        for mu in [0.5, 0.7, 0.9]:
        eta = mu * (1 + lam * (u - mu))
        np.testing.assert_almost_equal(test.lam_to_eta(lam, mu), eta)
        np.testing.assert_almost_equal(test.eta_to_lam(test.lam_to_eta(lam, mu), mu), lam)
        lam = np.array([0.5, 0.6, 0.7])
        mu = np.array([0.6, 0.65, 0.55])
        eta = np.array(list(mu[i] * (1 + lam[i] * (u - mu[i])) for i in range(len(lam))))
        np.testing.assert_almost_equal(test.lam_to_eta(lam, mu), eta)
        np.testing.assert_almost_equal(test.eta_to_lam(test.lam_to_eta(lam, mu), mu), lam)
    }

    fun test_agrapa() {
        t = 0.5
        c_g_0 = 0.5
        c_g_m = 0.99
        c_g_g = 0
        N = np.infty
        u = 1
        n = 10
        // test for sampling with replacement, constant c
        for val in [0.6, 0.7]:
        for lam in [0.2, 0.5]:
        test = NonnegMean(
            N = N, u = u, bet = NonnegMean.fixed_bet,
            c_grapa_0 = c_g_0, c_grapa_m = c_g_m, c_grapa_grow = c_g_g,
            lam = lam
        )
        x = val *np. ones (n)
        lam_0 = test.agrapa(x)
        term = max(0, min(c_g_0 / t, (val - t) / (
        val -t)**2))
        lam_t = term * np.ones_like(x)
        lam_t[0] = lam
        np.testing.assert_almost_equal(lam_0, lam_t)
        // test for sampling without replacement, growing c, but zero sample variance
        N = 10
        n = 5
        t = 0.5
        c_g_0 = 0.6
        c_g_m = 0.9
        c_g_g = 2
        for val in [0.75, 0.9]:
        for lam in [0.25, 0.5]:
        test = NonnegMean(
            N = N, u = u, bet = NonnegMean.agrapa,
            c_grapa_0 = c_g_0, c_grapa_max = c_g_m, c_grapa_grow = c_g_g,
            lam = lam
        )
        x = val *np. ones (n)
        lam_0 = test.agrapa(x)
        t_adj = np.array(
            [(N * t - i *
                    val ) / (N - i) for i in range(n)])
        mj =
            val
                    lam_t = (mj - t_adj) / (mj - t_adj) * *2
        lam_t = np.insert(lam_t, 0, lam)[0:-1]
        j = np.arange(n)
        cj = c_g_0 + (c_g_m - c_g_0) * (1 - 1 / (1 + c_g_g * np.sqrt(j)))
        lam_t = np.minimum(cj / t_adj, lam_t)
        np.testing.assert_almost_equal(lam_0, lam_t)
    }
    */

    @Test
    fun test_betting_martWR() {
        val N = -1
        val n = 20
        val t = 0.5
        val u = 1.0
        for (scale in listOf(0.75, 0.9)) {
            for (lam in listOf(0.25, 0.5)) {
                println("  scale $scale lam $lam")
                val test = NonnegMean(N = N, u = u, lamOverride = lam, withReplacement = true)
                val x = DoubleArray(n) { scale }
                val (p, _) = test.betting_mart(x)
                val expect = 1 / pow(1 + lam * (scale - t), n.toDouble())
                assertTrue(numpy_isclose(expect, p))
            }
        }
    }

    @Test
    fun test_betting_martWOR() {
        val n = 20
        val t = 0.5
        val u = 1.0
        for (scale in listOf(0.75, 0.9)) {
            for (lam in listOf(0.25, 0.5)) {
                println("  scale $scale lam $lam")
                val test = NonnegMean(N = n, u = u, lamOverride = lam, withReplacement = false)
                val x = DoubleArray(n) { scale }
                val (p, _) = test.betting_mart(x)
                val expect = 1 / pow(1 + lam * (scale - t), n.toDouble())
                println("$expect $p")
                assertEquals(expect, p, .001)
            }
        }
    }


}