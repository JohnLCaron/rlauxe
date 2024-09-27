package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.doublePrecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TestDeciles {

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

        val hist2 = Deciles(10)
        hist2.add(70)
        hist2.add(71)
        hist2.add(80)

        println("hist2 = ${hist2}")

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