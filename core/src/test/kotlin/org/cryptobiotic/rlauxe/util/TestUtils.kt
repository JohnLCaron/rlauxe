package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.betting.TestH0Status
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

