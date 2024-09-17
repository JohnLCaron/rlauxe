package org.cryptobiotic.rlauxe.plots

data class Histogram(val incr: Int) {
    val hist = mutableMapOf<Int, Int>() // upper bound,count
    var ntrials = 0

    // bin[key] goes from [(key-1)*incr, key*incr - 1]
    fun add(q: Int) {
        var bin = 0
        while (q >= bin * incr) bin++
        val currVal = hist.getOrPut(bin) { 0 }
        hist[bin] = (currVal + 1)
    }

    override fun toString() = buildString {
        val shist = hist.toSortedMap()
        append("[")
        shist.forEach { append("${it.key}:${it.value} ") }
        append("]")
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

    fun cumulPct() = buildString {
        val untrials = if (ntrials == 0) 1 else ntrials
        val smhist = hist.toSortedMap().toMutableMap()
        var cumul = 0
        smhist.forEach {
            cumul += it.value
            val binNo = it.key
            val binDesc = "[${(binNo-1)*incr}-${binNo*incr}]"
            append("$binDesc:${"%5.2f".format(((100.0 * cumul)/untrials))}; ")
        }
    }

    // bin[key] goes from [(key-1)*incr, key*incr - 1]
    // max must be n * incr
    fun cumul(max: Int) : Double {
        val untrials = if (ntrials == 0) 1 else ntrials
        val smhist = hist.toSortedMap()
        var cumul = 0
        for (entry:Map.Entry<Int,Int> in smhist) {
            if (max < entry.key*incr) {
                return 100.0 * cumul / untrials
            }
            cumul += entry.value
        }
        return 100.0 * cumul / untrials
    }
}