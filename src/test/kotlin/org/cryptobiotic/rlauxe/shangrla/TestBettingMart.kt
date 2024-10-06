package org.cryptobiotic.rlauxe.shangrla

import org.cryptobiotic.rlauxe.SampleFromArray
import org.cryptobiotic.rlauxe.SampleFromList
import org.cryptobiotic.rlauxe.core.AgrapaBet
import org.cryptobiotic.rlauxe.core.BettingMart
import org.cryptobiotic.rlauxe.core.FixedBet
import org.cryptobiotic.rlauxe.doublePrecision
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBettingMart {

    //     def test_betting_mart(self):
    //        N = np.inf
    //        n = 20
    //        t = 0.5
    //        u = 1
    //        for val in [0.75, 0.9]:
    //            for lam in [0.25, 0.5]:
    //                test = NonnegMean(N=N, u=u, bet=NonnegMean.fixed_bet, lam=lam)
    //                x = val*np.ones(n)
    //                np.testing.assert_almost_equal(test.betting_mart(x)[0], 1/(1+lam*(val-t))**n)

    @Test
    fun testBettingMart() {
        val N = 1000 // Double.POSITIVE_INFINITY
        val n = 20
        val t = 0.5
        val u = 1
        val values = listOf(0.75, 0.9)
        val lams = listOf(0.25, 0.5)
        for (value in values) {
            for (lam in lams) {
                // class BettingMart(
                //    val bettingFn : BettingFn,
                //    val N: Int,             // number of ballot cards
                //    val withoutReplacement: Boolean = true,
                //    val riskLimit: Double = 0.05, // α ∈ (0, 1)
                //    val upperBound: Double = 1.0,  // aka u
                //)
                println("assort value = $value lam=$lam")
                val betta = BettingMart(bettingFn = FixedBet(lam), N = N, withoutReplacement = false)
                val x = DoubleArray(n) { value }
                val sampler = SampleFromArray(x)
                val result = betta.testH0(x.size, false, showDetails = false) { sampler.sample() }
                println("  ${result}")

                // return min(1, 1 / np.max(terms)), np.minimum(1, 1 / terms)
                println("pvalues=  ${result.pvalues}")

                val expected = 1 / (1 + lam * (value - t)).pow(n)
                println("expected=  ${expected}")

                assertEquals(expected, result.pvalues.last(), 1e-6)
            }
        }
    }

    /*
        def test_agrapa(self):
        t = 0.5
        c_g_0 = 0.5
        c_g_m = 0.99
        c_g_g = 0
        N = np.inf
        u = 1
        n = 10
        # test for sampling with replacement, constant c
        for val in [0.6, 0.7]:
            for lam in [0.2, 0.5]:
                test = NonnegMean(N=N, u=u, bet=NonnegMean.fixed_bet,
                                  c_grapa_0=c_g_0, c_grapa_m=c_g_m, c_grapa_grow=c_g_g,
                                  lam=lam)
                x = val * np.ones(n)
                lam_0 = test.agrapa(x)
                term = max(0, min(c_g_0 / t, (val - t) / (val - t) ** 2))
                lam_t = term * np.ones_like(x)
                lam_t[0] = lam
                np.testing.assert_almost_equal(lam_0, lam_t)
        # test for sampling without replacement, growing c, but zero sample variance
        N = 10
        n = 5
        t = 0.5
        c_g_0 = 0.6
        c_g_m = 0.9
        c_g_g = 2
        for val in [0.75, 0.9]:
            for lam in [0.25, 0.5]:
                test = NonnegMean(N=N, u=u, bet=NonnegMean.agrapa,
                                  c_grapa_0=c_g_0, c_grapa_max=c_g_m, c_grapa_grow=c_g_g,
                                  lam=lam)
                x = val * np.ones(n)
                lam_0 = test.agrapa(x)
                t_adj = np.array([(N * t - i * val) / (N - i) for i in range(n)])
                mj = val
                lam_t = (mj - t_adj) / (mj - t_adj) ** 2
                lam_t = np.insert(lam_t, 0, lam)[0:-1]
                j = np.arange(n)
                cj = c_g_0 + (c_g_m - c_g_0) * (1 - 1 / (1 + c_g_g * np.sqrt(j)))
                lam_t = np.minimum(cj / t_adj, lam_t)
                np.testing.assert_almost_equal(lam_0, lam_t)
     */
    /*
    fun testAgrapa() {
    var t = 0.5
    var c_g_0 = 0.5
    var c_g_m = 0.99
    var c_g_g = 0
    var N = Double.POSITIVE_INFINITY
    var u = 1
    var n = 10
    // test for sampling with replacement, constant c
    for (val in listOf(0.6, 0.7)) {
        for (lam in listOf(0.2, 0.5)) {
            val test = NonnegMean(N, u, NonnegMean.fixedBet,
                                  c_g_0, c_g_m, c_g_g, lam)
            val x = DoubleArray(n) { `val` }
            val lam_0 = test.agrapa(x)
            val term = max(0.0, min(c_g_0 / t, (`val` - t) / (`val` - t).pow(2)))
            val lam_t = DoubleArray(n) { term }
            lam_t[0] = lam
            Assert.assertArrayEquals(lam_0, lam_t, 0.001)
        }
    }

    // test for sampling without replacement, growing c, but zero sample variance
    N = 10.0
    n = 5
    t = 0.5
    c_g_0 = 0.6
    c_g_m = 0.9
    c_g_g = 2
    for (val in listOf(0.75, 0.9)) {
        for (lam in listOf(0.25, 0.5)) {
            val test = NonnegMean(N, u, NonnegMean.agrapa,
                                  c_g_0, c_g_m, c_g_g, lam)
            val x = DoubleArray(n) { `val` }
            val lam_0 = test.agrapa(x)
            val t_adj = DoubleArray(n) { i -> (N * t - i * `val`) / (N - i) }
            val mj = `val`
            val lam_t = t_adj.map { (mj - it) / (mj - it).pow(2) }.toDoubleArray()
            lam_t[0] = lam
            val `j` = IntArray(n) { it }
            val `cj` = `j`.map { c_g_0 + (c_g_m - c_g_0) * (1 - 1 / (1 + c_g_g * sqrt(it.toDouble()))) }.toDoubleArray()
            val lam_t = DoubleArray(n) { i -> min(`cj`[i] / t_adj[i], lam_t[i]) }
            Assert.assertArrayEquals(lam_0, lam_t, 0.001)
        }
    }
}
     */
    @Test
    fun testAgrapa0() {
        var t = 0.5
        var c_g_0 = 0.5
        var c_g_m = 0.99
        var c_g_g = 0.0 // this makes it pretty simple
        var N = 1000
        var u = 1.0
        var n = 10
        // test for sampling with replacement, constant c
        for (value in listOf(0.6, 0.7)) {
            for (lam in listOf(0.2, 0.5)) {
                println("assort value = $value lam=$lam")
                val agrapa = AgrapaBet(
                    N = N,
                    withoutReplacement = false, // another simpleton
                    upperBound = u,
                    t = t,
                    lam0 = lam,
                    c_grapa_0 = c_g_0,
                    c_grapa_max = c_g_m,
                    c_grapa_grow = c_g_g,
                )
                val betta = BettingMart(bettingFn = agrapa, N = N, withoutReplacement = false)
                val x = DoubleArray(n) { value }
                val sampler = SampleFromArray(x)
                val result = betta.testH0(x.size, false, showDetails = false) { sampler.sample() }
                println("  ${result}")
                println("   bets=  ${result.etajs}") // these are the bets, despite the name

                result.etajs.forEachIndexed { index, bet ->
                    val expected = if (index == 0) lam else max(0.0, min(c_g_0 / t, 1.0 / (value - t)))
                    assertEquals(expected, bet, 1e-6)

                }
            }
        }
    }

    @Test
    fun testAgrapa() {
        val N = 10
        val n = 5
        val t = 0.5
        val c_g_0 = 0.6
        val c_g_m = 0.9
        val c_g_g = 2.0
        var u = 1.0
        // test for sampling without replacement, growing c, but zero sample variance
        for (value in listOf(0.75, 0.9)) {
            for (lam in listOf(0.25, 0.5)) {
                println("assort value = $value lam=$lam")
                val agrapa = AgrapaBet(
                    N = N,
                    withoutReplacement = true,
                    upperBound = u,
                    t = t,
                    lam0 = lam,
                    c_grapa_0 = c_g_0,
                    c_grapa_max = c_g_m,
                    c_grapa_grow = c_g_g,
                )
                val betta = BettingMart(bettingFn = agrapa, N = N, withoutReplacement = false)
                val x = DoubleArray(n) { value }
                val sampler = SampleFromArray(x)
                val result = betta.testH0(x.size, false, showDetails = false) { sampler.sample() }
                println("  ${result}")
                println("   bets=  ${result.etajs}") // these are the bets, despite the name

                result.etajs.forEachIndexed { index, bet ->
                    // (N * t - prevSamples.sum()) / ( N - lastSampleNumber)
                    val t_adj = (N * t - index * value) / (N - index)

                    //         val lamj = if (lastSampleNumber == 0) lam0 else {
                    //            val mj = prevSamples.mean()
                    //            val sdj2 = sqrt(prevSamples.variance())
                    //            val tmm = (mj - t_adj)
                    //            tmm / (sdj2 + tmm * tmm)
                    //        }
                    val mj = value
                    val lam_t = if (index == 0) lam else 1.0 / (mj - t_adj)

                    //  val c = c_grapa_0 + (c_grapa_max - c_grapa_0) * (1.0 - 1.0 / (1 + c_grapa_grow * sqrt(lastSampleNumber.toDouble())))
                    val cj = c_g_0 + (c_g_m - c_g_0) * (1.0 - 1.0 / (1.0 + c_g_g * sqrt(index.toDouble())))
                    //  lamj = np.maximum(0, np.minimum(c / t_adj, lamj))
                    val expected = min(cj / t_adj, lam_t)
                    assertEquals(expected, bet, 1e-6)
                }
            }
        }
    }

    //     def test_agrapa_with_variance(self):
    //        # test for sampling without replacement, growing c, with sample variance
    //        N = 100
    //        n = 10
    //        t = 0.5
    //        u = 1.0
    //        c_g_0 = 0.6
    //        c_g_m = 0.9
    //        c_g_g = 2.0
    //
    //        x = [0.75, 0.9, 0.9, 0.9, 0.75, 0.9, 0.9, 0.9, 0.9, 0.9]
    //        lam = 0.55
    //
    //        test = NonnegMean(N=N, u=u, bet=NonnegMean.agrapa,
    //                          c_grapa_0=c_g_0, c_grapa_max=c_g_m, c_grapa_grow=c_g_g,
    //                          lam=lam)
    //        bets = test.agrapa(x)
    //        print(f"\n {bets=}")
    @Test
    fun testAgrapaWithVariance() {
        val N = 100
        val n = 10
        val t = 0.5
        val c_g_0 = 0.6
        val c_g_m = 0.9
        val c_g_g = 2.0
        var u = 1.0

        val x = listOf(0.75, 0.9, 0.9, 0.9, 0.75, 0.9, 0.9, 0.9, 0.9, 0.9)
        val lam = 0.55

        // test for sampling without replacement, growing c, with sample variance
        val agrapa = AgrapaBet(
            N = N,
            withoutReplacement = true,
            upperBound = u,
            t = t,
            lam0 = lam,
            c_grapa_0 = c_g_0,
            c_grapa_max = c_g_m,
            c_grapa_grow = c_g_g,
        )
        val betta = BettingMart(bettingFn = agrapa, N = N, withoutReplacement = false)
        val sampler = SampleFromList(x)
        val result = betta.testH0(x.size, false, showDetails = false) { sampler.sample() }
        println("  ${result}")
        println("   bets=  ${result.etajs}") // these are the bets, despite the name

        val expected = listOf(0.55,  1.60812183, 1.66536931, 1.70245166, 1.73233083, 1.75309598, 1.77771742, 1.80148744, 1.82491642, 1.84834123)
        expected.forEachIndexed { idx, it ->
            assertEquals(it, result.etajs[idx], doublePrecision)
        }
    }
}