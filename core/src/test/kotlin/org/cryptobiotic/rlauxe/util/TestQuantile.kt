package org.cryptobiotic.rlauxe.util

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TestQuantile {

    @Test
    fun testQuantile() {
        val data = listOf(847, 868, 565, 319, 882, 782, 312, 570, 392, 111)
        val sdata = data.sorted()
        println("sorted = $sdata")
        repeat(10) {
            val pct = .10 * (it + 1)
            val q = quantile(data, pct)
            println(" ${(df(100*pct))}% quantile = $q")
            if (it < 9) assertEquals(sdata[it+1], q)
        }
    }

    @Test
    fun testQuantile2() {
        val n = 417
        val samples = mutableSetOf<Int>() // no duplicates
        while (samples.size < n) {
            samples.add(Random.nextInt(1000)) // pick a number between 0 and 999
        }
        val datas = samples.toList()
        for (idx in 1..10) {
            val q = .10 * idx
            val v = quantile(datas, q)
            val count = datas.count { data -> data < v }
            println(" ${(df(100*q))}% quantile has $v count=$count expect=${(n * q).toInt()}")
            if (q == 1.0)
                assertEquals(count+1, (n * q).toInt())
            else
                assertEquals(count, (n * q).toInt())
        }
    }

    @Test
    fun testMake100Deciles() {
        val data = List(100) { it+1 }

        val deciles = makeDeciles(data)
        println(" deciles=$deciles size = ${deciles.size}")

        // 10*(idx+1) percent of distribution is less than decile[idx]
        repeat(10) {
            val top = deciles[it]
            var count = data.count { data -> data < top }
            println(" ${10*(it+1)} percent of distribution is less than $top; count = $count")
            assertEquals(10*(it+1), count)
        }
    }

    @Test
    fun testMakeDeciles() {
        val data = listOf(847, 565, 319, 882, 782, 312, 570, 392, 111)
        val sdata = data.sorted()
        println("sorted = $sdata")

        val deciles = makeDeciles(data)
        println(" deciles=$deciles")

        repeat(9) {
            assertEquals(sdata[it], deciles[it])
        }
    }

    @Test
    fun testProbability100() {
        val data = List(100) { it }
        val deciles = makeDeciles(data)
        println(" deciles=$deciles")
        val p = probability(deciles, 42)
        println(" probability of 42 is = $p")

        repeat ( 100) {
            val rn = Random.nextInt(100)
            assertEquals(rn, probability(deciles, rn))
        }

    }

    @Test
    fun testProbability() {
        val data = listOf(868, 565, 319, 882, 782, 312, 320, 570, 392, 111)
        val deciles = makeDeciles(data)
        println(" deciles=$deciles")

        for (s in listOf(50, 190, 315, 600, 777, 860, 875, 882, 883)) {
            println(" probability of $s is = ${probability(deciles, s)}")
        }
    }

    @Test
    fun testProbabilityFromRealAudit() {
        var deciles = listOf(57, 58, 58, 58, 60, 60, 61, 62, 62, 62)
        var p = probability(deciles, 53)
        assertEquals(9, p)

        deciles = listOf(55, 55, 55, 57, 58, 58, 60, 63, 65, 65)
        assertEquals(30, probability(deciles, 55))
        assertEquals(35, probability(deciles, 56))
        assertEquals(40, probability(deciles, 57))
    }

}