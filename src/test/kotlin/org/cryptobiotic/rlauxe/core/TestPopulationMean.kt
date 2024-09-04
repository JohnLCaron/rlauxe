package org.cryptobiotic.rlauxe.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TestPopulationMeanWithoutReplacement {

    @Test
    fun testMean() {
        val x = listOf(0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 0.0, 1.0)
        println(x)

        var sum = 0.0
        val sumMinus1: List<Double> = x.mapIndexed { idx, it ->
            val summ1 = if (idx == 0) 0.0 else sum
            sum += it
            summ1
        }
        println(sumMinus1)
        assertEquals(listOf(0.0, 0.0, 1.0, 1.0, 2.0, 2.0, 2.0, 3.0, 4.0, 4.0), sumMinus1)

        val N = x.size
        val m = sumMinus1.mapIndexed { idx, s ->
            val sampleNum = idx + 1
            (N * .5 - s) / (N - sampleNum + 1)
        }
        println(m)
        assertEquals(listOf(0.5, 0.5555555555555556, 0.5, 0.5714285714285714, 0.5, 0.6, 0.75, 0.6666666666666666, 0.5, 1.0), m)
    }

    @Test
    fun testMeanNeg() {
        val x = listOf(0.0, 1.0, 0.0, 1.0, 1.0, 1.0, 0.0, 1.0, 1.0, 1.0, 1.0, 0.0)
        println(x)

        var sum = 0.0
        val sumMinus1: List<Double> = x.mapIndexed { idx, it ->
            val summ1 = if (idx == 0) 0.0 else sum
            sum += it
            summ1
        }
        println(sumMinus1)
        assertEquals(listOf(0.0, 0.0, 1.0, 1.0, 2.0, 3.0, 4.0, 4.0, 5.0, 6.0, 7.0, 8.0), sumMinus1)

        val N = x.size
        val m = sumMinus1.mapIndexed { idx, s ->
            val sampleNum = idx + 1
            (N * .5 - s) / (N - sampleNum + 1)
        }
        println(m)
        assertEquals(listOf(0.5, 0.5454545454545454, 0.5, 0.5555555555555556, 0.5, 0.42857142857142855, 0.3333333333333333, 0.4, 0.25, 0.0, -0.5, -2.0), m)
        // if mean goes < 0, not enough samples left to make average < .5, so RejectNull
    }

    @Test
    fun testMeanGtOne() {
        val x = listOf(0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0)
        println(x)

        var sum = 0.0
        val sumMinus1: List<Double> = x.mapIndexed { idx, it ->
            val summ1 = if (idx == 0) 0.0 else sum
            sum += it
            summ1
        }
        println(sumMinus1)
        assertEquals(listOf(0.0, 0.0, 1.0, 1.0, 2.0, 2.0, 2.0, 2.0, 3.0, 3.0, 3.0, 4.0), sumMinus1)

        val N = x.size
        val m = sumMinus1.mapIndexed { idx, s ->
            val sampleNum = idx + 1
            (N * .5 - s) / (N - sampleNum + 1)
        }
        println(m)
        assertEquals(listOf(0.5, 0.5454545454545454, 0.5, 0.5555555555555556, 0.5, 0.5714285714285714, 0.6666666666666666, 0.8, 0.75, 1.0, 1.5, 2.0), m)
        // if mean goes > 1, not enough samples left to make average > .5, so AcceptNull
    }
}