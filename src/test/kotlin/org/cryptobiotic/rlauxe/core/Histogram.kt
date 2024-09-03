package org.cryptobiotic.rlauxe.core

import kotlin.math.max

data class Histogram(val incr: Int) {
    val hist = mutableMapOf<Int, Int>() // upper bound,count

    // bin[key] goes from [(key-1)*incr, key*incr - 1]
    fun add(q: Int) {
        var bin = 0
        while (q >= bin * incr) bin++
        val currVal = hist.getOrPut(bin) { 0 }
        hist[bin] = (currVal + 1)
    }

    override fun toString() = buildString {
        val shist = hist.toSortedMap()
        shist.forEach { append("${it.key}:${it.value} ") }
    }

    fun toString(keys:List<String>) = buildString {
        hist.forEach { append("${keys[it.key]}:${it.value} ") }
    }

    fun toStringBinned() = buildString {
        val shist = hist.toSortedMap()
        shist.forEach {
            val binNo = it.key
            val binDesc = "[${(binNo-1)*incr}-${binNo*incr}]"
            append("$binDesc:${it.value}; ")
        }
    }

    fun cumul() = buildString {
        val smhist = hist.toSortedMap().toMutableMap()
        var cumul = 0
        smhist.forEach {
            cumul += it.value
            append("${it.key}:${cumul} ")
        }
    }

    // bin[key] goes from [(key-1)*incr, key*incr - 1]
    // max must be n * incr
    fun cumul(max: Int) : Int {
        val smhist = hist.toSortedMap()
        var cumul = 0
        for (entry:Map.Entry<Int,Int> in smhist) {
            if (max < entry.key*incr) {
                return cumul
            }
            cumul += entry.value
        }
        return cumul
    }
}