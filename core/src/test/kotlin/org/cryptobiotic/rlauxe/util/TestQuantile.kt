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
            samples.add(Random.nextInt(1000))
        }
        val datas = samples.toList()
        for (idx in 1..10) {
            val q = .10 * idx
            val v = quantile(datas, q)
            val count = datas.count { data -> data < v }
            println(" ${(df(100*q))}% quantile = $v count=$count expect=${(n * q).toInt()}")
            if (q == 1.0)
                assertEquals(count+1, (n * q).toInt())
            else
                assertEquals(count, (n * q).toInt())
        }
    }

}