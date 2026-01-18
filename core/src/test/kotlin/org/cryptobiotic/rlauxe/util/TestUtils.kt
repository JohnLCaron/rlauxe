package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.betting.TestH0Status
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TestUtils {

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

