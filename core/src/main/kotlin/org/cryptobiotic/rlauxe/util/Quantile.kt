package org.cryptobiotic.rlauxe.util

import kotlin.math.min
import kotlin.math.round

// find the sample value where percent of samples < that value equals quantile percent
fun quantile(data: List<Int>, quantile: Double): Int {
    require(quantile in 0.0..1.0)
    if (data.isEmpty()) return 0
    if (quantile == 0.0) return 0

    val sortedData = data.sorted()
    if (quantile == 100.0) return sortedData.last()
    // println(showDeciles(sortedData))

    // rounding down
    val p = min((quantile * data.size).toInt(), data.size-1)
    return sortedData[p]
}

// 10*(idx+1) percent of distribution is less than decile[idx]
fun makeDeciles(data: List<Int>): List<Int> {
    val sortedData = data.sorted()
    val deciles = mutableListOf<Int>()
    val n = sortedData.size
    repeat(9) {
        val quantile = .10 * (it + 1)
        val p = min((quantile * n).toInt(), n - 1)
        deciles.add(sortedData[p])
    }
    deciles.add(sortedData.last())
    return deciles
}

fun showDeciles(data: List<Int>) = buildString {
    if (data.isEmpty()) return ""
    val deciles = makeDeciles(data)
    append(" deciles=[")
    deciles.forEach { append(" $it, ") };
    appendLine("]")
}

/**
 *
 * @param deciles The distribution as deciles (10 values)
 * @param sample The sample estimate
 * @return probability of that sample as percent
 */
fun probability(deciles: List<Int>, sample: Int): Int {
    require(deciles.size == 10)
    if (sample >= deciles.last()) return 100
    if (sample <= deciles[0]) {
        val topSample = deciles[0]
        val frac = (topSample - sample - 1).toDouble() / topSample
        return round(10.0 * (1 - frac)).toInt()
    }

    var top = 0
    while (top < 10) {
        if (sample < deciles[top]) break
        top++
    }
    if (top == 0) return 10

    // interpolate
    val botSample = deciles[top-1]
    val topSample = deciles[top]
    val frac = (topSample - sample - 1).toDouble() / (topSample - botSample)

    return round(10.0 * (top + 1 - frac)).toInt()
}