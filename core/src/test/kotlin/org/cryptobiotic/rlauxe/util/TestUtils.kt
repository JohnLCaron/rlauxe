package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.corla.estimateCorla
import org.cryptobiotic.rlauxe.corla.optimistic
import org.cryptobiotic.rlauxe.corla.pValueApproximation
import org.junit.Assert.assertTrue
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TestUtils {

    @Test
    fun calcStuff() {
        val diff = 27538
        val Npop = 396121.0
        val Nc = 44675.0
        val bet = 2/1.03905
        val alpha = .03
        val margin = diff / Nc
        val dmargin = diff / Npop
        println("margin=$margin samples=${estSamplesFromMarginUpper(bet, margin, alpha)}")
        println("dmargin=$dmargin margin=${estSamplesFromMarginUpper(bet, dmargin, alpha)}")
    }

    @Test
    fun calcSS() {
        val gamma = 1.1
        val diff = 27538
        val Npop = 396121.0
        val Nc = 44675.0
        val bet = 2/1.03905
        val alpha = .03
        val margin = diff / Nc
        val dmargin = diff / Npop

        println("\nsuper simple estimation of samples needed")
        val rho = rho(alpha, gamma, p1o = 0.0)
        val ssEst = rho/dmargin
        println("   dmargin=$dmargin rho=$rho samples needed=$ssEst")

        val n = roundUp(ssEst)
        println("\nwhat is the risk when dmargin=$dmargin n=$n")

        val U = 2 * gamma / dmargin // "total error bound"
        val Uterm = (1.0 - 1.0/U)
        val risk = Math.pow(Uterm, n.toDouble())
        println("   U=$U Uterm=$Uterm risk=$risk")

        //val ss = pValueApproximation(n, dmargin, gamma, 0, 0)
        //println("dmargin=$dmargin U=$U ss=$ss")

        val noerror = 1.0 / (2.0 - dmargin)
        val payoff = 1.0 + bet * (noerror - 0.5)
        val payoffn = payoff.pow(n.toDouble())
        val risk2 =  1.0 / payoffn
        println("   noerror=$noerror payoff=$payoff risk2=$risk2")
        println("   Note Uterm=$Uterm 1/payoff=${(1/payoff)} almost equal diff= ${Uterm - 1/payoff}")

        println("\nhow many samples do you need when drawing from a diluted population?")

    }

    fun rho(alpha: Double, gamma: Double, p1o: Double): Double {
        val g12 = 1/(2*gamma)
        val rho = -ln(alpha) / (g12 + p1o * ln(1 - g12))
        return rho
    }

    @Test
    fun testSS() {
        // step 4 page 4
        val rho = rho(alpha = .1, gamma = 1.1, p1o = 0.5)
        println("rho = $rho")
        assertTrue(doubleIsClose(rho, 15.2, .01))
        val ss = rho / .02
        println("ss = $ss")
        assertTrue(doubleIsClose(ss, 760.0, .01))

        // page 9
        val rho2 = rho(alpha = .1, gamma = 1.1, p1o = 0.1)
        println("rho2 = $rho2")
        assertTrue(doubleIsClose(rho2, 5.85, .01))
        val ss2 = rho2 / .02
        println("ss2 = $ss2")
        assertTrue(doubleIsClose(ss2, 293.0, .01))
    }

    @Test
    fun testMoreStyle() {
        // p 6
        // Here’s an example. Suppose N = 10,000, p = 0.1, and mB = 0.1 = mS . For contest
        // B, there are 5,500 reported votes for the winner and 4,500 reported votes for the loser;
        // where do these numbers come from ??
        // for contest S there are 550 votes for the winner and 450 votes for the loser. Contest B has
        // an initial sample size of 64 cards, and contest S has an initial sample size of 721 cards.

        val gamma = 1.1  // paper doesnt specify ??
        val alpha = .05
        val p1o = .001

        val Npop = 10000
        val p = .1
        val Nb = Npop
        val Ns = p*Npop
        val margin = .1
        val marginB = margin * Nb / Npop.toDouble() // .1
        val marginS = margin * Ns / Npop.toDouble() // .01

        val sbRho = roundUp(rho(alpha, gamma, p1o)/marginB)
        val ssRho = roundUp(rho(alpha, gamma, p1o)/marginS)
        println("       ssRho = $ssRho sbRho = $sbRho when gamma=$gamma")
        //        ssRho = 660 sbRho = 66 when gamma=1.1
        // paper  ssRho = 721 sbRho = 64 WHY DIFFERENT?

        // p 8.
        // "without CSD we would have had to examine rho/p * mρ S ballots
        val ssRhop = roundUp(rho(alpha, gamma, p1o)/(p*marginB))
        println("       ssRhop = $ssRhop")

/*
        val ssb = estimateCorla(alpha, marginB, gamma, oneOver = roundToClosest(p1o*Nb))
        val ssb2 = optimistic(alpha, marginB, gamma, oneOver = roundToClosest(p1o*Nb))
        val sss = estimateCorla(alpha, marginS, gamma, oneOver = roundToClosest(p1o*Ns))
        println(" marginB = $marginB ssb = $ssb")
        println(" marginS = $marginS ssS = $sss") */
    }

    ///////////////////////////////////////
    @Test
    fun calcMarginUpperFromSamples() {
        val samples = 101
        val bet = 2/1.03905
        val margin = estMarginUpperFromSamples(bet, samples, .03)
        println("samples=$samples margin=$margin")
    }

    @Test
    fun calcSamplesFromMarginUpper() {
        val bet = 2/1.03905
        val alpha = .03
        val marginUpper = .02
        val samples = estSamplesFromMarginUpper(bet, marginUpper, alpha)
        println("margin=$marginUpper samples=$samples ")
        println("margin=${marginUpper/2} samples=${estSamplesFromMarginUpper(bet, marginUpper/2, alpha)} ")

        val noerror = 1.0 / (2.0 - marginUpper)
        val nomargin = 2.0 * noerror - 1.0
        val n =  -ln(alpha) / ln(1.0 + bet * nomargin / 2)
        println("-ln(alpha) = ${-ln(alpha)}")
        println("1.0 + bet * nomargin / 2 = ${1.0 + bet * nomargin / 2}")
        println("ln(1.0 + bet * nomargin / 2) = ${ln(1.0 + bet * nomargin / 2)}")
        println("x = bet * nomargin / 2 = ${bet * nomargin / 2}") // x < 1

        val ans = ln(1.0 + bet * nomargin / 2)
        val x = bet * nomargin / 2
        var sum = 0.0
        println("taylor series of ln(1+x)")
        repeat(5) {
            val term = taylor(x, it+1)
            sum += term
            println("${it+1} $term $sum ${ans-sum}")
        }

        // noerror = 1 / (2 - marginUpper)
        // noerror - 1/2 = 1 / (2 - marginUpper) - 1/2
        // noerror - 1/2 = 2 / 2(2 - marginUpper) - (2 - marginUpper)/2(2 - marginUpper)
        // noerror - 1/2 = (2 - (2 - marginUpper)) / 2 (2 - marginUpper )
        // noerror - 1/2 = marginUpper / 2*(2 - marginUpper)

        // val n =  -ln(alpha) / ln(1.0 + bet * (noerror - 1/2))
        // val n =  -ln(alpha) / ln(1.0 + bet * (marginUpper / 2*(2 - marginUpper ))


        // so ln(1.0 + x) ~ x
        // so n = -ln(alpha) / ln(1.0 + bet * nomargin / 2)
        // so n = -ln(alpha) / (bet * nomargin / 2)
        // so n ~ -ln(alpha) / (bet * (marginUpper / 2*(2 - marginUpper )))
        // so n ~ [-ln(alpha) / bet/2]  / (marginUpper/ (2 - marginUpper ))
        val num = -ln(alpha) / (bet/2)
        val den = marginUpper / (2 - marginUpper )
        val napprox = num / den
        println("num=$num den = $den")
        println("${napprox} ${samples} ${samples-napprox}")

        val den2 = marginUpper/2 / (2 - marginUpper/2 )
        val napprox2 = num / den2
        println("num=$num den2 = $den2")
        println("${napprox2} ${2*samples} ${2*samples-napprox2}")

        // so if margin = voteDiff/Npop, and you double Npop, you half the margin and you ~ double the sample size
    }

    // x < 1
    fun taylor(x: Double, k: Int): Double {
        val sign = if (k % 2 == 0) -1 else 1
        return sign * x.pow(k) / k
    }

    @Test
    fun testDoubleIsClose() {
        val ratio=0.9041144901610018
        val close = doubleIsClose2(1.0, ratio, 0.10)
        println("close = $close")
    }

    @Test
    fun testFormat() {
        assertEquals("0.9041", df(0.9041144901610018))
        assertEquals("0.904114", dfn(0.9041144901610018, 6))

        assertEquals("  9041", nfn(9041, 6))
        assertEquals("90411449", nfn(90411449, 6))
        assertEquals("1234567890", nfn(1234567890, 9))

        assertEquals("          1234567890", sfn("1234567890", 20))
        assertEquals("1234567890", sfn("1234567890", 5))
        assertEquals("1234567890", sfn("1234567890", -5))
        assertEquals("1234567890     ", sfn("1234567890", -15))
    }

    @Test
    fun testEnums() {
        assertEquals(TestH0Status.LimitReached, enumValueOf("LimitReached"))
        assertEquals(TestH0Status.LimitReached, enumValueOf("LimitReacHED", TestH0Status.entries))

        assertNull(enumValueOf("bad", TestH0Status.entries))
        assertFailsWith<IllegalArgumentException> {
            assertEquals(TestH0Status.InProgress, enumValueOf("bad"))
        }
    }
}

fun doubleIsClose2(a: Double, b: Double, rtol: Double=1.0e-5, atol:Double=1.0e-8): Boolean {
    val t1 =  abs(a - b)
    val t2 = rtol * abs(b)
    val t3 = atol + t2
    //     return abs(a - b) <= atol + rtol * abs(b)
    return (t1 <= t3)
}

