package org.cryptobiotic.rlauxe.shangrla

import org.cryptobiotic.rlauxe.SampleFromArray
import org.cryptobiotic.rlauxe.SampleFromList
import org.cryptobiotic.rlauxe.core.AgrapaBet
import org.cryptobiotic.rlauxe.core.BettingMart
import org.cryptobiotic.rlauxe.core.FixedBet
import org.cryptobiotic.rlauxe.core.OptimalComparison
import org.cryptobiotic.rlauxe.core.PrevSamples
import org.cryptobiotic.rlauxe.core.eps
import org.cryptobiotic.rlauxe.core.eta_to_lam
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
        var u = 1.0  // TODO should be 2 * noerror
        // test for sampling without replacement, growing c, but zero sample variance
        for (value in listOf(0.75, 0.9)) {
            for (lam in listOf(0.25, 0.5)) {
                println("assort value = $value lam=$lam")
                val agrapa = AgrapaBet(
                    N = N,
                    withoutReplacement = true,
                    upperBound = u,
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
        val c_g_0 = 0.6
        val c_g_m = 0.9
        val c_g_g = 2.0
        var u = 1.0  // TODO should be 2 * noerror

        val x = listOf(0.75, 0.9, 0.9, 0.9, 0.75, 0.9, 0.9, 0.9, 0.9, 0.9)
        val lam = 0.55

        // test for sampling without replacement, growing c, with sample variance
        val agrapa = AgrapaBet(
            N = N,
            withoutReplacement = true,
            upperBound = u,
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

        // from SHANGRLAorg.test_agrapa_with_variance()
        val expected = listOf(
            0.55,
            1.60812183,
            1.66536931,
            1.70245166,
            1.73233083,
            1.75309598,
            1.77771742,
            1.80148744,
            1.82491642,
            1.84834123
        )
        expected.forEachIndexed { idx, it ->
            assertEquals(it, result.etajs[idx], doublePrecision)
        }
    }

    @Test
    fun testOptimalComparison() {
        val N = 100
        val mean = .51                              // .5 < mean <= 1
        val dilutedMargin = 2 * mean - 1            // aka v;  0 < v <= 1
        val noerror = 1.0 / (2.0 - dilutedMargin)   // aka a;  1/2 < a <= 1
        val upperBound = 2 * noerror                // aka u; 1 < u <= 2
        val p2 = .001
        val mu = .5

        val eta =  (1.0 - upperBound * (1.0 - p2)) / (2.0 - 2.0 * upperBound) + upperBound * (1.0 - p2) - 0.5
        val lam =  eta_to_lam(eta, mu, upperBound) // (eta / mu - 1) / (upper - mu)

        // so this equation only works if mu = .5
        val p0 = 1.0 - p2
        val expectedLam = (2 - 4 * noerror * p0) / (1 - 2 * noerror) // Cobra eq 3

        assertEquals(expectedLam, lam)
    }

    @Test
    fun testOptimalComparisonBet() {
        val N = 100
        val mean = .51
        val dilutedMargin = 2 * mean - 1
        val noerror = 1.0 / (2.0 - dilutedMargin)
        val upperBound = 2 * noerror
        val p2 = .001

        val betFn = OptimalComparison(
            N = N,
            withoutReplacement = true,
            upperBound = upperBound,
            p2 = p2
        )

        val prevSamples = PrevSamples()
        val x = listOf(1, 1, 1, 0, 1, 1, 0, 0, 1, 1)
        val bets = mutableListOf<Double>()
        x.forEach {
            bets.add(betFn.bet(prevSamples))
            prevSamples.addSample(it * noerror)
        }
        println("bets = $bets")
    }

    @Test
    fun testOptimalComparisonBet2() {
        val N = 100
        val mean = .51
        val dilutedMargin = 2 * mean - 1
        val noerror = 1.0 / (2.0 - dilutedMargin)
        val upperBound = 2 * noerror
        val p2 = .001
        println("upperBound = $upperBound")
        val betFn2 = OptimalComparison(
            N = N,
            withoutReplacement = true,
            upperBound = 1.0 + eps,
            p2 = p2
        )

        val x = listOf(1, 1, 1, 0, 1, 1, 0, 0, 1, 1)
        val prevSamples2 = PrevSamples()
        val bets2 = mutableListOf<Double>()
        x.forEach {
            bets2.add( betFn2.bet(prevSamples2) )
            prevSamples2.addSample(it.toDouble())
        }
        println("bets2 = $bets2")
    }

}