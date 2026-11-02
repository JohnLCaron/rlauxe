package org.cryptobiotic.rlauxe.util


import kotlin.math.min
import kotlin.math.round
import kotlin.test.assertEquals

//// TODO perhaps combine or replace with Quantiles ??

// TODO if < 10, just show list with trailing 0s
// 10*(idx+1) percent of distribution is less than decile[idx]
fun makeDeciles(data: List<Int>): List<Int> {
    if (data.isEmpty()) return emptyList()
    val sortedData = data.sorted()
    val deciles = mutableListOf<Int>()
    val n = sortedData.size
    repeat(9) {
        val quantile = .10 * (it + 1)
        val p = min((quantile * n).toInt(), n - 1)
        deciles.add(sortedData[p])
    }
    deciles.add(sortedData.last()+1)
    return deciles
}

fun showDeciles(data: List<Int>) = buildString {
    if (data.isEmpty()) append("") else {
        val deciles = makeDeciles(data)
        append(" deciles=[")
        deciles.forEach { append(" $it, ") }
        append("]")
    }
}

/**
 * @param deciles The distribution as deciles (10 values)
 * @param sample The sample estimate
 * @return probability of that sample as percent
 */
fun probability(deciles: List<Int>, sample: Int): Int {
    if (deciles.size < 10) return 0
    if (sample >= deciles.last()) return 100

    if (sample < deciles[0]) {
        val frac = frac(0, sample, deciles[0])
        return round(10 * frac).toInt()
    }

    var topIdx = 0
    while (topIdx < 10) {
        if (sample < deciles[topIdx]) break
        topIdx++
    }
    // if (top == 0) return 10 // shouldnt this be caught above ??
    // if (sample == deciles[top-1]) return top * 10

    // interpolate
    val frac = frac(deciles[topIdx-1], sample, deciles[topIdx])
    return round(10.0 * (topIdx + frac)).toInt()
}

fun frac(bot: Int, sample: Int, top: Int): Double {
    return (sample - bot) / (top - bot).toDouble()
}

//////////////////////////////////////////////////////////////

// find the sample value where percent of samples < that value equals quantile percent
fun quantile(data: List<Int>, quantile: Double): Int {
    require(quantile in 0.0..1.0)
    if (data.isEmpty()) return 0
    if (quantile == 0.0) return 0

    val sortedData = data.sorted()
    if (quantile == 100.0) return sortedData.last()

    // rounding down
    val p = min((quantile * data.size).toInt(), data.size-1)
    return sortedData[p]
}

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