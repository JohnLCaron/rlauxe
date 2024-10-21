package org.cryptobiotic.rlauxe.util

data class Deciles(val ntrials: Int, val hist: MutableMap<Int, Int>) {
    private val incr = 10

    constructor(ntrials: Int): this(ntrials, mutableMapOf())

    // bin[key] goes from [(key-1)*incr, key*incr - 1]
    fun add(q: Int) {
        var bin = 0
        while (q >= bin * incr) bin++
        val currVal = hist.getOrPut(bin) { 0 }
        hist[bin] = (currVal + 1)
    }

    override fun toString() = buildString {
        val shist = hist.toSortedMap()
        append("$ntrials [")
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
        require(ntrials != 0) {"ntrials not set"}
        val smhist = hist.toSortedMap().toMutableMap()
        var cumul = 0
        smhist.forEach {
            cumul += it.value
            val binNo = it.key
            val binDesc = "[${(binNo-1)*incr}-${binNo*incr}]"
            append("$binDesc:${"%5.2f".format(((100.0 * cumul)/ntrials))}; ")
        }
    }

    // bin[key] goes from [(key-1)*incr, key*incr - 1]
    // max must be n * incr
    fun cumul(max: Int) : Double {
        require(ntrials != 0) {"ntrials not set"}
        val smhist = hist.toSortedMap()
        var cumul = 0
        for (entry:Map.Entry<Int,Int> in smhist) {
            if (max < entry.key*incr) {
                return 100.0 * cumul / ntrials
            }
            cumul += entry.value
        }
        return 100.0 * cumul / ntrials
    }

    companion object {
        // 111 [1:9 2:10 3:10 4:10 5:10 6:10 7:10 8:10 9:10 10:10 11:10 12:2 ]
        fun fromString(str: String): Deciles {
            val tokens = str.split(" ", "[", "]", "\"")
            val ftokens = tokens.filter { it.isNotEmpty() }
            val ntrials = ftokens.first().toInt()
            val hist = mutableMapOf<Int, Int>()

            for (tidx in 1 until ftokens.size) {
                val ftoke = ftokens[tidx]
                val htokes = ftoke.split(":")
                val key = htokes[0].toInt()
                val value = htokes[1].toInt()
                hist[key] = value
            }
            return Deciles(ntrials, hist)
        }
    }
}