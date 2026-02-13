package org.cryptobiotic.rlauxe.util

import kotlin.math.min
import kotlin.math.round

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