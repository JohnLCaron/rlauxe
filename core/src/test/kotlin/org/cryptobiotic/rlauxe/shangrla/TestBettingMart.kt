package org.cryptobiotic.rlauxe.shangrla

import org.cryptobiotic.rlauxe.SampleFromArray
import org.cryptobiotic.rlauxe.SampleFromList
import org.cryptobiotic.rlauxe.core.AgrapaBet
import org.cryptobiotic.rlauxe.core.BettingMart
import org.cryptobiotic.rlauxe.core.FixedBet
import org.cryptobiotic.rlauxe.core.OptimalComparisonNoP1
import org.cryptobiotic.rlauxe.core.PluralityErrorTracker
import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker
import org.cryptobiotic.rlauxe.betting.lamToEta
import org.cryptobiotic.rlauxe.util.doublePrecision
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

// compare BetaMart results with SHANGRLA
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
        val u = 1.0
        val values = listOf(0.75, 0.9)
        val lams = listOf(0.25, 0.5)
        val tracker = ClcaErrorTracker(0.0, 1.0)

        for (value in values) {
            for (lam in lams) {
                println("assort value = $value lam=$lam")
                val betta = BettingMart(bettingFn = FixedBet(lam),
                    N = N, sampleUpperBound = u)
                val debugSeq = betta.setDebuggingSequences()
                val x = DoubleArray(n) { value }
                val sampler = SampleFromArray(x)
                val result = betta.testH0(x.size, false, tracker=tracker) { sampler.sample() }
                println("  ${result}")

                // return min(1, 1 / np.max(terms)), np.minimum(1, 1 / terms)
                println("pvalues=  ${debugSeq.pvalues()}")

                val expected = 1 / (1 + lam * (value - t)).pow(n)
                println("expected=  ${expected}")

                assertEquals(expected, result.pvalueLast, .01)
            }
        }
    }

    @Test
    fun testAgrapa0() {
        val t = 0.5
        val c_g_0 = 0.5
        val c_g_m = 0.99
        val c_g_g = 0.0 // this makes it pretty simple
        val N = 1000
        val u = 1.0
        val n = 10

        // test for sampling with replacement, constant c
        for (value in listOf(0.6, 0.7)) {
            for (lam in listOf(0.2, 0.5)) {
                println("assort value = $value lam=$lam")
                val agrapa = AgrapaBet(
                    N = N,
                    upperBound = u,
                    lam0 = lam,
                    c_grapa_0 = c_g_0,
                    c_grapa_max = c_g_m,
                    c_grapa_grow = c_g_g,
                )
                val betta = BettingMart(bettingFn = agrapa, N = N,
                    sampleUpperBound = u)
                val debugSeq = betta.setDebuggingSequences()
                val x = DoubleArray(n) { value }
                val sampler = SampleFromArray(x)
                val tracker = ClcaErrorTracker(0.0, 1.0)

                val result = betta.testH0(x.size, false, tracker=tracker) { sampler.sample() }
                println("  ${result}")
                println("   bets=  ${debugSeq.bets}")

                debugSeq.bets.forEachIndexed { index, bet ->
                    val expected = if (index == 0) lam else max(0.0, min(c_g_0 / t, 1.0 / (value - t)))
                    assertEquals(expected, bet, .005)
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
        val u = 1.0  // TODO should be 2 * noerror
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
                val betta = BettingMart(bettingFn = agrapa, N = N,
                    sampleUpperBound = u)
                val debugSeq = betta.setDebuggingSequences()
                val x = DoubleArray(n) { value }
                val sampler = SampleFromArray(x)
                val tracker = ClcaErrorTracker(0.0, 1.0)

                val result = betta.testH0(x.size, false, tracker=tracker) { sampler.sample() }
                println("  ${result}")
                println("   bets=  ${debugSeq.bets}")

                debugSeq.bets.forEachIndexed { index, bet ->
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
        val c_g_0 = 0.6
        val c_g_m = 0.9
        val c_g_g = 2.0
        val u = 1.0

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
        val betta = BettingMart(bettingFn = agrapa, N = N,
            sampleUpperBound = u)
        val debugSeq = betta.setDebuggingSequences()

        val sampler = SampleFromList(x)
        val tracker = ClcaErrorTracker(0.0, 1.0)

        val result = betta.testH0(x.size, false, tracker=tracker) { sampler.sample() }
        println("  ${result}")
        println("   bets=  ${debugSeq.bets}") // these are the bets, despite the name

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
            assertEquals(it, debugSeq.bets[idx], doublePrecision)
        }
    }

    @Test
    fun testOptimalComparisonNoP1() {
        val N = 100
        val upper = 1.1

        // from SHANGRLA.test_optimal
        //  with p2=.01 bet=1.0339999999999994
        //  with p2=.001 bet2=1.0933999999999995
        //  with p2=.0001 bet3=1.0993399999999998

        // since OptimalComparisonNoP1 depends only on P2, we dont need to test more
        val optimal = OptimalComparisonNoP1(N = N, withoutReplacement = true, upperBound = upper, p2 = .01)
        val bet = optimal.bet(PluralityErrorTracker(upper/2))
        assertEquals(1.0339999999999994, lamToEta(bet, .5, upper))

        val optimal2 = OptimalComparisonNoP1(N = N, withoutReplacement = true, upperBound = upper, p2 = .001)
        val bet2 = optimal2.bet(PluralityErrorTracker(upper/2))
        assertEquals(1.0933999999999995, lamToEta(bet2, .5, upper))

        val optimal3 = OptimalComparisonNoP1(N = N, withoutReplacement = true, upperBound = upper, p2 = .0001)
        val bet3 = optimal3.bet(PluralityErrorTracker(upper/2))
        assertEquals(1.0993399999999998, lamToEta(bet3, .5, upper))
    }

}