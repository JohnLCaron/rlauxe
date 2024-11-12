package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.doubleIsClose
import org.junit.jupiter.api.Assertions.assertNotEquals
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

class TestUtils {

    @Test
    fun testDoubleIsClose() {
        val ratio=0.9041144901610018
        val close = doubleIsClose2(1.0, ratio, 0.10)
        println("close = $close")
    }
}

fun doubleIsClose2(a: Double, b: Double, rtol: Double=1.0e-5, atol:Double=1.0e-8): Boolean {
    val t1 =  abs(a - b)
    val t2 = rtol * abs(b)
    val t3 = atol + t2
    //     return abs(a - b) <= atol + rtol * abs(b)
    return (t1 <= t3)
}

