package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.integration.Histogram
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TestHistogram {

    @Test
    fun testBasics() {
        val hist = Histogram(10)

        // bin[key] goes from [(key-1)*incr, key*incr - 1]
        hist.add(0)
        hist.add(1)
        hist.add(9)
        hist.add(10)
        println("hist = ${hist}")
        assertEquals(3,  hist.hist[1])
        assertEquals(1,  hist.hist[2])
        assertNull(hist.hist[9])

        val hist2 = Histogram(10)
        hist2.add(70)
        hist2.add(71)
        hist2.add(80)

        println("hist2 = ${hist2}")

        assertNull(hist2.hist[7])
        assertEquals(2,  hist2.hist[8])
        assertEquals(1,  hist2.hist[9])
        assertNull(hist2.hist[10])
    }

    @Test
    fun testCumul() {
        val hist = Histogram(10)

        repeat(100) { hist.add(it+1) }

        println("hist = ${hist}")
        println("hist binned = ${hist.toStringBinned()}")
        println("hist cumulPct = ${hist.cumulPct(100)}")
        println("cumul 10 = ${hist.cumul(10)}")
        println("cumul 20 = ${hist.cumul(20)}")
        println("cumul 30= ${hist.cumul(30)}")

        assertEquals(9, hist.cumul(10))
        assertEquals(19, hist.cumul(20))
        assertEquals(29, hist.cumul(30))
        assertEquals(100, hist.cumul(110))
    }

    @Test
    fun testHistogramSparse() {
        val hist = Histogram(10)

        repeat(87) { hist.add(77) }

        println("hist = ${hist}")
        println("hist binned = ${hist.toStringBinned()}")
        println("hist cumulPct = ${hist.cumulPct(1)}")

        repeat(8) {
            println("hist ${it*10}= ${hist.cumul(it*10)}")
            assertEquals(hist.cumul(it*10), 0)
        }
        assertEquals(hist.cumul(70), 0)
        assertEquals(hist.cumul(80), 87)
        assertEquals(hist.cumul(90), 87)
        assertEquals(hist.cumul(1200), 87)
    }
}