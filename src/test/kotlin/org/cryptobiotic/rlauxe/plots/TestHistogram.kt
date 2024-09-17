package org.cryptobiotic.rlauxe.plots

import org.cryptobiotic.rlauxe.doublePrecision
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
        val ntrials = 100
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
    }

    @Test
    fun testCumulNot100() {
        val hist = Histogram(10)
        val ntrials = 111
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
    }

    @Test
    fun testHistogramSparse() {
        val hist = Histogram(10)
        val ntrials = 87
        repeat(ntrials) { hist.add(77) }
        hist.ntrials = 100

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
    }
}