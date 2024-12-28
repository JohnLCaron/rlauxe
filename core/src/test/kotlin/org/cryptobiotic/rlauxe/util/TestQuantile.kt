package org.cryptobiotic.rlauxe.util

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TestQuantile {

    @Test
    fun testQuantile() {
        val data = listOf(847, 868, 565, 319, 882, 782, 319, 570, 392, 319)
        val sdata = data.sorted()
        println("sorted = $sdata")
        repeat(10) {
            val q = .10 * it
            println(" ${(df(100*q))}% quantile = ${quantile(data, q)}")
            assertEquals(sdata[it], quantile(data, q))
        }
    }

    @Test
    fun testQuantile2() {
        val n = 666
        val datas = List<Int>(n) { Random.nextInt(1000) }
        val count = datas.count { data -> data <= 1000 }
        println(" $count")
        // val sdata = data.sorted()
        // println("sorted = $sdata")
        repeat(11) {
            val q = .10 * it
            val v = quantile(datas, q)
            val count = datas.count { data -> data <= v }
            println(" ${(df(100*q))}% quantile = $v count=$count pct=${100.0*count/n}")
            // assertEquals(sdata[it], quantile(data, q))
        }
    }

}