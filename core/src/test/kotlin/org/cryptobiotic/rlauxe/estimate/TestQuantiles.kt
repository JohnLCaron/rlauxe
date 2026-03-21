package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.util.Quantiles.percentiles
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.estimateOld.probability
import org.cryptobiotic.rlauxe.estimateOld.quantile
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

class TestQuantiles {

    @Test
    fun testQuantile() {
        val data = listOf(847, 868, 565, 319, 882, 782, 312, 570, 392, 111)
        val sdata = data.sorted()
        println("sorted = $sdata")
        repeat(10) {
            val pct = .10 * (it + 1)
            val q = quantile(data, pct)
            println(" ${(df(100 * pct))}% quantile = $q")
            if (it < 9) assertEquals(sdata[it+1], q)
        }
    }

    @Test
    fun testFindQuantile() {
        val data = listOf(137, 194, 198, 229, 241, 252, 266, 386, 668, 896, 919, 1135, 2131, 2631, 3364, 3651, 3651, 4127, 7473, 9629).sorted()
        val sdata = data.sorted()
        println("sorted = $sdata")
        repeat(10) {
            val pct = .05 +  .1 * (it)
            val q = findQuantile(sdata, pct)
            println(" ${(df(100 * pct))}% quantile = $q")
            // if (it < 9) assertEquals(sdata[it+1], q)
        }
    }

    @Test
    fun testQuantiles() {
        val data = listOf(137, 194, 198, 229, 241, 252, 266, 386, 668, 896, 919, 1135, 2131, 2631, 3364, 3651, 3651, 4127, 7473, 9629)
        println("sorted = $data")
        repeat(101) { pct ->
            val wtf: Double = percentiles().index(pct).compute(*data.toIntArray())
            println("percent=$pct = $wtf")
        }
    }

    @Test
    fun testProbabilityFromRealAudit() {
        var deciles = listOf(57, 58, 58, 58, 60, 60, 61, 62, 62, 65)
        val p = probability(deciles, 53)
        assertEquals(9, p)

        deciles = listOf(55, 55, 55, 57, 58, 58, 60, 63, 65, 66)
        assertEquals(30, probability(deciles, 55))
        assertEquals(35, probability(deciles, 56))
        assertEquals(40, probability(deciles, 57))
        assertEquals(90, probability(deciles, 65))
    }

}

// outdated
fun findQuantile(sortedData: List<Int>, quantile: Double): Int {
    require(sortedData.isNotEmpty())

    val deciles = mutableListOf<Int>()
    val n = sortedData.size
    repeat(9) {
        val quantile = .10 * (it + 1)
        val p = min((quantile * n).toInt(), n - 1)
        deciles.add(sortedData[p])
    }
    deciles.add(sortedData.last() + 1)

    require(quantile in 0.0..1.0)

    // edge cases
    if (quantile == 0.0) return sortedData.first()
    if (quantile == 100.0) return sortedData.last()

    // rounding down. TODO interpolate; see Deciles ??
    val p = min((quantile * sortedData.size).toInt(), sortedData.size-1)
    return sortedData[p]
}