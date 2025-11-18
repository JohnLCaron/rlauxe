package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.util.doublePrecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TestDecilesOld {

    @Test
    fun testBasics() {
        val hist = Deciles(10)

        // bin[key] goes from [(key-1)*incr, key*incr - 1]
        hist.add(0)
        hist.add(1)
        hist.add(9)
        hist.add(10)
        println("hist = ${hist}")
        assertEquals(3,  hist.hist[1])
        assertEquals(1,  hist.hist[2])
        assertNull(hist.hist[9])
        assertEquals("10 [1:3 2:1 ]",  hist.toString())

        val hist2 = Deciles(10)
        hist2.add(70)
        hist2.add(71)
        hist2.add(80)

        println("hist2 = ${hist2}")
        assertEquals("10 [8:2 9:1 ]",  hist2.toString())

        assertNull(hist2.hist[7])
        assertEquals(2,  hist2.hist[8])
        assertEquals(1,  hist2.hist[9])
        assertNull(hist2.hist[10])

        assertEquals(hist, Deciles.fromString(hist.toString()))
    }

    @Test
    fun testCumul() {
        val ntrials = 100
        val hist = Deciles(ntrials)
        repeat(ntrials) { hist.add(it+1) }

        println("hist = ${hist}")
        println("hist binned = ${hist.toStringBinned()}")
        println("hist cumulPct = ${hist.cumulPct()}")
        println("cumul 10 = ${hist.cumul(10)}")
        println("cumul 20 = ${hist.cumul(20)}")
        println("cumul 30= ${hist.cumul(30)}")

        assertEquals(9.0, hist.cumul(10))
        assertEquals(19.0, hist.cumul(20))
        assertEquals(29.0, hist.cumul(30))
        assertEquals(99.0, hist.cumul(100))
        assertEquals(100.0, hist.cumul(110))

        assertEquals(hist, Deciles.fromString(hist.toString()))
    }

    @Test
    fun testCumulNot100() {
        val ntrials = 111
        val hist = Deciles(ntrials)
        repeat(ntrials) { hist.add(it+1) }

        println("hist = ${hist}")
        println("hist binned = ${hist.toStringBinned()}")
        println("hist cumulPct = ${hist.cumulPct()}")
        println("cumul 10 = ${hist.cumul(10)}")
        println("cumul 20 = ${hist.cumul(20)}")
        println("cumul 30= ${hist.cumul(30)}")

        assertEquals(9.0/1.11, hist.cumul(10), doublePrecision)
        assertEquals(19.0/1.11, hist.cumul(20), doublePrecision)
        assertEquals(29.0/1.11, hist.cumul(30), doublePrecision)
        assertEquals(99.0/1.11, hist.cumul(100), doublePrecision)
        assertEquals(100.0, hist.cumul(120), doublePrecision)

        assertEquals(hist, Deciles.fromString(hist.toString()))
    }

    @Test
    fun testSparse() {
        val ntrials = 87
        val hist = Deciles(100)
        repeat(ntrials) { hist.add(77) }

        println("hist = ${hist}")
        println("hist binned = ${hist.toStringBinned()}")
        println("hist cumulPct = ${hist.cumulPct()}")

        repeat(8) {
            // println("hist ${it*10}= ${hist.cumul(it*10, ntrials)}")
            assertEquals(0.0, hist.cumul(it*10))
        }
        assertEquals(0.0, hist.cumul(70))
        assertEquals(87.0, hist.cumul(80))
        assertEquals(87.0, hist.cumul(90))
        assertEquals(87.0, hist.cumul(1200))

        assertEquals(hist, Deciles.fromString(hist.toString()))
    }

    @Test
    fun testCsv() {
        val ntrials = 111
        val hist = Deciles(ntrials)
        repeat(ntrials) { hist.add(it+1) }

        println("hist = ${hist}")
        val roundtrip = Deciles.fromString(hist.toString())
        assertEquals(hist, roundtrip)
    }

    @Test
    fun testCsvWithQuotes() {
        val ntrials = 87
        val hist = Deciles(ntrials)
        repeat(ntrials) { hist.add(77) }

        println("hist = ${hist}")
        val roundtrip = Deciles.fromString(" \"${hist}\" ") // quotes and leasing/trailing blanks
        assertEquals(hist, roundtrip)
    }
}

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