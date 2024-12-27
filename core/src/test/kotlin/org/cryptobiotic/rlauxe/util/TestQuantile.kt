package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.workflow.quantile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TestQuantile {

    @Test
    fun testQuantile() {
        val data = listOf(847, 868, 565, 319, 882, 782, 319, 570, 392, 319)
        val sdata = data.sorted()
        println("sorted = $sdata")
        repeat(10) {
            val q = .10 * (it+1)
            println(" ${(df(100*q))}% quantile = ${quantile(data, q)}")
        }
    }

}